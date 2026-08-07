package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.chatwaifu.log.AttachmentKind
import com.chatwaifu.log.AttachmentRef
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Description: 附件字节在磁盘上的归属地。DB 里只存相对路径，字节存这里。
 *
 * ```
 * <getExternalFilesDir("attachments")>/
 *   <characterId>/
 *     <uuid>.jpg
 * ```
 *
 * 和 `ModelStorage` 同样落应用专属外部目录：零权限、卸载自动清理、不占内部存储配额。
 *
 * ## 为什么必须落地成真实文件而不是留着 `content://`
 *
 * 和 VITS 模型导入是同一个理由的变体：SAF 给的 `content://` URI 生命周期绑在
 * 那一次 `ACTION_OPEN_DOCUMENT` 的授权上，进程重启后可能已经失效，
 * 而历史重放要在任意时刻都能读到这个文件。
 *
 * ## 为什么要重编码而不是原样复制
 *
 * 各家云端 API 对单张图有体积和边长上限，超了直接 400 或者被服务端强行降采样。
 * 落盘的这份副本是**模型真正看到的东西**，历史重放必须用它，所以裁剪要在入库时做完。
 *
 * 参数按支持的**最严 provider** 取（见 [Limits]）：按最严的压，切到任何一家重放都合法；
 * 按宽松的压，切到严格的一家就重放失败。
 * **只留这一份，不留原图**——留原图能解决保真度，代价是媒体占用翻倍，而 APK 已经 440MB。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
class AttachmentStore(private val context: Context) {

    /**
     * 合规化的目标参数。各家的具体数值会随文档漂移，集中在这里改。
     */
    object Limits {
        /** 长边上限。再大对识别精度没有收益，只是白烧 token。 */
        const val IMAGE_MAX_EDGE = 1568

        /** 单张图字节上限，压到这个以下。取的是各家里最紧的那一档再留些余量。 */
        const val IMAGE_MAX_BYTES = 4L * 1024 * 1024

        /** JPEG 质量的起点和地板，超字节上限就往下退。 */
        const val JPEG_QUALITY_START = 88
        const val JPEG_QUALITY_FLOOR = 55
        const val JPEG_QUALITY_STEP = 11

        /** 音频 / 文档暂不转码，只做体积闸门。 */
        const val GENERIC_MAX_BYTES = 20L * 1024 * 1024
    }

    /**
     * 附件根目录。外部存储不可用时退回内部存储，保证功能可用而不是直接崩
     * （和 `ModelStorage.root` 一致）。
     */
    val root: File
        get() = (context.getExternalFilesDir(DIR) ?: File(context.filesDir, DIR))
            .also { if (!it.exists()) it.mkdirs() }

    /**
     * 把 [uri] 指向的图片解码、按 [Limits] 降采样并重编码成 JPEG 落地。
     *
     * @return 落地后的引用，失败返回 null（解码不了 / 写不进去）。
     */
    fun importImage(characterId: String, uri: Uri): AttachmentRef? {
        val raw = readBytes(uri) ?: return null
        val bitmap = decodeDownsampled(raw) ?: run {
            Log.e(TAG, "decode failed for $uri")
            return null
        }
        val oriented = applyExifOrientation(raw, bitmap)
        val jpeg = compressUnderBudget(oriented)
        val ref = write(
            characterId = characterId,
            bytes = jpeg,
            ext = "jpg",
            kind = AttachmentKind.IMAGE,
            mime = "image/jpeg",
            width = oriented.width,
            height = oriented.height,
        )
        oriented.recycle()
        return ref
    }

    /**
     * 音频 / 文档的直通导入：**不转码**，只做体积闸门后原样落地。
     *
     * 音频转码要上 MediaCodec，视频还要抽帧和时间轴，都不在本轮范围——
     * 所以 [AttachmentKind.VIDEO] 这里刻意不受理，宁可明确拒绝也不要落一个发不出去的附件。
     */
    fun importRaw(
        characterId: String,
        uri: Uri,
        kind: AttachmentKind,
        mime: String,
        ext: String,
        durationMs: Long? = null,
    ): AttachmentRef? {
        require(kind != AttachmentKind.IMAGE) { "use importImage() for images" }
        if (kind == AttachmentKind.VIDEO) {
            Log.w(TAG, "video import not supported yet (needs transcoding), rejected")
            return null
        }
        val bytes = readBytes(uri) ?: return null
        if (bytes.size > Limits.GENERIC_MAX_BYTES) {
            Log.w(TAG, "attachment too large: ${bytes.size} bytes, rejected")
            return null
        }
        return write(characterId, bytes, ext, kind, mime, durationMs = durationMs)
    }

