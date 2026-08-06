package com.chatwaifu.chat.net

import android.util.Base64
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.MediaSource
import java.io.File

/**
 * Description: 多模态数据 → 各家 wire format 的编码。
 *
 * 三家最终都收 base64 内联或者远端引用，差别只在字段名，所以编码这一步能共用。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
object MediaEncoding {

    /** base64 内联的结果。 */
    data class Inline(val mimeType: String, val base64: String) {
        /** OpenAI 的 `image_url` / `file_data` 收的是 data URI 形式。 */
        val dataUri: String get() = "data:$mimeType;base64,$base64"
    }

    /**
     * 把来源编码成 base64。[MediaSource.Url] / [MediaSource.RemoteFileId] 不是内联形式，返回 null，
     * 调用方要走各自的 url / file_id 字段。
     */
    fun inline(source: MediaSource): Inline? = when (source) {
        is MediaSource.Bytes -> Inline(source.mimeType, encode(source.bytes))
        is MediaSource.LocalPath -> {
            val file = File(source.path)
            if (!file.isFile) {
                throw ChatError.Unknown("media file not found: ${source.path}")
            }
            Inline(source.mimeType, encode(file.readBytes()))
        }

        is MediaSource.Url, is MediaSource.RemoteFileId -> null
    }

    /**
     * 给「只认一个 url 字符串」的字段用（OpenAI 的 `image_url.url`）：
     * 能内联就转 data URI，是 URL 就原样返回，file id 形式则报错让调用方改走 `file_id`。
     */
    fun asUrlOrDataUri(source: MediaSource, what: String): String = when (source) {
        is MediaSource.Url -> source.url
        is MediaSource.Bytes, is MediaSource.LocalPath -> inline(source)!!.dataUri
        is MediaSource.RemoteFileId ->
            throw ChatError.CapabilityUnsupported("$what by remote file id")
    }

    // NO_WRAP：默认会插换行，塞进 JSON 字符串里没必要
    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
