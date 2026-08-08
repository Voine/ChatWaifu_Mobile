package com.chatwaifu.log

/**
 * Description: 一条消息里的一个富媒体附件——**只是引用，不含字节**。
 *
 * 字节存在应用私有空间里（合规化裁剪后的副本），DB 里只留 [relPath]。
 * 三条纪律（详见 docs/chat-storage.md）：
 *
 * 1. **[relPath] 是相对路径，绝对路径不能进 DB**。`getExternalFilesDir()` 会变
 *    （换机恢复、`/storage/emulated/0` vs `10`、SD 卡迁移），存绝对路径是 Android 经典坑
 * 2. **读路径必须能处理「行在、文件没了」**。专属目录内容用户能在系统设置里清掉，
 *    降级成文本占位符，不要崩
 * 3. **删记录要单独删文件**。FK cascade 只清行
 *
 * Author: Voine
 * Date: 2026/8/7
 */
data class AttachmentRef(
    val id: Long = NO_ID,

    /** 归属消息。落库前为 [NO_ID]，由仓库在插入消息后回填。 */
    val messageId: Long = NO_ID,

    val kind: AttachmentKind,

    /** 相对附件根目录的路径，形如 `<characterId>/<uuid>.jpg`。 */
    val relPath: String,

    val mime: String,

    val byteSize: Long = 0,

    /**
     * 图片/视频的像素尺寸，音频/视频的时长。
     *
     * 存下来是为了让 [com.chatwaifu.log] 的调用方按**真实尺寸**估 token：
     * 各家的图片计费都是分辨率的函数（按 tile 数 / 按 `w*h/750`），
     * 一个平坦常量在大截图上能差一个数量级，而上下文裁剪正是靠这个数决定丢谁。
     */
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,

    /**
     * 音频的采样率和声道数。
     *
     * 存下来是因为「这份字节能不能直接发给某个基座」是采样率/声道的函数
     * （OpenAI 只收 wav/mp3，各家对 PCM 参数还有各自要求），
     * 每次判定都重新 probe 一遍文件是白花 IO。
     */
    val sampleRate: Int? = null,
    val channels: Int? = null,

    /**
     * 派生来源的 [relPath]。`null` = 这份字节是用户直接给的；
     * 非 null = 它是从那个附件派生出来的（视频抽的帧、抽出来的音轨）。
     *
     * **为什么是 relPath 而不是 sourceId + 外键**：派生行和父行永远属于同一条消息、
     * 永远一起插一起删（`updateWithAttachments` 是「删光重插」），所以自引用外键买到的
     * 引用完整性这里本来就有；而外键要求「先插父拿到自增 id 再插子」，
     * 插入顺序就成了隐式契约。relPath 是 uuid 文件名、天然唯一，而且
     * [ChatLogRepository.rememberRemoteFileId] 早就在拿它当这份字节的身份了。
     *
     * 语义上的关键一条：**派生物才是模型看到的东西**。一个视频落三行——
     * 父行（原视频，只给 UI 回放）+ N 行抽帧（IMAGE）+ 1 行音轨（AUDIO），
     * 映射成 content block 时只取派生行。见 docs/media-pipeline.md。
     */
    val sourceRelPath: String? = null,

    /** 派生物在源里的位置，抽帧的时间戳。父行为 null。 */
    val posMs: Long? = null,

    /**
     * 用户**原本**给的 mime 和体积。归一化会改这两个值（HEIC → JPEG、重采样后体积变化），
     * 而 UI 上要能说清「12MB 的 HEIC 存成了 380KB 的 JPEG」，排查格式问题也要靠它。
     * `origByteSize` 为 0 表示未记录（v4 之前的历史行）。
     */
    val origMime: String? = null,
    val origByteSize: Long = 0,

    /**
     * 基座侧 Files API 的文件 id 缓存，省掉重复上传。
     *
     * 是**按 provider** 的（[remoteProvider]），且带 TTL（[remoteExpiresAt]，
     * epoch millis，0 表示不过期/未知）——同一个文件在不同基座上的 id 不通用。
     * 严格说这是 `(附件, provider) → id` 的多对多，先用三列扁平存着，
     * 真需要同时缓存多家再拆表。
     */
    val remoteFileId: String? = null,
    val remoteProvider: String? = null,
    val remoteExpiresAt: Long = 0,
) {
    /** 是不是派生物（视频抽的帧 / 抽出来的音轨）。见 [sourceRelPath]。 */
    val isDerived: Boolean get() = sourceRelPath != null

    /** 缓存的 remote id 在 [now] 这一刻对 [providerId] 是否还能用。 */
    fun remoteIdValidFor(providerId: String, now: Long): Boolean =
        remoteFileId != null &&
            remoteProvider == providerId &&
            (remoteExpiresAt == 0L || remoteExpiresAt > now)

    companion object {
        const val NO_ID = 0L
    }
}

enum class AttachmentKind {
    IMAGE,
    AUDIO,
    VIDEO,

    /** PDF 之类。 */
    DOC,
}