    /**
     * 相对路径 → 真实文件。**文件不在时返回 null**——专属目录的内容用户能在系统设置里
     * 清掉，调用方要能降级成文本占位符而不是崩。
     */
    fun resolve(relPath: String): File? =
        File(root, relPath).takeIf { it.isFile }

    /** 删掉这些相对路径对应的文件。删角色历史时配合 [com.chatwaifu.log.ChatLogRepository.attachmentPathsOf]。 */
    fun deletePaths(relPaths: Collection<String>) {
        relPaths.forEach { path ->
            val file = File(root, path)
            if (file.isFile && !file.delete()) Log.w(TAG, "delete failed: $path")
        }
        pruneEmptyDirs()
    }

    /**
     * 孤儿文件回收：磁盘上不在 [referenced] 里的附件文件全删。
     *
     * [referenced] 必须是**全库**的引用集合（`referencedAttachmentPaths()`），
     * 按角色算会把别人的文件误判成孤儿。
     *
     * @return 删掉几个文件。
     */
    fun gc(referenced: Set<String>): Int {
        var deleted = 0
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(root).path
                if (rel !in referenced) {
                    if (file.delete()) deleted++ else Log.w(TAG, "gc: delete failed: $rel")
                }
            }
        pruneEmptyDirs()
        if (deleted > 0) Log.i(TAG, "gc removed $deleted orphan attachment file(s)")
        return deleted
    }

    // ---- 内部实现 ----

    private fun write(
        characterId: String,
        bytes: ByteArray,
        ext: String,
        kind: AttachmentKind,
        mime: String,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null,
    ): AttachmentRef? {
        val dir = File(root, characterId).also { if (!it.exists()) it.mkdirs() }
        val name = "${UUID.randomUUID()}.$ext"
        val target = File(dir, name)
        return try {
            target.writeBytes(bytes)
            AttachmentRef(
                kind = kind,
                // 相对路径，不是 absolutePath —— 见类注释第 1 条纪律
                relPath = "$characterId/$name",
                mime = mime,
                byteSize = bytes.size.toLong(),
                width = width,
                height = height,
                durationMs = durationMs,
            )
        } catch (e: Exception) {
            Log.e(TAG, "write attachment failed", e)
            target.delete()
            null
        }
    }

    private fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        Log.e(TAG, "read $uri failed", e)
        null
    }

    /**
     * 两段式解码：先只读尺寸算 `inSampleSize`，再真解码。
     * 直接全尺寸解码一张 108MP 的图会 OOM。
     *
     * `inSampleSize` 只能是 2 的幂，所以解出来的长边可能还大于上限，
     * 后面再用 [scaleToMaxEdge] 精确缩一次。
     */
    private fun decodeDownsampled(raw: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
        return scaleToMaxEdge(decoded)
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longEdge = maxOf(width, height)
        while (longEdge / 2 >= Limits.IMAGE_MAX_EDGE) {
            longEdge /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(source: Bitmap): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= Limits.IMAGE_MAX_EDGE) return source
        val ratio = Limits.IMAGE_MAX_EDGE.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== source) source.recycle()
        return scaled
    }

    /**
     * 相机拍的 JPEG 靠 EXIF 记方向，像素本身是没转的。
     * 重编码会丢掉 EXIF，所以必须在这里把旋转烧进像素，
     * 否则喂给视觉模型的是一张躺着的图。
     */
    private fun applyExifOrientation(raw: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(raw.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (e: Exception) {
            Log.w(TAG, "read exif failed, assume normal", e)
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "rotate failed, keep original orientation", e)
            bitmap
        }
    }

    /** 从 [Limits.JPEG_QUALITY_START] 往下退，直到压进字节预算或者触到质量地板。 */
    private fun compressUnderBudget(bitmap: Bitmap): ByteArray {
        var quality = Limits.JPEG_QUALITY_START
        var bytes = compress(bitmap, quality)
        while (bytes.size > Limits.IMAGE_MAX_BYTES && quality > Limits.JPEG_QUALITY_FLOOR) {
            quality -= Limits.JPEG_QUALITY_STEP
            bytes = compress(bitmap, quality)
        }
        if (bytes.size > Limits.IMAGE_MAX_BYTES) {
            // 到地板还超预算：长边已经压到 1568 了，正常图片走不到这里。
            // 留个日志而不是静默——真出现说明 Limits 需要重新校准。
            Log.w(TAG, "still ${bytes.size} bytes at quality floor $quality")
        }
        return bytes
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    /** 删文件之后留下的空角色目录顺手清掉，不然根目录会越来越脏。 */
    private fun pruneEmptyDirs() {
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.listFiles()?.isEmpty() == true) dir.delete()
        }
    }

    companion object {
        private const val TAG = "AttachmentStore"
        private const val DIR = "attachments"
    }
}
