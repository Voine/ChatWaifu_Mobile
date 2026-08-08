package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chatwaifu.log.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Description: 文档归一化 = 体积闸门 + 原样落地。
 *
 * 只受理 PDF（[MediaProbe] 的 `DOC_MIME` 白名单已经拦过一道）。理由是各家的
 * "文件输入" 实质上就是 PDF：服务端把页面栅格化后当图看，所以能力也跟着视觉模型走
 * （`ModelInfo.supportsFileInput` 默认跟随 `supportsImageInput` 就是这个道理）。
 * docx / xlsx 这些要先自己解析成文本或图片，那是另一件事。
 *
 * **不做任何内容改写**：PDF 重新编码一遍除了引入风险没有收益，
 * 而分页/裁剪应该由用户决定，不是导入时替他猜。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
class DocNormalizer(
    private val context: Context,
    private val store: AttachmentStore,
) : MediaNormalizer {

    override val kind: AttachmentKind = AttachmentKind.DOC

    override fun normalize(uri: Uri, info: MediaProbe.MediaInfo): Flow<NormalizeProgress> = flow {
        if (info.byteSize > MediaLimits.DOC_MAX_BYTES) {
            emit(
                NormalizeProgress.Failed(
                    NormalizeError.TooLarge(info.byteSize, MediaLimits.DOC_MAX_BYTES)
                )
            )
            return@flow
        }

        emit(NormalizeProgress.Working(10, NormalizeStage.COPYING))
        val target = store.newTempFile(EXT)
        val ok = try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                false
            } else {
                stream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "copy doc failed for $uri", e)
            false
        }

        if (!ok) {
            target.delete()
            emit(NormalizeProgress.Failed(NormalizeError.Unreadable))
            return@flow
        }

        emit(
            NormalizeProgress.Success(
                NormalizeResult(
                    primary = NormalizedMedia(
                        kind = AttachmentKind.DOC,
                        file = target,
                        mime = info.mime,
                        ext = EXT,
                    )
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "DocNormalizer"
        private const val EXT = "pdf"
    }
}
