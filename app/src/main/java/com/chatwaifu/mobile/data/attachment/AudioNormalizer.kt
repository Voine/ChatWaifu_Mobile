package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chatwaifu.log.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Description: 音频归一化。要么直通，要么转成 16kHz 单声道 WAV。
 *
 * ## 直通判定
 *
 * 不是所有音频都需要转码。两种情况原样落地：
 *
 * | 情况 | 理由 |
 * |---|---|
 * | **MP3** | OpenAI 和 Gemini 都收，而且它已经是压缩格式。转成 WAV 只会让体积涨 10 倍 |
 * | **已经是 16k 单声道的 WAV** | 转码的输入输出完全一致，纯属白跑一遍 |
 *
 * 其余一切（m4a/aac、ogg、opus、flac、48k 立体声的 wav……）都走
 * [PcmDecoder] 转成规范形态。判定依据是 [MediaProbe] 探出来的参数，
 * 所以这一步不需要再打开文件。
 *
 * ## 为什么不留原文件
 *
 * 和图像一样：落盘那份**就是模型看到的东西**，历史重放必须用它。
 * 同时留原件能保真，代价是媒体占用翻倍，而这个 APK 已经 440MB。
 *
 * ## 超时长是拒绝而不是截断
 *
 * 截断会静默地把用户后半段的话吃掉——他不会知道模型只听了前十分钟。
 * 明确拒绝并告诉他上限，是这个工程一贯的选择（和 `ModelImporter` 的
 * `MultipleLive2DEntries` 同一个思路：宁可让用户做决定，不要替他猜）。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
class AudioNormalizer(
    private val context: Context,
    private val store: AttachmentStore,
) : MediaNormalizer {

    override val kind: AttachmentKind = AttachmentKind.AUDIO

    private val decoder = PcmDecoder(context)

    /**
     * 用 `channelFlow` 而不是 `flow`：转码是整条管线里最慢的一步（几十秒级），
     * 进度必须是真的。而 [PcmDecoder.decodeToWav] 的进度是个**普通回调**，
     * 发生在它自己的调用栈里，没法在 `flow {}` 里 `emit`（emit 是 suspend 的）。
     * `channelFlow` 的 `trySend` 可以从任意上下文调，正好对上。
     */
    override fun normalize(uri: Uri, info: MediaProbe.MediaInfo): Flow<NormalizeProgress> =
        channelFlow {
            if (info.byteSize > MediaLimits.AUDIO_SOURCE_MAX_BYTES) {
                send(
                    NormalizeProgress.Failed(
                        NormalizeError.TooLarge(info.byteSize, MediaLimits.AUDIO_SOURCE_MAX_BYTES)
                    )
                )
                return@channelFlow
            }
            val duration = info.durationMs
            if (duration != null && duration > MediaLimits.AUDIO_MAX_DURATION_MS) {
                send(
                    NormalizeProgress.Failed(
                        NormalizeError.TooLong(duration, MediaLimits.AUDIO_MAX_DURATION_MS)
                    )
                )
                return@channelFlow
            }

            val passThrough = passThroughForm(info)
            if (passThrough != null) {
                send(NormalizeProgress.Working(10, NormalizeStage.COPYING))
                val media = copyThrough(uri, info, passThrough)
                send(
                    if (media == null) {
                        NormalizeProgress.Failed(NormalizeError.Unreadable)
                    } else {
                        NormalizeProgress.Success(NormalizeResult(primary = media))
                    }
                )
                return@channelFlow
            }

            send(NormalizeProgress.Working(0, NormalizeStage.RESAMPLING))
            val target = store.newTempFile(WAV_EXT)
            val durationMs = decoder.decodeToWav(uri, target) { percent ->
                // 转码占 0..95，剩下 5 留给落盘。trySend 丢掉的中间值无所谓，
                // 进度是可以跳的，卡住才是问题
                trySend(NormalizeProgress.Working(percent * 95 / 100, NormalizeStage.RESAMPLING))
            }
            if (durationMs == null) {
                target.delete()
                send(NormalizeProgress.Failed(NormalizeError.DecodeFailed(info.mime)))
                return@channelFlow
            }
            if (durationMs <= 0) {
                // 有音轨但一个采样都没解出来，等于没有内容
                target.delete()
                send(NormalizeProgress.Failed(NormalizeError.NoTrack("audio")))
                return@channelFlow
            }

            send(
                NormalizeProgress.Success(
                    NormalizeResult(
                        primary = NormalizedMedia(
                            kind = AttachmentKind.AUDIO,
                            file = target,
                            mime = WAV_MIME,
                            ext = WAV_EXT,
                            durationMs = durationMs,
                            sampleRate = MediaLimits.AUDIO_SAMPLE_RATE,
                            channels = MediaLimits.AUDIO_CHANNELS,
                        )
                    )
                )
            )
        }.flowOn(Dispatchers.IO)

    /** 能直通就返回落盘用的 (mime, ext)，需要转码返回 null。 */
    private fun passThroughForm(info: MediaProbe.MediaInfo): Pair<String, String>? = when {
        info.mime in MP3_MIME -> MP3_MIME_CANONICAL to "mp3"

        info.mime in WAV_MIME_ALIASES &&
            info.sampleRate == MediaLimits.AUDIO_SAMPLE_RATE &&
            info.channels == MediaLimits.AUDIO_CHANNELS -> WAV_MIME to WAV_EXT

        else -> null
    }

    /**
     * 流式复制到临时文件。**不用 `readBytes()`**——直通的上限是
     * [MediaLimits.AUDIO_SOURCE_MAX_BYTES]（200MB），整份进内存必然 OOM。
     */
    private fun copyThrough(
        uri: Uri,
        info: MediaProbe.MediaInfo,
        form: Pair<String, String>,
    ): NormalizedMedia? {
        val (mime, ext) = form
        val target = store.newTempFile(ext)
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            NormalizedMedia(
                kind = AttachmentKind.AUDIO,
                file = target,
                mime = mime,
                ext = ext,
                durationMs = info.durationMs,
                sampleRate = info.sampleRate,
                channels = info.channels,
            )
        } catch (e: Exception) {
            Log.e(TAG, "pass-through copy failed for $uri", e)
            target.delete()
            null
        }
    }

    companion object {
        private const val TAG = "AudioNormalizer"

        private const val WAV_EXT = "wav"
        private const val WAV_MIME = "audio/wav"

        /** 各家 provider 给 wav 的 mime 写法不统一，探出来哪个都算。 */
        private val WAV_MIME_ALIASES = setOf("audio/wav", "audio/x-wav", "audio/wave")

        private const val MP3_MIME_CANONICAL = "audio/mpeg"
        private val MP3_MIME = setOf("audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg-3")
    }
}
