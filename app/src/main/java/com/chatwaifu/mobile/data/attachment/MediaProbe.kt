package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.chatwaifu.log.AttachmentKind

/**
 * Description: `content://` → [MediaInfo]。**在读任何字节之前**把这份媒体是什么、多大、
 * 什么参数问清楚。
 *
 * ## 为什么必须有这一步
 *
 * 老实现是 `openInputStream().readBytes()` 拿到 `ByteArray` 之后再判体积。
 * 那道闸门形同虚设：选一个 500MB 的视频，OOM 发生在闸门之前。
 * 所以体积必须从**元数据**拿（`OpenableColumns.SIZE`），而不是从字节数拿。
 *
 * ## 探测手段按媒体类型分
 *
 * - 体积 / 文件名：`ContentResolver.query` 拿 `OpenableColumns`，
 *   退路是 `openAssetFileDescriptor().length`
 * - 图像尺寸：`BitmapFactory` 的 `inJustDecodeBounds`，只读文件头不解像素
 * - 音视频：`MediaMetadataRetriever` 拿时长和画面尺寸，
 *   `MediaExtractor` 拿音轨的采样率 / 声道数 / 编码
 *
 * 探测**全部失败也不抛异常**：拿不到的字段留 null，由调用方决定是拒绝还是放行。
 * 这些接口面对畸形文件的行为很不统一（有的返回 null，有的抛 `IllegalArgumentException`，
 * 有的抛 `RuntimeException`），逐个 try 是唯一可靠的写法。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
class MediaProbe(private val context: Context) {

    /**
     * @param mime 最终判定的 mime。`ContentResolver.getType()` 说不清时会退到按扩展名猜，
     *   所以它**不保证**和文件真实内容一致——归一化那一步解不开会再拒一次。
     * @param declaredWidth / @param declaredHeight 文件头里写的尺寸。
     *   注意带 EXIF 方向的 JPEG 这两个值是**旋转前**的，
     *   落库用的尺寸要取归一化产物的实际尺寸，不是这里的。
     */
    data class MediaInfo(
        val kind: AttachmentKind,
        val mime: String,
        val byteSize: Long,
        val displayName: String?,
        val declaredWidth: Int? = null,
        val declaredHeight: Int? = null,
        val durationMs: Long? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val audioCodec: String? = null,
        val hasAudioTrack: Boolean = false,
    ) {
        /**
         * 用户原本给的形态，落库时写进 `origMime` / `origByteSize`。
         *
         * [byteSize] 探不到时是 -1（闸门据此放行），但 `origByteSize` 那一列是
         * NOT NULL 且 0 表示「未记录」，所以这里把负数折成 0 ——
         * 不折的话 [OriginalForm] 的 require 会在落库前一刻抛出来。
         */
        val original: OriginalForm get() = OriginalForm(mime, byteSize.coerceAtLeast(0))
    }

    /** 探不出体积时用这个值：让闸门放行，由后续步骤自己失败。 */
    private val unknownSize = -1L

    fun probe(uri: Uri): MediaInfo? {
        val (name, size) = queryNameAndSize(uri)
        val mime = resolveMime(uri, name) ?: run {
            Log.w(TAG, "cannot determine mime for $uri")
            return null
        }
        val kind = kindOf(mime) ?: run {
            Log.w(TAG, "unsupported mime: $mime")
            return null
        }
        val base = MediaInfo(
            kind = kind,
            mime = mime,
            byteSize = size,
            displayName = name,
        )
        return when (kind) {
            AttachmentKind.IMAGE -> base.withImageBounds(uri)
            AttachmentKind.AUDIO, AttachmentKind.VIDEO -> base.withAvMetadata(uri)
            AttachmentKind.DOC -> base
        }
    }

    // ---- 体积和文件名 ----

    /**
     * `OpenableColumns` 是 SAF / photo picker / 分享进来的 `content://` 都会实现的那组列。
     * 有的 provider 只实现一半（size 为 null），所以退到
     * `openAssetFileDescriptor().length`——它对大多数 provider 也能给出真实长度。
     */
    private fun queryNameAndSize(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = unknownSize
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { name = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { size = cursor.getLong(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "query OpenableColumns failed for $uri", e)
        }
        if (size < 0) size = descriptorLength(uri)
        return name to size
    }

    private fun descriptorLength(uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
            fd.length.takeIf { it >= 0 } ?: unknownSize
        } ?: unknownSize
    } catch (e: Exception) {
        Log.w(TAG, "openAssetFileDescriptor failed for $uri", e)
        unknownSize
    }

    // ---- mime 判定 ----

    /**
     * `getType()` 优先，说不清（null / `application/octet-stream`）时按扩展名猜。
     *
     * [EXTRA_MIME] 是给 `MimeTypeMap` 补的几个格式：它的内置表在部分 ROM 上不认
     * heic/heif/avif，而这几个恰好是相机和 iPhone 分享过来的常见格式，
     * 认不出来就会被判成 DOC 走直通，落一个发不出去的附件。
     */
    private fun resolveMime(uri: Uri, displayName: String?): String? {
        val declared = try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            Log.w(TAG, "getType failed for $uri", e)
            null
        }
        if (declared != null && declared != OCTET_STREAM) return declared

        val ext = (displayName ?: uri.lastPathSegment.orEmpty())
            .substringAfterLast('.', "")
            .lowercase()
        if (ext.isEmpty()) return declared
        return EXTRA_MIME[ext]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: declared
    }

    /** mime → 附件类型。认不出来返回 null（= 拒绝导入），不要默默当 DOC 收下。 */
    private fun kindOf(mime: String): AttachmentKind? = when {
        mime.startsWith("image/") -> AttachmentKind.IMAGE
        mime.startsWith("audio/") -> AttachmentKind.AUDIO
        mime.startsWith("video/") -> AttachmentKind.VIDEO
        mime in DOC_MIME -> AttachmentKind.DOC
        else -> null
    }

    // ---- 图像 ----

    /** `inJustDecodeBounds` 只读文件头，不分配像素内存，所以对多大的图都安全。 */
    private fun MediaInfo.withImageBounds(uri: Uri): MediaInfo {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "decode bounds failed for $uri", e)
        }
        return if (opts.outWidth > 0 && opts.outHeight > 0) {
            copy(declaredWidth = opts.outWidth, declaredHeight = opts.outHeight)
        } else {
            this
        }
    }

    // ---- 音视频 ----

    private fun MediaInfo.withAvMetadata(uri: Uri): MediaInfo {
        val meta = retrieveMetadata(uri)
        val track = audioTrackFormat(uri)
        return copy(
            declaredWidth = meta?.width ?: declaredWidth,
            declaredHeight = meta?.height ?: declaredHeight,
            durationMs = meta?.durationMs,
            sampleRate = track?.sampleRate,
            channels = track?.channels,
            audioCodec = track?.codec,
            hasAudioTrack = track != null,
        )
    }

    private data class AvMeta(val durationMs: Long?, val width: Int?, val height: Int?)

    /**
     * `MediaMetadataRetriever` 在 API 29 起才实现 `AutoCloseable`，minSdk 是 28，
     * 所以老实实 try/finally 调 `release()`。不释放会漏一个 native 提取器。
     */
    private fun retrieveMetadata(uri: Uri): AvMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            AvMeta(
                durationMs = retriever.int(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong(),
                width = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                height = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed for $uri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.int(key: Int): Int? =
        extractMetadata(key)?.toIntOrNull()

    private data class AudioTrack(val sampleRate: Int?, val channels: Int?, val codec: String?)

    /**
     * 找第一条音轨的参数。视频也走这里——抽音轨那一步要知道它有没有声音。
     *
     * `MediaExtractor` 对非法文件会抛 `IOException` 或 `IllegalArgumentException`，
     * 两者都当「探不到」处理。
     */
    private fun audioTrackFormat(uri: Uri): AudioTrack? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount)
                .asSequence()
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.let { format ->
                    AudioTrack(
                        sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
                        channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
                        codec = format.getString(MediaFormat.KEY_MIME),
                    )
                }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor failed for $uri", e)
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** `getInteger` 在键不存在时抛 `NullPointerException`，先问一句 `containsKey`。 */
    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    companion object {
        private const val TAG = "MediaProbe"
        private const val OCTET_STREAM = "application/octet-stream"

        /** `MimeTypeMap` 在部分 ROM 上不认的几个格式。 */
        private val EXTRA_MIME = mapOf(
            "heic" to "image/heic",
            "heif" to "image/heif",
            "avif" to "image/avif",
            "webp" to "image/webp",
            "opus" to "audio/opus",
            "m4a" to "audio/mp4",
        )

        /** 当 DOC 收的类型。刻意是白名单：各家的文档输入实质上只支持 PDF。 */
        private val DOC_MIME = setOf("application/pdf")
    }
}
