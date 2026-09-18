package com.chatwaifu.mobile.data.asr

/**
 * Description: ASR 的能力描述与两级配置。
 *
 * Author: Voine
 * Date: 2026/9/17
 */

/**
 * 这个 runtime 实际能做什么。**按真实能力填，不要许愿** ——
 * UI 会据此决定要不要显示中间文本。
 */
data class AsrCapabilities(
    /** 边录边解码（而不是录完再一次性识别） */
    val streaming: Boolean,
    /** 能给出可用的中间结果 */
    val partialResult: Boolean,
    /** 能自己判断「一句说完了」 */
    val endpointDetection: Boolean,
    /** 完全本机推理，不需要网络 */
    val offline: Boolean,
    /** 输出带标点 */
    val punctuation: Boolean,
)

/**
 * **引擎级**配置：装载一次模型就固定下来的东西。
 *
 * 刻意只有语言这一项通用参数：线程数、是否用 GPU、beam search 宽度、endpoint 三条规则
 * 这些都是 Sherpa 专属的，放进来会让 `MnnAsrEngine` 被迫接受一堆无意义字段。
 * 它们留在 `SherpaAsrOptions` 里，只有 Sherpa 实现自己认识。
 */
data class AsrConfig(
    /** BCP-47 或 null（跟随模型自带能力）。当前 Sherpa 模型是中英双语，传 null 即可。 */
    val language: String? = null,
)

/**
 * **单次录音**配置。每次 [AsrEngine.startSession] 可以不同。
 */
data class AsrSessionConfig(
    /** 采样率。当前实现固定 16k，传别的值会被拒（模型 fbank 就是按 16k 算的）。 */
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE,
    /** 是否让 runtime 报 [AsrEvent.EndpointReached] */
    val endpointDetection: Boolean = true,
    /** 录音硬上限，到点报 [AsrError.Timeout]。防止用户忘了停。 */
    val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 16_000
        const val DEFAULT_MAX_DURATION_MS = 60_000L
    }
}

/** 当前只有一种 runtime。MNN 是**占位**，本阶段不提供实现。 */
enum class AsrEngineType {
    SHERPA_NCNN,

    /**
     * 未来的 MNN ASR。**没有实现** —— [AsrEngineFactory.create] 传它会抛，
     * 而不是悄悄回落到 Sherpa（那样会让「以为换了引擎」的 bug 极难发现）。
     */
    MNN,
}

interface AsrEngineFactory {
    fun create(type: AsrEngineType = AsrEngineType.SHERPA_NCNN): AsrEngine
}
