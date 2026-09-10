package com.chatwaifu.vits

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log

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
    private var audioTrack: AudioTrack? = null
    private var sampleRate = DEFAULT_SAMPLE_RATE
    private var channels = AudioFormat.CHANNEL_OUT_MONO

    init {
        val handlerThread = HandlerThread("SoundPlayHandler")
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
        if (audioTrack != null && sr == sampleRate && newChannels == channels) return

        sampleRate = sr
        channels = newChannels
        rebuildTrack()
        Log.i(TAG, "audio track rebuilt: sampleRate=$sr channels=$ch")
    }

    private fun rebuildTrack() {
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channels, AUDIO_FORMAT)
        if (bufferSize <= 0) throw IllegalStateException("AudioTrack 不可用，采样率 $sampleRate")
        audioTrack = AudioTrack.Builder()
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
            .also { it.play() }
    }

    fun sendSound(floatArray: FloatArray) {
        handler.sendMessage(Message.obtain(handler, 0, floatArray))
    }

    private fun onHandleMessage(msg: Message) {
        val sound = msg.obj as FloatArray
        // 没设过参数就先按默认值把 track 建起来，别静默丢掉这段音频
        val track = audioTrack ?: run {
            runCatching { rebuildTrack() }.onFailure {
                Log.e(TAG, "build audio track failed", it)
                return
            }
            audioTrack ?: return
        }
        try {
            track.write(sound, 0, sound.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            Log.e(TAG, "write pcm failed", e)
        }
    }

    fun release() {
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "SoundPlayHandler"
        private const val DEFAULT_SAMPLE_RATE = 44100
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    }
}
