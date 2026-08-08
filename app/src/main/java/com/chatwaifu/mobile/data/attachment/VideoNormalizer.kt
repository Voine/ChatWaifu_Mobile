package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.chatwaifu.log.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Description: 视频归一化 = **抽帧 + 抽音轨**，不转码视频本身。
 *
 * ## 为什么是抽帧而不是转码
 *
 * 能力面决定的。2026 年只有 Gemini 一家真的收视频输入（OpenAI 的 Responses 不收，
 * Anthropic 只有图和 PDF），而这个工程的 Gemini provider 目前还是 stub。
 * 为一家还没接通的基座去做视频转码，要么引 FFmpeg（ARTHENICA 的 ffmpeg-kit
 * 2025 年 1 月已归档、Maven 产物下架，自己编是每 ABI +20~40MB 加 LGPL/GPL 合规审查），
 * 要么上 `media3-transformer`（连带 exoplayer 一整套）。
 *
 * 而抽帧只用平台的 `MediaMetadataRetriever`，**零新增依赖**，并且换来的能力更广：
 * 「看视频」在任何有 vision 的基座上都能用。代价写清楚——
 * 丢掉了帧间连续性，「这段视频里发生了什么」够用，
 * 「数一下他挥了几次手」不够。等 Gemini provider 接通、真有人要原生视频了再说。
 *
 * ## 一个视频落几行
 *
 * ```
 * chat_attachment
 *   ├─ VIDEO  <uuid>.mp4   sourceRelPath = null   ← 原视频，只给 UI 回放
 *   ├─ IMAGE  <uuid>.jpg   sourceRelPath = ↑, posMs = 0
 *   ├─ IMAGE  <uuid>.jpg   sourceRelPath = ↑, posMs = 2000
 *   │  ...
 *   └─ AUDIO  <uuid>.wav   sourceRelPath = ↑      ← 抽出来的音轨，16k mono
 * ```
 *
 * **只有派生行会被映射成 content block**，父行模型看不到。
 * 这条规则在 `ChatHistoryStore.toCoreMessage()` 里执行。
 *
 * 父行仍然原样存原视频：它是用户真实给的内容，气泡里要能点开播放。
 * 这是唯一一处「落盘的不等于模型看到的」，所以值得在这里说清。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
