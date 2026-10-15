package com.chatwaifu.mobile.ui.companion

enum class CompanionRuntimeState {
    IDLE,

    /**
     * 正在录音。**和 THINKING 分开**：Listening 期间没有任何请求在跑，
     * 用户随时可以取消，而 THINKING 是已经发出去的请求。
     */
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR,
}

/**
 * 语音输入的独立状态。不并进 [CompanionRuntimeState]：
 * 准备模型（[PREPARING]）时角色可能还在 Speaking，两者是正交的。
 */
enum class VoiceInputState {
    /** 没在用语音 */
    OFF,

    /** 正在装 ASR 模型 / 绑服务。此时再点麦克风应该被忽略而不是开第二个 session */
    PREPARING,

    /** 正在录音 */
    LISTENING,
}

enum class CompanionOverlayState {
    NONE,
    INPUT,
    HISTORY,

    /** 「更多」菜单：通往角色 / 记录 / 记忆 / 设置这些二级页面 */
    MENU,

    /**
     * 调整角色位置：只有这一态下 Live2D 才接收拖动 / 双指缩放，
     * 其余时候点到角色身上不会误拖（第五节第 6 条）
     */
    ADJUST,
}

/** 「更多」菜单能去的二级页面。它们都住在 `ChatActivity` 的 Fragment 里 */
enum class CompanionDestination {
    CHARACTERS,
    CHAT_LOG,
    MEMORY,
    SETTINGS,
}

sealed interface RendererUiState {
    data object Loading : RendererUiState
    data object Ready : RendererUiState
    data class Failed(val reason: String) : RendererUiState
}

enum class CompanionAuthor {
    USER,
    ASSISTANT,
}

data class CompanionHistoryItem(
    val id: Long,
    val author: CompanionAuthor,
    val text: String,
    val time: String,
    /** 这条消息带的附件（父附件，派生的抽帧 / 音轨不单独显示） */
    val attachments: List<PendingAttachment> = emptyList(),
)

/**
 * 用户能选的附件类型。和 `AttachmentKind` 一一对应，但单独一份：
 * 状态机不依赖 `Log` 模块，JVM 单测也不用拉 Room。
 */
enum class AttachmentType {
    IMAGE,

    /** 不转码，导入时抽帧 + 抽音轨，模型看的是派生物 */
    VIDEO,
    AUDIO,

    /** 只收 PDF */
    DOC,
}

/**
 * 一个已经导入落盘的附件：输入条上「待发送」的，或者历史里已发出的。
 *
 * [previewPath] 是给缩略图用的本地文件——图片是它自己，视频是第一帧，
 * 音频 / PDF 没有（UI 画类型标签）。
 */
data class PendingAttachment(
    val relPath: String,
    val type: AttachmentType,
    val previewPath: String? = null,
    /** 音频 / 视频的时长，标签上显示 */
    val durationMs: Long? = null,
)

/**
 * 当前模型能收哪些附件。按**选中的模型**算（`capabilitiesFor`），不是按 provider。
 * 视频能不能发取决于看图——发出去的是抽帧和音轨，见 [canAccept]
 */
data class AttachmentSupport(
    val image: Boolean = false,
    val audio: Boolean = false,
    val doc: Boolean = false,
) {
    val any: Boolean get() = image || audio || doc

    fun canAccept(type: AttachmentType): Boolean = when (type) {
        AttachmentType.IMAGE, AttachmentType.VIDEO -> image
        AttachmentType.AUDIO -> audio
        AttachmentType.DOC -> doc
    }

    companion object {
        val NONE = AttachmentSupport()
    }
}

/** 附件导入状态。和录音一样是正交的一维，不并进 runtime */
data class AttachmentUiState(
    /** 当前模型能收什么。不支持的类型入口给提示而不是打开选择器 */
    val support: AttachmentSupport = AttachmentSupport.NONE,
    val importing: Boolean = false,
    val pending: List<PendingAttachment> = emptyList(),
    /** 「+」展开的类型菜单 */
    val pickerMenuOpen: Boolean = false,
)

data class CompanionErrorState(
    val message: String,
    val canRetry: Boolean,
)

/**
 * 语音输入的 UI 状态。
 *
 * [partialText] 只用于「正在听」的即时反馈；它同时也被写进 draft，
 * 所以最终提交走的仍是普通输入那条路，不需要第二套提交链路。
 */
