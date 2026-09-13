package com.chatwaifu.vits

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Description: SoundPlayHandler
 *
 * **采样率是运行时才知道的**：BV2 的日文底模是 44100，老 VITS 的内置模型是 22050，
 * 用户导入的模型又可能是别的值。所以 [AudioTrack] 不能在 init 里建死 ——
 * 改造前就是这样，`setTrackData()` 只改字段、从不重建 track，
 * 结果 44100 的音频会被 22050 的 track 以半速放出来。现在按参数变化惰性重建。
 *
 * Author: Voine
 * Date: 2023/2/23
 */
class SoundPlayHandler {
    private val handler: Handler
    private val handlerThread = HandlerThread("SoundPlayHandler")
    private val trackLock = Any()
    private val nextPlaybackId = AtomicLong(1L)
    private val cancelledPlaybackIds = ConcurrentHashMap.newKeySet<Long>()
    private val pendingCompletions =
        ConcurrentHashMap<Long, CancellableContinuation<Boolean>>()
    private var audioTrack: AudioTrack? = null
    private var sampleRate = DEFAULT_SAMPLE_RATE
    private var channels = AudioFormat.CHANNEL_OUT_MONO
    private var channelCount = 1
    private var activePlayback: PlaybackState? = null
    @Volatile
    private var activePlaybackId = NO_PLAYBACK
    @Volatile
    private var released = false