class VideoNormalizer(
    private val context: Context,
    private val store: AttachmentStore,
) : MediaNormalizer {

    override val kind: AttachmentKind = AttachmentKind.VIDEO

    private val encoder = BitmapEncoder(store)
    private val pcm = PcmDecoder(context)

    override fun normalize(uri: Uri, info: MediaProbe.MediaInfo): Flow<NormalizeProgress> =
        channelFlow {
            if (info.byteSize > MediaLimits.VIDEO_SOURCE_MAX_BYTES) {
                send(
                    NormalizeProgress.Failed(
                        NormalizeError.TooLarge(info.byteSize, MediaLimits.VIDEO_SOURCE_MAX_BYTES)
                    )
                )
                return@channelFlow
            }
            val duration = info.durationMs
            if (duration == null || duration <= 0) {
                // 时长探不出来就没法定采样点，而且大概率这个容器本身有问题
                send(NormalizeProgress.Failed(NormalizeError.DecodeFailed(info.mime)))
                return@channelFlow
            }
            if (duration > MediaLimits.VIDEO_MAX_DURATION_MS) {
                send(
                    NormalizeProgress.Failed(
                        NormalizeError.TooLong(duration, MediaLimits.VIDEO_MAX_DURATION_MS)
                    )
                )
                return@channelFlow
            }

            val produced = mutableListOf<File>()

            // ---- 1. 原视频直通落地，父行用 ----
            send(NormalizeProgress.Working(5, NormalizeStage.COPYING))
            val original = copyThrough(uri, info)
            if (original == null) {
                send(NormalizeProgress.Failed(NormalizeError.Unreadable))
                return@channelFlow
            }
            produced += original.file

            // ---- 2. 抽帧 ----
            send(NormalizeProgress.Working(20, NormalizeStage.EXTRACTING_FRAMES))
            val frames = extractFrames(uri, duration) { percent ->
                trySend(
                    NormalizeProgress.Working(
                        20 + percent * 60 / 100,
                        NormalizeStage.EXTRACTING_FRAMES,
                    )
                )
            }
            if (frames.isEmpty()) {
                // 一帧都抽不出来 = 这个视频对模型毫无价值，别落一个只能自己看的附件
                produced.forEach { it.delete() }
                send(NormalizeProgress.Failed(NormalizeError.DecodeFailed(info.mime)))
                return@channelFlow
            }
            produced += frames.map { it.file }

            // ---- 3. 抽音轨（可选，没有就算了）----
            val audio = if (info.hasAudioTrack) {
                send(NormalizeProgress.Working(85, NormalizeStage.RESAMPLING))
                extractAudio(uri)?.also { produced += it.file }
            } else {
                Log.d(TAG, "no audio track, frames only")
                null
            }

            send(
                NormalizeProgress.Success(
                    NormalizeResult(
                        primary = original,
                        derived = frames + listOfNotNull(audio),
                    )
                )
            )
        }.flowOn(Dispatchers.IO)

    /** 原视频原样复制。流式，不经过 `ByteArray`——上限是 500MB。 */
    private fun copyThrough(uri: Uri, info: MediaProbe.MediaInfo): NormalizedMedia? {
        val ext = info.mime.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "mp4"
        val target = store.newTempFile(ext)
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            NormalizedMedia(
                kind = AttachmentKind.VIDEO,
                file = target,
                mime = info.mime,
                ext = ext,
                width = info.declaredWidth,
                height = info.declaredHeight,
                durationMs = info.durationMs,
            )
        } catch (e: Exception) {
            Log.e(TAG, "copy video failed for $uri", e)
            target.delete()
            null
        }
    }

    /**
     * 按 [MediaLimits.VIDEO_FRAME_INTERVAL_MS] 采样，最多 [MediaLimits.VIDEO_MAX_FRAMES] 张。
     *
     * 超过上限时**拉大间隔**而不是只取前 N 张——只取前面等于把后半段视频扔了，
     * 而均匀铺开至少能覆盖全片。
     *
     * 用 `getScaledFrameAtTime` 而不是 `getFrameAtTime` + 自己缩：
     * 前者在 native 层就按目标尺寸解，省掉一次全分辨率 bitmap 的分配
     * （4K 视频的一帧是 33MB，抽 24 张就是 800MB 的峰值）。
     * `OPTION_CLOSEST_SYNC` 而不是 `OPTION_CLOSEST`：只取关键帧，快得多，
     * 而采样间隔是秒级，落在哪个关键帧上无所谓。
     */
    private suspend fun extractFrames(
        uri: Uri,
        durationMs: Long,
        onProgress: (Int) -> Unit,
    ): List<NormalizedMedia> {
        val interval = frameInterval(durationMs)
        val timestamps = generateSequence(0L) { it + interval }
            .takeWhile { it < durationMs }
            .take(MediaLimits.VIDEO_MAX_FRAMES)
            .toList()

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<NormalizedMedia>()
        try {
            retriever.setDataSource(context, uri)
            timestamps.forEachIndexed { index, posMs ->
                // 抽帧是可取消的长任务，用户退出页面就别继续解了
                if (!currentCoroutineContext().isActive) return@forEachIndexed
                val bitmap = frameAt(retriever, posMs)
                if (bitmap == null) {
                    Log.w(TAG, "no frame at ${posMs}ms")
                    return@forEachIndexed
                }
                // encode 负责 recycle，无论成败
                encoder.encode(bitmap, MediaLimits.VIDEO_FRAME_MAX_EDGE, posMs)
                    ?.let { frames += it }
                onProgress((index + 1) * 100 / timestamps.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "extract frames failed for $uri", e)
        } finally {
            runCatching { retriever.release() }
        }
        Log.i(TAG, "extracted ${frames.size}/${timestamps.size} frame(s), interval=${interval}ms")
        return frames
    }

    /** 帧数超上限就等比拉大间隔，让采样点铺满整段视频。 */
    private fun frameInterval(durationMs: Long): Long {
        val wanted = MediaLimits.VIDEO_FRAME_INTERVAL_MS
        val count = durationMs / wanted
        if (count <= MediaLimits.VIDEO_MAX_FRAMES) return wanted
        return durationMs / MediaLimits.VIDEO_MAX_FRAMES
    }

    private fun frameAt(retriever: MediaMetadataRetriever, posMs: Long): Bitmap? = try {
        retriever.getScaledFrameAtTime(
            posMs * 1000,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            MediaLimits.VIDEO_FRAME_MAX_EDGE,
            MediaLimits.VIDEO_FRAME_MAX_EDGE,
        )
    } catch (e: Exception) {
        Log.w(TAG, "getScaledFrameAtTime(${posMs}ms) failed", e)
        null
    }

    /** 抽音轨，复用音频那条管线的产物形态（16k mono WAV）。 */
    private fun extractAudio(uri: Uri): NormalizedMedia? {
        val target = store.newTempFile("wav")
        val durationMs = pcm.decodeToWav(uri, target)
        if (durationMs == null || durationMs <= 0) {
            target.delete()
            Log.w(TAG, "audio track extraction produced nothing")
            return null
        }
        return NormalizedMedia(
            kind = AttachmentKind.AUDIO,
            file = target,
            mime = "audio/wav",
            ext = "wav",
            durationMs = durationMs,
            sampleRate = MediaLimits.AUDIO_SAMPLE_RATE,
            channels = MediaLimits.AUDIO_CHANNELS,
        )
    }

    companion object {
        private const val TAG = "VideoNormalizer"
    }
}