data class VoiceInputUiState(
    val state: VoiceInputState = VoiceInputState.OFF,
    val partialText: String = "",
    /** 权限被永久拒绝，需要引导去系统设置 */
    val permissionBlocked: Boolean = false,
) {
    val listening: Boolean get() = state == VoiceInputState.LISTENING
    val busy: Boolean get() = state != VoiceInputState.OFF
}

data class CompanionUiState(
    val characterId: String = "",
    val characterName: String = "Companion",
    val runtime: CompanionRuntimeState = CompanionRuntimeState.IDLE,
    val overlay: CompanionOverlayState = CompanionOverlayState.NONE,
    val renderer: RendererUiState = RendererUiState.Loading,
    val utterance: String = "",
    val draft: String = "",
    val requestInputFocus: Boolean = false,
    val history: List<CompanionHistoryItem> = emptyList(),
    val historyExpanded: Boolean = false,
    /** Room 里还有更早的记录没加载。历史面板滑到顶时用 [CompanionEvent.LoadOlderHistory] 取 */
    val hasOlderHistory: Boolean = false,
    val loadingOlderHistory: Boolean = false,
    val notice: String? = null,
    val error: CompanionErrorState? = null,
    val voice: VoiceInputUiState = VoiceInputUiState(),
    val attachments: AttachmentUiState = AttachmentUiState(),
)

sealed interface CompanionEvent {
    data object OpenInput : CompanionEvent
    data object DismissInput : CompanionEvent
    data class DraftChanged(val value: String) : CompanionEvent
    data object Submit : CompanionEvent
    data object OpenHistory : CompanionEvent
    data object DismissHistory : CompanionEvent
    data object ToggleHistoryHeight : CompanionEvent

    /** 历史面板滑到了最早一条：往前翻一页 */
    data object LoadOlderHistory : CompanionEvent
    data object RetryRenderer : CompanionEvent
    data object Interact : CompanionEvent
    /** 点「视觉」：直接开相册（图片 + 视频）。不能看图时提示 */
    data object Vision : CompanionEvent

    /** 输入条上的「+」：展开 / 收起附件类型菜单 */
    data object ToggleAttachMenu : CompanionEvent

    /** 菜单里选了一种类型：能收就让宿主打开对应的选择器，否则提示 */
    data class PickAttachment(val type: AttachmentType) : CompanionEvent

    /**
     * 宿主的选择器回来了（null = 用户取消）。传字符串而不是 `Uri`：
     * 状态机不碰 platform 类型，JVM 单测里 `android.net.Uri` 只是个 stub
     */
    data class AttachmentPicked(val uri: String?) : CompanionEvent

    data class RemovePendingAttachment(val relPath: String) : CompanionEvent
    /** 打开「更多」菜单 */
    data object More : CompanionEvent
    data object DismissMenu : CompanionEvent

    /** 去二级页面。由宿主 Activity 处理（要先释放 renderer 和 TTS），ViewModel 只收起菜单 */
    data class Navigate(val destination: CompanionDestination) : CompanionEvent

    data object StartAdjust : CompanionEvent
    data object FinishAdjust : CompanionEvent

    /** 角色位置回到默认值。由宿主处理（位置归 renderer 管），ViewModel 不感知 */
    data object ResetPose : CompanionEvent
    data object DismissNotice : CompanionEvent
    data object RetryResponse : CompanionEvent

    /**
     * 点麦克风。**同一个事件既开始也停止**（第十节：只支持一种交互，
     * 不同时做点击和长按两套手势）：不在录音时开始，在录音时正常停止。
     */
    data object ToggleVoiceInput : CompanionEvent

    /** 用户主动取消录音，**丢弃已识别内容** */
    data object CancelVoiceInput : CompanionEvent

    /** 权限申请结果由 UI 层回传 —— 权限不归 AsrEngine 管 */
    data class VoicePermissionResult(
        val granted: Boolean,
        /** false 且未授予 = 永久拒绝，要引导去系统设置 */
        val canAskAgain: Boolean,
    ) : CompanionEvent
}

sealed interface CompanionResponseEvent {
    /** 累计文本（不是增量）。可能在 [VoiceStarted] 之后继续到达：按句流水线下先开口、后写完 */
    data class Streaming(val text: String) : CompanionResponseEvent

    /** 第一段语音已经排进播放队列。运行态从 THINKING 进 SPEAKING，此时文本可能还没写完 */
    data object VoiceStarted : CompanionResponseEvent

    data class Reply(val text: String) : CompanionResponseEvent
    data object Finished : CompanionResponseEvent
    data class Failed(val reason: String) : CompanionResponseEvent
    data class TtsFailed(val reason: String) : CompanionResponseEvent
}