    init {
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                onHandleMessage(msg)
            }
        }
    }

    /**
     * 设置轨道参数。和上一次相同时什么都不做，不同就重建 [AudioTrack]。
     *
     * 校验的是入参 `sr` —— 改造前这里写的是 `if (sampleRate <= 0)`，
     * 校验的是自己的字段而不是传进来的值，等于没校验。
     */
    fun setTrackData(sr: Int, ch: Int) {
        if (ch > 2 || ch <= 0) throw IllegalArgumentException("不支持的通道数 $ch！")
        if (sr <= 0) throw IllegalArgumentException("不支持的采样率 $sr！")
        val newChannels =
            if (ch == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        synchronized(trackLock) {
            check(!released) { "SoundPlayHandler 已释放" }
            if (audioTrack != null && sr == sampleRate && newChannels == channels) return
            sampleRate = sr
            channels = newChannels
            channelCount = ch
            rebuildTrack()
        }
        Log.i(TAG, "audio track rebuilt: sampleRate=$sr channels=$ch")
    }

    private fun rebuildTrack() {
        check(!released) { "SoundPlayHandler 已释放" }
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channels, AUDIO_FORMAT)
        if (bufferSize <= 0) throw IllegalStateException("AudioTrack 不可用，采样率 $sampleRate")
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setChannelMask(channels)
                    .setSampleRate(sampleRate)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(notifyingTrack: AudioTrack) {
                    handleMarkerReached(notifyingTrack)
                }

                override fun onPeriodicNotification(notifyingTrack: AudioTrack) = Unit
            },
            handler,
        )
        track.play()
        audioTrack = track
    }

    fun beginPlayback(): Long {
        check(!released) { "SoundPlayHandler 已释放" }
        val playbackId = nextPlaybackId.getAndIncrement()
        check(handler.sendMessage(handler.obtainMessage(MSG_BEGIN, PlaybackCommand.Begin(playbackId)))) {
            "SoundPlayHandler 工作线程不可用"
        }
        return playbackId
    }

    fun sendSound(playbackId: Long, floatArray: FloatArray) {
        if (released) {
            Log.w(TAG, "drop pcm after release")
            return
        }
        handler.sendMessage(
            handler.obtainMessage(MSG_PCM, PlaybackCommand.Pcm(playbackId, floatArray))
        )
    }

    private fun onHandleMessage(msg: Message) {
        when (val command = msg.obj as PlaybackCommand) {
            is PlaybackCommand.Begin -> handleBegin(command)
            is PlaybackCommand.Pcm -> handlePcm(command)
            is PlaybackCommand.End -> handleEnd(command)
            is PlaybackCommand.Cancel -> handleCancel(command.playbackId)
            PlaybackCommand.Shutdown -> handleShutdown()
        }
    }

    private fun handleBegin(command: PlaybackCommand.Begin) {
        if (released || cancelledPlaybackIds.remove(command.playbackId)) return
        completeActivePlayback(false)
        val trackReady = runCatching {
            synchronized(trackLock) {
                rebuildTrack()
            }
        }.onFailure {
            Log.e(TAG, "start audio playback failed", it)
        }.isSuccess
        activePlayback = PlaybackState(
            id = command.playbackId,
            failed = !trackReady,
        )
        activePlaybackId = command.playbackId
    }

    private fun handlePcm(command: PlaybackCommand.Pcm) {
        val state = activePlayback
        if (released || cancelledPlaybackIds.contains(command.playbackId) ||
            state?.id != command.playbackId || state.failed
        ) {
            return
        }
        val track = synchronized(trackLock) {
            if (released || activePlaybackId != command.playbackId) return
            audioTrack ?: return
        }
        var offset = 0
        while (offset < command.samples.size &&
            !released &&
            activePlaybackId == command.playbackId
        ) {
            val written = try {
                track.write(
                    command.samples,
                    offset,
                    command.samples.size - offset,
                    AudioTrack.WRITE_BLOCKING,
                )
            } catch (e: Exception) {
                Log.e(TAG, "write pcm failed", e)
                state.failed = true
                return
            }
            if (written <= 0) {
                Log.e(TAG, "write pcm failed with code $written")
                state.failed = true
                return
            }
            offset += written
        }
        state.writtenFrames += offset / channelCount
    }

    suspend fun awaitPlaybackComplete(playbackId: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
            pendingCompletions[playbackId] = continuation
            continuation.invokeOnCancellation {
                pendingCompletions.remove(playbackId, continuation)
                cancelPlayback(playbackId)
            }
            val accepted = !released && handler.sendMessage(
                handler.obtainMessage(
                    MSG_END,
                    PlaybackCommand.End(playbackId, continuation),
                )
            )
            if (!accepted) {
                pendingCompletions.remove(playbackId, continuation)
                if (continuation.isActive) continuation.resume(false)
            }
        }

    private fun handleEnd(command: PlaybackCommand.End) {
        pendingCompletions.remove(command.playbackId, command.continuation)
        val state = activePlayback
        if (released || cancelledPlaybackIds.remove(command.playbackId) ||
            state?.id != command.playbackId
        ) {
            if (command.continuation.isActive) command.continuation.resume(false)
            return
        }
        if (state.failed || state.writtenFrames <= 0) {
            state.completion = command.continuation
            completeActivePlayback(false)
            return
        }
        val track = synchronized(trackLock) { audioTrack }
        if (track == null || state.writtenFrames > Int.MAX_VALUE) {
            if (command.continuation.isActive) command.continuation.resume(false)
            completeActivePlayback(false)
            return
        }

        state.completion = command.continuation
        val markerResult = track.setNotificationMarkerPosition(state.writtenFrames.toInt())
        if (markerResult != AudioTrack.SUCCESS) {
            Log.e(TAG, "set playback marker failed with code $markerResult")
            completeActivePlayback(false)
            return
        }

        val playedFrames = track.playbackHeadPosition.toLong() and UINT_MASK
        if (playedFrames >= state.writtenFrames) {
            completeActivePlayback(true)
        } else {
            Log.d(
                TAG,
                "await playback marker: id=${state.id} frames=${state.writtenFrames} " +
                    "head=$playedFrames"
            )
        }
    }

    private fun handleMarkerReached(notifyingTrack: AudioTrack) {
        val state = activePlayback ?: return
        if (released || notifyingTrack !== audioTrack || state.completion == null) return
        val playedFrames = notifyingTrack.playbackHeadPosition.toLong() and UINT_MASK
        if (playedFrames >= state.writtenFrames) {
            completeActivePlayback(true)
        }
    }

    fun cancelPlayback(playbackId: Long) {
        cancelledPlaybackIds += playbackId
        pendingCompletions.remove(playbackId)?.takeIf { it.isActive }?.resume(false)
        if (activePlaybackId == playbackId) {
            synchronized(trackLock) {
                if (activePlaybackId == playbackId) releaseTrack()
            }
        }
        if (!released) {
            handler.sendMessageAtFrontOfQueue(
                handler.obtainMessage(MSG_CANCEL, PlaybackCommand.Cancel(playbackId))
            )
        }
    }

    private fun handleCancel(playbackId: Long) {
        if (activePlayback?.id != playbackId) return
        cancelledPlaybackIds.remove(playbackId)
        completeActivePlayback(false)
        synchronized(trackLock) { releaseTrack() }
    }

    private fun completeActivePlayback(success: Boolean) {
        val state = activePlayback ?: return
        activePlayback = null
        activePlaybackId = NO_PLAYBACK
        if (success) {
            Log.d(TAG, "playback complete: id=${state.id} frames=${state.writtenFrames}")
        }
        state.completion?.takeIf { it.isActive }?.resume(success)
    }

    private fun releaseTrack() {
        audioTrack?.let {
            runCatching { it.stop() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        audioTrack = null
    }

    fun release() {
        if (released) return
        released = true
        synchronized(trackLock) { releaseTrack() }
        val accepted = handler.sendMessageAtFrontOfQueue(
            handler.obtainMessage(MSG_SHUTDOWN, PlaybackCommand.Shutdown)
        )
        if (!accepted) {
            handlerThread.quitSafely()
        }
    }

    private fun handleShutdown() {
        completeActivePlayback(false)
        pendingCompletions.values.forEach { continuation ->
            if (continuation.isActive) continuation.resume(false)
        }
        pendingCompletions.clear()
        handler.removeCallbacksAndMessages(null)
        cancelledPlaybackIds.clear()
        synchronized(trackLock) { releaseTrack() }
        handlerThread.quitSafely()
    }

    private data class PlaybackState(
        val id: Long,
        var writtenFrames: Long = 0,
        var failed: Boolean = false,
        var completion: CancellableContinuation<Boolean>? = null,
    )

    private sealed interface PlaybackCommand {
        data class Begin(val playbackId: Long) : PlaybackCommand
        data class Pcm(val playbackId: Long, val samples: FloatArray) : PlaybackCommand
        data class End(
            val playbackId: Long,
            val continuation: CancellableContinuation<Boolean>,
        ) : PlaybackCommand
        data class Cancel(val playbackId: Long) : PlaybackCommand
        data object Shutdown : PlaybackCommand
    }

    companion object {
        private const val TAG = "SoundPlayHandler"
        private const val DEFAULT_SAMPLE_RATE = 44100
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private const val NO_PLAYBACK = 0L
        private const val UINT_MASK = 0xFFFF_FFFFL
        private const val MSG_BEGIN = 1
        private const val MSG_PCM = 2
        private const val MSG_END = 3
        private const val MSG_CANCEL = 4
        private const val MSG_SHUTDOWN = 5
    }
}
