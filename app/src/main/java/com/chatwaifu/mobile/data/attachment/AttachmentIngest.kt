package com.chatwaifu.mobile.data.attachment

import android.net.Uri
import android.util.Log
import com.chatwaifu.log.AttachmentKind
import com.chatwaifu.log.AttachmentRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Description: 附件导入的编排层：探测 → 派活 → 落盘。UI 只需要认识这一个类。
 *
 * ```
 * MediaProbe        content:// → MediaInfo（体积闸门在读字节之前）
 *      ↓
 * MediaNormalizer   按 kind 派给 Image / Audio / Video / Doc，产出临时文件
 *      ↓
 * AttachmentStore   rename 接管临时文件，产出 AttachmentRef
 * ```
 *
 * 出来的 [AttachmentRef] 列表**第一个是父附件**，后面是派生物（只有视频有）。
 * 直接交给 `ChatHistoryStore.appendUser(text, attachmentRefs = ...)` 落库即可。
 *
 * ## 为什么编排要单独一层
 *
 * 三件事的失败语义不一样，混在一起就没法给用户一个准确的提示：
 * 探测失败是「这个文件读不了」，归一化失败是「这个格式/这台设备不行」，
 * 落盘失败是「存储满了」。而且**回滚责任**在这里——归一化可能已经产出了
 * 二十多个临时文件（视频抽帧），中途落盘失败必须全部清掉，
 * 否则它们会一直占着 `externalCacheDir`（那个目录不在 `gc()` 覆盖范围内）。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
class AttachmentIngest(
    private val probe: MediaProbe,
    private val store: AttachmentStore,
    private val normalizers: Map<AttachmentKind, MediaNormalizer>,
) {

    sealed interface Progress {

        data class Working(val percent: Int, val stage: NormalizeStage) : Progress

        /** [refs] 的第一个是父附件，其余是派生物。 */
        data class Success(val refs: List<AttachmentRef>) : Progress

        data class Failed(val error: NormalizeError) : Progress
    }

    fun ingest(characterId: String, uri: Uri): Flow<Progress> = flow {
        val info = probe.probe(uri)
        if (info == null) {
            emit(Progress.Failed(NormalizeError.Unreadable))
            return@flow
        }
        val normalizer = normalizers[info.kind]
        if (normalizer == null) {
            // probe 认出了 kind 但没人处理它。当前四种都有实现，
            // 走到这里说明 AttachmentKind 加了新成员而 normalizers 没跟上
            Log.e(TAG, "no normalizer for ${info.kind}")
            emit(Progress.Failed(NormalizeError.UnsupportedFormat(info.mime)))
            return@flow
        }
        Log.d(TAG, "ingest ${info.kind} ${info.mime} ${info.byteSize}B for $characterId")

        normalizer.normalize(uri, info).collect { progress ->
            when (progress) {
                is NormalizeProgress.Working ->
                    emit(Progress.Working(progress.percent, progress.stage))

                is NormalizeProgress.Failed ->
                    emit(Progress.Failed(progress.error))

                is NormalizeProgress.Success ->
                    emit(adoptAll(characterId, progress.result, info))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 把归一化产物全部交给 [AttachmentStore]。
     *
     * 父附件先落，因为派生物的 `sourceRelPath` 要用它落地后的 relPath——
     * 这个顺序是 `AttachmentRef.sourceRelPath` 那套设计的唯一约束
     * （比自增 id 外键的约束轻得多，见那里的 KDoc）。
     *
     * 任何一步失败就整组回滚：只落了一半的视频（有父行没有帧）等于一个
     * 模型看不见的附件，比彻底失败更难排查。
     */
    private fun adoptAll(
        characterId: String,
        result: NormalizeResult,
        info: MediaProbe.MediaInfo,
    ): Progress {
        val orig = info.original
        val parent = store.adopt(characterId, result.primary, orig)
        if (parent == null) {
            result.derived.forEach { it.file.delete() }
            return Progress.Failed(NormalizeError.WriteFailed)
        }

        val derived = mutableListOf<AttachmentRef>()
        for (media in result.derived) {
            val ref = store.adopt(characterId, media, orig, sourceRelPath = parent.relPath)
            if (ref == null) {
                Log.e(TAG, "adopt derived failed, rolling back ${derived.size + 1} file(s)")
                // 已落盘的那些从磁盘上删掉；还没落盘的临时文件由 adopt 自己清了
                store.deletePaths(derived.map { it.relPath } + parent.relPath)
                result.derived.forEach { it.file.delete() }
                return Progress.Failed(NormalizeError.WriteFailed)
            }
            derived += ref
        }
        Log.i(TAG, "ingested ${parent.relPath} (+${derived.size} derived)")
        return Progress.Success(listOf(parent) + derived)
    }

    companion object {
        private const val TAG = "AttachmentIngest"
    }
}
