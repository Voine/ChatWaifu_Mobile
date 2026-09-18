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
    /**
     * 非 null = 音频来自这个 16k/mono/16bit WAV 而不是麦克风。
     *
     * **只给验证用**，由 debug 构建的调试入口设置；正式链路永远是 null。
     * 存在的理由：模拟器麦克风只有静音，partial transcript 这条路
     * 没有别的办法在设备上跑通。换 ASR runtime 时它还是回归工具 ——
     * 同一段音频喂两个引擎可以直接比对识别结果。
     *
     * 实现方不支持时应当忽略它并照常用麦克风，而不是报错。
     */
    val debugWavPath: String? = null,
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 16_000
        const val DEFAULT_MAX_DURATION_MS = 60_000L
    }
}

/**
 * 可用的 ASR runtime。
 *
 * 2026/10 起只剩 MNN：原来的 `SHERPA_NCNN` 已随 Sherpa 模块一起移除，
 * **枚举值也一并删掉而不是留成死分支** —— 它从没被持久化过，
 * 留着只会让 [AsrProvider.isImplemented] 多一个永远为假的分支。
 * 将来加新 runtime（Whisper / 云端）时在这里追加。
 */
enum class AsrEngineType {
    MNN,
}

interface AsrEngineFactory {
    fun create(type: AsrEngineType = AsrEngineType.MNN): AsrEngine
}
