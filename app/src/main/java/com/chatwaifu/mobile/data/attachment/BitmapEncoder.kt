package com.chatwaifu.mobile.data.attachment

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import com.chatwaifu.log.AttachmentKind
import java.io.File

/**
 * Description: `Bitmap` → 落盘的临时文件。图像归一化和视频抽帧共用这一段。
 *
 * 抽出来是因为两条路的**前半段**完全不同（一个走 Coil 解码 `content://`，
 * 一个走 `MediaMetadataRetriever` 取帧），但**后半段**——缩到长边上限、
 * 选格式、压进体积预算、写文件——是同一件事，而且这段恰好是最容易写错的部分。
 *
 * ## 格式选择：alpha 决定走哪条阶梯
 *
 * 老实现无条件 `compress(JPEG)`。JPEG 没有 alpha 通道，于是带透明区域的
 * PNG 截图落盘后**透明变成黑色**——喂给视觉模型的图和用户看到的不是一张。
 *
 * 现在按 [Bitmap.hasAlpha] 分两条阶梯，两条都在三家的接受列表里
 * （jpeg / png / webp 是 OpenAI、Anthropic、Gemini 的公共子集）：
 *
 * | 源 | 阶梯 |
 * |---|---|
 * | 不透明 | JPEG，质量从 88 退到 55 |
 * | 带 alpha | PNG（无损，一次）→ 超预算则 WebP 有损，质量同样往下退 |
 *
 * 带 alpha 时**不退回 JPEG**：那等于把「保住透明度」这个目的丢掉。
 * WebP 有损是唯一既能压又保 alpha 的公共格式。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
internal class BitmapEncoder(private val store: AttachmentStore) {

    /**
     * 缩放 + 编码 + 落临时文件。
     *
     * @param maxEdge 长边上限。单张图用 [MediaLimits.IMAGE_MAX_EDGE]，
     *   视频帧用小得多的 [MediaLimits.VIDEO_FRAME_MAX_EDGE]（见那里的注释）。
     * @param posMs 这张图在源视频里的时间位置，单张图传 null。
     * @return null 表示写不出去。**无论成功失败 [bitmap] 都会被 recycle**，
     *   调用方不要再用它——抽帧那条路一次会解出几十张，漏一张就是几 MB。
     */
    fun encode(bitmap: Bitmap, maxEdge: Int, posMs: Long? = null): NormalizedMedia? {
        val scaled = scaleToMaxEdge(bitmap, maxEdge)
        return try {
            writeLadder(scaled, posMs)
        } finally {
            scaled.recycle()
        }
    }

    /**
     * 精确缩到长边 [maxEdge] 以内，**不放大**。
     *
     * 已经够小就原样返回（此时返回的就是入参，[encode] 的 recycle 语义仍然成立）。
     * 不放大是因为放大不增加信息量，只让 token 变贵——图片的 token 数是分辨率的函数。
     */
    private fun scaleToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= maxEdge) return source
        val ratio = maxEdge.toFloat() / longEdge
        val scaled = source.scale(
            width = (source.width * ratio).toInt().coerceAtLeast(1),
            height = (source.height * ratio).toInt().coerceAtLeast(1),
        )
        if (scaled !== source) source.recycle()
        return scaled
    }

    private fun writeLadder(bitmap: Bitmap, posMs: Long?): NormalizedMedia? {
        val ladder = if (bitmap.hasAlpha()) alphaLadder() else opaqueLadder()
        for (step in ladder) {
            val file = store.newTempFile(step.ext)
            if (!compress(bitmap, step, file)) {
                file.delete()
                continue
            }
            val size = file.length()
            if (size <= MediaLimits.IMAGE_MAX_BYTES) {
                return NormalizedMedia(
                    kind = AttachmentKind.IMAGE,
                    file = file,
                    mime = step.mime,
                    ext = step.ext,
                    width = bitmap.width,
                    height = bitmap.height,
                    posMs = posMs,
                )
            }
            // 还超预算：留着最后一级的产物，前面的删掉重试
            if (step === ladder.last()) {
                // 长边已经压到上限、质量也到地板了还超预算，正常图片走不到这里。
                // 留个日志而不是静默失败——真出现说明 MediaLimits 需要重新校准。
                Log.w(TAG, "still $size bytes at ${step.format}/${step.quality}, keeping anyway")
                return NormalizedMedia(
                    kind = AttachmentKind.IMAGE,
                    file = file,
                    mime = step.mime,
                    ext = step.ext,
                    width = bitmap.width,
                    height = bitmap.height,
                    posMs = posMs,
                )
            }
            file.delete()
        }
        Log.e(TAG, "all encode attempts failed")
        return null
    }

    private fun compress(bitmap: Bitmap, step: Step, file: File): Boolean = try {
        // 直接写进 FileOutputStream，不经过 ByteArrayOutputStream ——
        // 中间那份 ByteArray 是纯浪费，而且抽帧时会几十次反复申请几 MB
        file.outputStream().use { out ->
            bitmap.compress(step.format, step.quality, out)
        }
    } catch (e: Exception) {
        Log.e(TAG, "compress to ${file.name} failed", e)
        false
    }

    private fun opaqueLadder(): List<Step> =
        qualitySteps().map { Step(Bitmap.CompressFormat.JPEG, it, "jpg", "image/jpeg") }

    /** PNG 先试一次（无损），超预算再走 WebP 有损阶梯。 */
    private fun alphaLadder(): List<Step> =
        listOf(Step(Bitmap.CompressFormat.PNG, 100, "png", "image/png")) +
            qualitySteps().map { Step(webpLossy(), it, "webp", "image/webp") }

    private fun qualitySteps(): List<Int> = generateSequence(MediaLimits.JPEG_QUALITY_START) {
        (it - MediaLimits.JPEG_QUALITY_STEP).takeIf { q -> q >= MediaLimits.JPEG_QUALITY_FLOOR }
    }.toList()

    /**
     * `WEBP_LOSSY` 是 API 30 才有的枚举值，minSdk 是 28，所以低两档上只能用已废弃的
     * `WEBP`——它在 quality < 100 时本来就是有损编码，行为一致。
     */
    @Suppress("DEPRECATION")
    private fun webpLossy(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private data class Step(
        val format: Bitmap.CompressFormat,
        val quality: Int,
        val ext: String,
        val mime: String,
    )

    companion object {
        private const val TAG = "BitmapEncoder"
    }
}
