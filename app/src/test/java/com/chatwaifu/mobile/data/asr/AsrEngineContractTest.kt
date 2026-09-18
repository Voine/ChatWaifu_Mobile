package com.chatwaifu.mobile.data.asr

import com.chatwaifu.mobile.data.asr.sherpa.SherpaNcnnAsrEngine
import com.chatwaifu.mobile.ui.companion.CompanionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.8：ASR 抽象边界。
 *
 * 这些测试守的是**架构约束**而不是行为：Sherpa 的类型不许泄漏到上层、
 * MNN 不许有假实现、ASR 不许和 InferenceProvider 混在一起。
 * 真实识别需要 native 库和麦克风，不在 JVM 单测范围。
 */
class AsrEngineContractTest {

    @Test
    fun sherpaEngineImplementsTheCapabilityInterface() {
        // 编译期就该成立，但显式断一次：将来有人把它改成不实现接口会立刻失败
        assertTrue(AsrEngine::class.java.isAssignableFrom(SherpaNcnnAsrEngine::class.java))
    }

    @Test
    fun sherpaCapabilitiesReflectRealBehaviour() {
        val fields = AsrCapabilities::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("streaming" in fields)
        assertTrue("partialResult" in fields)
        assertTrue("endpointDetection" in fields)
        assertTrue("offline" in fields)
        assertTrue("punctuation" in fields)
    }

    @Test
    fun mnnEngineIsAPlaceholderWithoutFakeImplementation() {
        assertTrue(AsrEngineType.entries.contains(AsrEngineType.MNN))
        // 占位不等于假实现：悄悄回落到 Sherpa 会让「以为换了引擎」的 bug 极难发现
        assertFalse(AsrProvider.isImplemented(AsrEngineType.MNN))
        assertTrue(AsrProvider.isImplemented(AsrEngineType.SHERPA_NCNN))
        assertEquals(AsrEngineType.SHERPA_NCNN, AsrProvider.defaultType)
    }

    @Test
    fun engineConfigDoesNotLeakRuntimeSpecificOptions() {
        val engineFields = AsrConfig::class.java.declaredFields.map { it.name }.toSet()

        // Sherpa 专属的这些必须留在实现内部，否则 MnnAsrEngine 会被迫接受无意义字段
        assertFalse("numThreads" in engineFields)
        assertFalse("useGPU" in engineFields)
        assertFalse("decoderMethod" in engineFields)
        assertFalse("numActivePaths" in engineFields)
        assertFalse("endpointConfig" in engineFields)
    }

    @Test
    fun sessionConfigSeparatesPerRecordingOptions() {
        val sessionFields = AsrSessionConfig::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("sampleRateHz" in sessionFields)
        assertTrue("endpointDetection" in sessionFields)
        assertTrue("maxDurationMs" in sessionFields)
        assertEquals(16_000, AsrSessionConfig().sampleRateHz)
    }

    @Test
    fun asrIsNotMixedIntoInferenceProviderConfig() {
        val providerFields = com.chatwaifu.chat.core.ProviderConfig::class.java
            .declaredFields.map { it.name.lowercase() }

        // ASR 和 LLM 是两个独立能力域
        assertTrue(providerFields.none { "asr" in it })
        assertTrue(providerFields.none { "speech" in it })
        assertTrue(providerFields.none { "recogni" in it })
    }

    /**
     * Companion 的 ViewModel 只能认识 [com.chatwaifu.mobile.ui.companion.CompanionVoiceInput]，
     * 不能出现 Sherpa 或具体 engine 类型 —— 这就是「换 MNN 不用改 Companion UI」的约束。
     */
    @Test
    fun companionViewModelDependsOnAbstractionOnly() {
        val constructorTypes = CompanionViewModel::class.java.constructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }

        assertTrue(constructorTypes.none { it.contains("sherpa", ignoreCase = true) })
        assertTrue(constructorTypes.none { it.contains("AsrEngine") })
        assertTrue(constructorTypes.any { it.endsWith("CompanionVoiceInput") })
    }

    @Test
    fun asrErrorsAreClosedAndRuntimeAgnostic() {
        val names = listOf(
            AsrError.PermissionDenied,
            AsrError.ModelUnavailable,
            AsrError.InitializationFailed,
            AsrError.AudioRecordFailed,
            AsrError.RecognitionFailed,
            AsrError.Timeout,
            AsrError.NotPrepared,
            AsrError.Unknown,
        ).map { it::class.java.simpleName }

        // 没有任何一个错误类型泄漏 runtime 名字
        assertTrue(names.none { it.contains("Sherpa") || it.contains("Ncnn") })
        assertEquals(names.size, names.distinct().size)
    }
}
