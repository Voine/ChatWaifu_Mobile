package com.chatwaifu.mobile.data.asr

/**
 * Description: ASR 的稳定事件语义。上层只消费这些，
 * 不允许去读 recognizer / decoder 的内部状态。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
sealed interface AsrEvent {

    /** 录音已真正开始（AudioRecord 起来了），UI 此时才该进 Listening。 */
    data object ListeningStarted : AsrEvent

    /**
     * 中间结果。**同一次录音里会反复发**，每次都是「到目前为止的完整文本」而不是增量，
     * 所以 UI 直接覆盖输入框即可，不要拼接。
     */
    data class PartialResult(val text: String) : AsrEvent

    /** 最终结果。一次 session 最多一条；[cancel] 掉的 session 不会有。 */
    data class FinalResult(val text: String) : AsrEvent

    /**
     * runtime 检测到一句话说完了（静音超时等）。
     * [AsrCapabilities.endpointDetection] 为 false 的实现永远不发。
     *
     * 只是**信号**，不代表 session 结束 —— 要不要因此停止录音是上层的策略。
     */
    data object EndpointReached : AsrEvent

    data class Error(val error: AsrError) : AsrEvent

    /** 流的终点。正常结束、取消、出错都会以它收尾，UI 靠它归位到非 Listening 态。 */
    data object Ended : AsrEvent
}

/**
 * 稳定错误模型。**UI 不应看到任何 runtime 的原始异常类型**；
 * 原始异常只进日志，且不带模型路径。
 */
sealed interface AsrError {
    /** 没有 RECORD_AUDIO。权限申请不归 engine 管（见 AsrEngine 的分层），engine 只报告。 */
    data object PermissionDenied : AsrError

    /** 模型文件缺失 / 读不出来 */
    data object ModelUnavailable : AsrError

    /** 模型装载失败（构造 recognizer 抛了） */
    data object InitializationFailed : AsrError

    /** AudioRecord 起不来（被别的应用占着、设备无麦克风等） */
    data object AudioRecordFailed : AsrError

    /** 识别过程中挂了 */
    data object RecognitionFailed : AsrError

    /** 超过 [AsrSessionConfig.maxDurationMs] */
    data object Timeout : AsrError

    /** 引擎还没 prepare 就开始录音 */
    data object NotPrepared : AsrError

    data object Unknown : AsrError
}
