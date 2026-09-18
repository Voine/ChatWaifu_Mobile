package com.chatwaifu.mobile.data.asr

import android.content.Context
import com.chatwaifu.mobile.data.asr.mnn.MnnAsrEngine

/**
 * Description: [AsrEngine] 的唯一入口。工程里没有 DI 框架，照 `ModelProvider` 的形态收成单例。
 *
 * 换 runtime 只改这里一个文件：`AsrEngineType.MNN` 那一支填上真实实现即可，
 * Companion UI / ViewModel / 状态机 / 权限流程都不用动。
 * **禁止**在 UI 或 ViewModel 里出现 `if (engine == SHERPA)` 这种分支。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
object AsrProvider : AsrEngineFactory {

    @Volatile
    private var engineRef: AsrEngine? = null

    /**
     * 当前默认 runtime。
     *
     * **2026/10 从 Sherpa-ncnn 换成 MNN**：工程的 TTS 早就是 MNN
     * （Bert-VITS2-MNN），ASR 是最后一处 ncnn 依赖，换掉之后整个仓库
     * 不再需要 ncnn 运行时。
     *
     * 换引擎**只改了这个文件** —— Companion UI / 语音输入状态机 / 权限流程 /
     * 录音生命周期 / ChatSession 接线一行没动，这正是 Phase 2.8 那层抽象的目的。
     */
    val defaultType: AsrEngineType = AsrEngineType.MNN

    fun engine(context: Context): AsrEngine =
        engineRef ?: synchronized(this) {
            engineRef ?: create(context, defaultType).also { engineRef = it }
        }

    override fun create(type: AsrEngineType): AsrEngine =
        error("use create(context, type); AsrEngine needs a Context to bind its service")

    /**
     * 这个 runtime 有没有实现。**先查它再碰 Context** ——
     * 将来再出现占位类型时，它不该因为「构造参数求值」这种顺序问题才暴露出来。
     */
    fun isImplemented(type: AsrEngineType): Boolean = when (type) {
        AsrEngineType.MNN -> true
    }

    fun create(context: Context, type: AsrEngineType): AsrEngine {
        // 占位类型要明确失败，不能悄悄回落到另一个引擎 ——
        // 那样「以为换了引擎」的 bug 极难发现
        if (!isImplemented(type)) {
            throw NotImplementedError(
                "$type ASR is not implemented yet; see the AsrEngine contract"
            )
        }
        return when (type) {
            AsrEngineType.MNN -> MnnAsrEngine(context.applicationContext)
        }
    }
}
