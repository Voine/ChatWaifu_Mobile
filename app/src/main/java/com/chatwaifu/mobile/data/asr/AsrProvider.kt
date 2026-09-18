package com.chatwaifu.mobile.data.asr

import android.content.Context
import com.chatwaifu.mobile.data.asr.sherpa.SherpaNcnnAsrEngine

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

    /** 当前默认 runtime。将来做 ASR 设置页时才需要把它变成可配置项。 */
    val defaultType: AsrEngineType = AsrEngineType.SHERPA_NCNN

    fun engine(context: Context): AsrEngine =
        engineRef ?: synchronized(this) {
            engineRef ?: create(context, defaultType).also { engineRef = it }
        }

    override fun create(type: AsrEngineType): AsrEngine =
        error("use create(context, type); AsrEngine needs a Context to bind its service")

    /**
     * 这个 runtime 有没有实现。**先查它再碰 Context** ——
     * 占位类型不该因为「构造参数求值」这种顺序问题才暴露出来。
     */
    fun isImplemented(type: AsrEngineType): Boolean = when (type) {
        AsrEngineType.SHERPA_NCNN -> true
        AsrEngineType.MNN -> false
    }

    fun create(context: Context, type: AsrEngineType): AsrEngine {
        // 占位而不是假实现：悄悄回落到 Sherpa 会让「以为换了引擎」的 bug 极难发现
        if (!isImplemented(type)) {
            throw NotImplementedError(
                "$type ASR is not implemented yet; see the AsrEngine contract"
            )
        }
        return when (type) {
            AsrEngineType.SHERPA_NCNN -> SherpaNcnnAsrEngine(context.applicationContext)
            AsrEngineType.MNN -> error("unreachable: guarded by isImplemented")
        }
    }
}
