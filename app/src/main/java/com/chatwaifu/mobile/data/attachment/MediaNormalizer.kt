package com.chatwaifu.mobile.data.attachment

import android.net.Uri
import com.chatwaifu.log.AttachmentKind
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Description: 把用户选中的一份媒体，变成「能发给任何一家基座」的规范形态。
 *
 * ## 归一化到底在做什么
 *
 * 落盘那份副本**就是模型看到的东西**，所以格式裁剪必须在入库时做完，
 * 而不是发送时临时算——否则历史重放和当初发出去的不是同一份内容。
 * 参数按最严的 provider 取（见 [MediaLimits]）。
 *
 * ## 三条实现纪律
 *
 * 1. **产物是 [File] 不是 `ByteArray`**。视频和长音频不可能整份进内存，
 *    所以归一化写临时文件，由 [AttachmentStore.adopt] rename 接管。
 *    这也顺手把老实现「先 readBytes 再判体积」的 OOM 洞堵死了
 * 2. **失败要说清是哪种失败**（[NormalizeError]），因为 UI 要给出「用户下一步该干什么」。
 *    和 `ImportError` 同一个思路
 * 3. **产物的元数据以实际产物为准**。probe 给的是源文件的声明值，
 *    带 EXIF 方向的图旋转后宽高会互换，重采样后音频参数会变
 *
 * ## 派生物
 *
 * [NormalizeResult] 分 primary 和 derived 是为了视频：一个视频落 1 + N + 1 行——
 * 父行（原视频，只给 UI 回放）、N 行抽帧（IMAGE）、1 行音轨（AUDIO）。
 * **只有派生行会被映射成 content block**，父行模型看不到。
 * 详见 `AttachmentRef.sourceRelPath` 的 KDoc 和 docs/media-pipeline.md。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
interface MediaNormalizer {

    /** 这个实现能处理哪种媒体。[AttachmentIngest] 按它派活。 */
    val kind: AttachmentKind

    /**
     * 归一化 [uri]，进度和结果都从 [Flow] 出来。
     *
     * [info] 是 [MediaProbe] 已经探好的元数据，实现**不要再探一遍**。
     *
     * 终态一定是 [NormalizeProgress.Success] 或 [NormalizeProgress.Failed] 之一。
     * 失败时实现负责把自己开出去的临时文件清干净。
     */
    fun normalize(uri: Uri, info: MediaProbe.MediaInfo): Flow<NormalizeProgress>
}

/**
 * 用户原本给的形态。落库写进 `origMime` / `origByteSize`，
 * UI 上要能说清「12MB 的 HEIC 存成了 380KB 的 JPEG」。
 */
data class OriginalForm(val mime: String, val byteSize: Long) {
    init {
        require(byteSize >= 0) { "byteSize must be >= 0, probe 探不到时传 0" }
    }
}

/**
 * 一份归一化产物：临时文件 + 它的实际元数据。
 *
 * [file] 的所有权在 [AttachmentStore.adopt] 调用后转移（文件被 rename 走），
 * 之后不要再读它。
 */
data class NormalizedMedia(
    val kind: AttachmentKind,
    val file: File,
    val mime: String,
    /** 落盘用的扩展名，不带点。 */
    val ext: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    /** 派生物在源里的时间位置，抽帧用。primary 恒为 null。 */
    val posMs: Long? = null,
)

/**
 * @param primary 用户给的那份东西归一化后的形态。
 * @param derived 从 [primary] 派生出来的产物，只有视频有。
 */
data class NormalizeResult(
    val primary: NormalizedMedia,
    val derived: List<NormalizedMedia> = emptyList(),
) {
    /** 出错回滚时要删的全部临时文件。 */
    val allFiles: List<File> get() = listOf(primary.file) + derived.map { it.file }
}

/** 当前在哪一步。UI 拿它显示文案，比一个裸百分比可读。 */
enum class NormalizeStage {
    DECODING,
    SCALING,
    ENCODING,
    RESAMPLING,
    EXTRACTING_FRAMES,
    COPYING,
}

sealed interface NormalizeProgress {

    /** [percent] 为 0..100。算不出总量时可能长时间停在同一个值（和 `ImportProgress.Extracting` 一样）。 */
    data class Working(val percent: Int, val stage: NormalizeStage) : NormalizeProgress

    data class Success(val result: NormalizeResult) : NormalizeProgress

    data class Failed(val error: NormalizeError) : NormalizeProgress
}

/**
 * 每个分支都要能在 UI 上给出「用户下一步该干什么」的提示，
 * 所以按失败原因分类而不是统一抛一个 message。和 `ImportError` 同一个思路。
 */
sealed interface NormalizeError {

    /** 打不开这个 uri：SAF 授权失效、文件被删、provider 抽风。 */
    data object Unreadable : NormalizeError

    /** 原文件超过入口闸门。提示应该是「换一个小点的」而不是「失败了」。 */
    data class TooLarge(val byteSize: Long, val limit: Long) : NormalizeError

    /** 时长超限。 */
    data class TooLong(val durationMs: Long, val limitMs: Long) : NormalizeError

    /** mime 认得出来但这个工程不受理（比如非 PDF 的文档）。 */
    data class UnsupportedFormat(val mime: String) : NormalizeError

    /**
     * 格式对但解不开。最常见的是 ROM 缺对应的解码器
     * （AVIF 在 API 31 以下、厂商阉掉的编解码器），提示要区别于 [UnsupportedFormat]：
     * 这是「你这台设备不行」而不是「这个格式不行」。
     */
    data class DecodeFailed(val mime: String) : NormalizeError

    /** 容器里没有需要的轨道，比如一个没有音轨的视频要抽音轨。 */
    data class NoTrack(val what: String) : NormalizeError

    /** 临时文件写不进去，通常是存储满了。 */
    data object WriteFailed : NormalizeError
}
