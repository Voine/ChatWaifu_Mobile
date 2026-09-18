package com.chatwaifu.mobile.data.asr

import kotlinx.coroutines.flow.Flow

/**
 * Description: 语音转文本的能力接口。上层（Companion UI / ViewModel）只认这一层，
 * 不得 import 任何 `com.k2fsa.sherpa.ncnn.*` 类型。
 *
 * **为什么接口在 app 侧而不是 Sherpa 模块里**：AIDL 是**进程**边界（ASR 跑在 `:sherpa`，
 * 因为多个 ncnn 库像是进程独享资源），不是**能力**边界。把能力接口放进 Sherpa 模块
 * 等于让「换 runtime」这件事依赖那个模块继续存在。所以接口留在 app 的 data 层，
 * [com.chatwaifu.mobile.data.asr.sherpa.SherpaNcnnAsrEngine] 才是那个知道 AIDL 的实现。
 *
 * 未来换 MNN ASR 只需要再写一个 `MnnAsrEngine : AsrEngine`，
 * Companion UI / 状态机 / 权限流程 / 录音生命周期 / ChatSession 接线都不用改。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
interface AsrEngine {

    /** 这个 runtime 实际支持什么。UI 按它决定要不要显示 partial 文本等。 */
    val capabilities: AsrCapabilities

    /**
     * 装载模型。**必须可挂起且可失败** —— 老实现是在 Binder 线程的 `init{}` 里同步构造
     * 108MB 的 ncnn 模型，既阻塞又没法把失败告诉上层。
     *
     * 幂等：已经就绪时直接返回成功。
     */
    suspend fun prepare(config: AsrConfig): Result<Unit>

    /**
     * 开一次识别会话。**同一时刻只允许一个活跃 session**：实现必须让前一个失效，
     * 迟到的 partial/final 不能污染新 session（见 [AsrSession] 的 KDoc）。
     */
    fun startSession(config: AsrSessionConfig): AsrSession

    suspend fun release()
}

/**
 * 一次录音识别。生命周期短，用完即弃 —— 不要复用一个 session 做第二次录音。
 *
 * 实现方要保证：这个 session 被 [cancel] 或被新 session 顶掉之后，
 * [events] 不再发出任何 [AsrEvent.PartialResult] / [AsrEvent.FinalResult]。
 */
interface AsrSession {

    /** 冷流。collect 它才会真正开始录音。 */
    val events: Flow<AsrEvent>

    /** 正常结束：停止录音，把已识别的内容作为 [AsrEvent.FinalResult] 发出。 */
    suspend fun stop()

    /** 用户取消：停止录音，**不发** FinalResult，只发 [AsrEvent.Ended]。 */
    suspend fun cancel()
}
