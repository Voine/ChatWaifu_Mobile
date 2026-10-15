package com.chatwaifu.vits.utils

import android.content.Context
import android.util.Log
import com.chatwaifu.vits.SoundPlayHandler
import com.chatwaifu.vits.utils.file.ConfigParseResult
import com.chatwaifu.vits.utils.file.FileUtils
import com.example.bertvits2_infer_wrapper.impl.BertVITS2FullInferImpl
import com.example.bertvits2_infer_wrapper.interfaces.IBertVITS2FullInfer
import com.example.textpreprocess.preprocess.LANGUAGE_EN
import com.example.textpreprocess.preprocess.LANGUAGE_JP
import com.example.textpreprocess.preprocess.LANGUAGE_MIX_ZH_EN
import com.example.textpreprocess.preprocess.LANGUAGE_ZH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Description: 端内 TTS。Bert-VITS2 + MNN 推理（`Bert-VITS2-MNN` submodule 打出的 AAR）。
 *
 * 取代了 2023 年那套 VITS-ncnn：老实现是「自己写的 Kotlin 音素化
 * （`ChineseTextUtils` / `JapaneseTextUtils`）+ `vitsncnn_jni.cpp` 里的 ncnn 前向」，
 * 已经连 cpp 和 302MB 的 `.ncnn.bin` 权重一起删掉了。
 *
 * 为什么走 [IBertVITS2FullInfer] 而不是现成的 `BertVITS2SimpleInferImpl`：
 * 后者把模型路径硬编码在 `filesDir/bv2_model/<lang>/`，并且启动时要遍历全部四个语种的
 * `config.json`。这个工程只拉了日文链路的 LFS 权重（zh / en / mix 还是 LFS 指针文件），
 * SimpleInfer 那条路会在解析指针文件时直接挂掉。FullInfer 的
 * `setBertVITS2ModelPath` 收绝对路径，正好接得上 ChatWaifu 自己的
 * `models/` 布局（角色模型可以由用户 SAF 导入，路径不由我们决定）。
 *
 * Author: Voine
 * Date: 2023/2/19（BV2 重写于 2026/9/10）
 */
class SoundGenerateHelper(val context: Context) {

    private val infer: IBertVITS2FullInfer by lazy { BertVITS2FullInferImpl(context) }

    private val soundHandler: SoundPlayHandler by lazy { SoundPlayHandler() }

    private var loaderInited = false
    private var modelReady = false

    private var speakerId = 0
    private var language = LANGUAGE_JP
    @Volatile
    private var activePlaybackId = NO_PLAYBACK

    /** 从 config.json 读出来，喂给 AudioTrack 和口型时长估算。日文底模是 44100。 */
    var sampleRate = DEFAULT_SAMPLE_RATE
        private set

    /**
     * 装载一个 BV2 模型。取代了老实现的 `loadConfigs()` + `loadModel()` 两步——
     * 那两步是分开的历史包袱（config 要先解出 symbols 才能建 textUtils），
     * BV2 这边 config 只提供采样率，没有先后依赖。
     *
     * **声学模型和 BERT 分两个参数传**，因为两者的生命周期不同：声学模型是
     * 「一个角色（组）的声音」，可以由用户 SAF 导入；BERT 是「一个语种的编码器」，
     * 随包走、所有同语种角色共用。合成一个根目录会逼着导入模型也自带一份 40MB 的 BERT。
     *
     * @param bv2Dir 6 个 `.mnn` + config.json 所在目录
     * @param bertDir BERT `.mnn` 所在目录；[LANGUAGE_MIX_ZH_EN] 不吃 BERT，可以传 null
     * @param language [LANGUAGE_ZH] / [LANGUAGE_EN] / [LANGUAGE_JP] / [LANGUAGE_MIX_ZH_EN]
     * @param targetSpeakerId config.json 的 `spk2id` 里的值
     */
    suspend fun init(
        bv2Dir: String,
        bertDir: String?,
        language: Int,
        targetSpeakerId: Int,
    ): Boolean {
        val modelDir = File(bv2Dir)
        val config = when (val parsed = FileUtils.parseConfig(File(modelDir, CONFIG_JSON).absolutePath)) {
            is ConfigParseResult.Success -> parsed.config
            is ConfigParseResult.Failure -> {
                Log.e(TAG, "load config failed: ${parsed.reason}")
                return false
            }
        }

        val modules = findModules(modelDir) ?: run {
            Log.e(TAG, "incomplete bv2 model in $bv2Dir")
            return false
        }
        // MIX 语种不吃 BERT（BV2 那边直接传空串），其余三个必须有
        val bertModel = bertDir
            ?.let { File(it).listFiles()?.firstOrNull { f -> f.name.endsWith(MNN_SUFFIX) } }
        if (bertModel == null && language != LANGUAGE_MIX_ZH_EN) {
            Log.e(TAG, "bert model missing in $bertDir")
            return false
        }

        this.language = language
        this.speakerId = targetSpeakerId
        this.sampleRate = config.data?.sampling_rate ?: DEFAULT_SAMPLE_RATE
        soundHandler.setTrackData(sampleRate, 1)

        // loader 是进程级的 MNN 上下文，只初始化一次；切角色只需要重设路径
        if (!loaderInited) {
            infer.initBertVITS2Loader()
            loaderInited = true
        }

        // 日文链路的预处理只用 openjtalk 词典（直接从 APK assets 读）和
        // filesDir/bert/jp/vocab.txt，不碰 zh 的 jieba 词典 —— BV2 那边四个语种全是
        // by lazy，所以那 17MB 的 assets 拷贝对日文是纯浪费，只在真用到时才做
        if (language != LANGUAGE_JP && !infer.initPreprocessor()) {
            Log.e(TAG, "init preprocessor failed")
            return false
        }

        infer.setBertVITS2ModelPath(
            enc_model_path = modules.enc,
            dec_model_path = modules.dec,
            sdp_model_path = modules.sdp,
            dp_model_path = modules.dp,
            emb_model_path = modules.emb,
            flow_model_path = modules.flow,
            bert_model_path = bertModel?.absolutePath.orEmpty(),
        )
        modelReady = true
        Log.i(TAG, "bv2 model ready: $bv2Dir, speaker=$targetSpeakerId, sr=$sampleRate")
        return true
    }

    /**
     * 合成并播放一整段文本（语音试听用）。按句切分逐句推理，第一句推完就开始播。
     *
     * 对话链路不走这里，走 [openStream]：它不等完整文本，LLM 每凑满一句就推一句。
     *
     * @param forwardResult 每句的 PCM + 采样率，转给口型驱动。采样率必须一起给 ——
     *   老实现里口型时长按写死的 22050 估算，44100 的模型会算出两倍长的动画。
     * @return 有音频生成且最后一帧已由 AudioTrack 播放完成时返回 true
     */
    suspend fun generateAndPlay(
        text: String?,
        forwardResult: (FloatArray, Int) -> Unit,
    ): Boolean {
        val sentences = SentenceSplitter.split(text.orEmpty())
        if (sentences.isEmpty()) return false
        val stream = openStream(forwardResult) ?: return false
        try {
            sentences.forEach { stream.speak(it) }
            return stream.finish()
        } finally {
            stream.cancel()
        }
    }

    /**
     * 开一个**流式**播报轮次：句子陆续 [SpeechStream.speak] 进来，全部 PCM 排进同一个
     * AudioTrack 轮次，最后 [SpeechStream.finish] 等真实播放完成。
     *
     * 和一次性 [generateAndPlay] 的区别只在「句子是陆续来的」：
     * - 播放完成仍然按 marker 判（[SoundPlayHandler.awaitPlaybackComplete]），marker
     *   在 finish 时才按**累计**写入帧数设置，所以陆续入队不影响完成语义
     * - 后一句推得比前一句播得慢时，AudioTrack 会短暂欠载（静音一下）然后接着播，
     *   这是推理速度决定的，不是 bug
     *
     * 同一时刻只能有一个活跃轮次：新开的轮次会让旧轮次的 PCM 被丢弃。
     *
     * @return null 表示模型没就绪
     */
    fun openStream(forwardResult: (FloatArray, Int) -> Unit): SpeechStream? {
        if (!modelReady) {
            Log.e(TAG, "model not ready, cannot open speech stream")
            return null
        }
        val playbackId = soundHandler.beginPlayback().also { activePlaybackId = it }
        return SpeechStream(playbackId, forwardResult)
    }

    inner class SpeechStream internal constructor(
        private val playbackId: Long,
        private val forwardResult: (FloatArray, Int) -> Unit,
    ) {
        @Volatile
        private var closed = false

        /** 是否已经有至少一句成功入队 */
        var anyQueued = false
            private set

        /**
         * 推理一句并排进播放队列。**阻塞在 native 推理上**，调用方放在 IO 线程。
         * 单句失败只记日志、跳过，不让整段回复哑掉。
         *
         * native 推理本身不可中断：协程被取消后要等这一句推完才会在下面的
         * ensureActive 抛出。调用方因此必须在**不可取消**的上下文里 join 推理协程，
         * 否则下一轮可能和这一句的推理同时碰 BV2 的进程级单例。
         */
        suspend fun speak(sentence: String) {
            if (closed || sentence.isBlank()) return
            currentCoroutineContext().ensureActive()
            val pcm = try {
                inferOne(sentence)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "infer failed for \"$sentence\"", e)
                null
            }
            currentCoroutineContext().ensureActive()
            if (closed || pcm == null || pcm.isEmpty()) return
            soundHandler.sendSound(playbackId, pcm)
            forwardResult(pcm, sampleRate)
            anyQueued = true
        }

        /**
         * 不会再有新句子了：等已入队的 PCM 真正播完。
         * @return 有音频且播放完成 true；一句都没推出来 / 被取消 / 播放失败 false
         */
        suspend fun finish(): Boolean {
            if (closed) return false
            if (!anyQueued) {
                cancel()
                return false
            }
            return try {
                soundHandler.awaitPlaybackComplete(playbackId)
            } finally {
                closed = true
                if (activePlaybackId == playbackId) activePlaybackId = NO_PLAYBACK
            }
        }

        /** 立刻停播并丢弃队列。重复调用无害，finish 之后调用也无害。 */
        fun cancel() {
            if (closed) return
            closed = true
            soundHandler.cancelPlayback(playbackId)
            if (activePlaybackId == playbackId) activePlaybackId = NO_PLAYBACK
        }
    }

    /** 立即停止当前 AudioTrack 轮次；不会销毁或中断仍在执行的 native 推理。 */
    fun cancelPlayback() {
        activePlaybackId.takeIf { it != NO_PLAYBACK }?.let(soundHandler::cancelPlayback)
    }

    private suspend fun inferOne(sentence: String): FloatArray? {
        val preprocessed = infer.preprocess(sentence, language) ?: run {
            Log.e(TAG, "preprocess returned null for \"$sentence\"")
            return null
        }
        preprocessed.errorMsg?.takeIf { it.isNotEmpty() }?.let {
            Log.e(TAG, "preprocess error: $it")
            return null
        }
        return infer.startAudioInfer(preprocessed, speakerId)
    }

    fun clear() {
        soundHandler.release()
        if (loaderInited) {
            infer.destroyBertVITS2Loader()
            loaderInited = false
        }
        modelReady = false
        Log.i(TAG, "cleared!")
    }

    /** 6 个 BV2 子模块按文件名后缀认领，和 BV2 自己的导出脚本命名保持一致 */
    private fun findModules(dir: File): Bv2Modules? {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(MNN_SUFFIX) } ?: return null
        fun pick(suffix: String) = files.firstOrNull { it.name.endsWith(suffix) }?.absolutePath
        return Bv2Modules(
            enc = pick("_enc$MNN_SUFFIX") ?: return null,
            dec = pick("_dec$MNN_SUFFIX") ?: return null,
            sdp = pick("_sdp$MNN_SUFFIX") ?: return null,
            dp = pick("_dp$MNN_SUFFIX") ?: return null,
            emb = pick("_emb$MNN_SUFFIX") ?: return null,
            flow = pick("_flow$MNN_SUFFIX") ?: return null,
        )
    }

    private data class Bv2Modules(
        val enc: String,
        val dec: String,
        val sdp: String,
        val dp: String,
        val emb: String,
        val flow: String,
    )

    companion object {
        private const val TAG = "SoundGenerateHelper"

        const val BV2_MODEL_DIR = "bv2_model"
        const val BERT_DIR = "bert"
        const val CONFIG_JSON = "config.json"
        private const val MNN_SUFFIX = ".mnn"

        /** config.json 读不出采样率时的兜底。日文底模实际是 44100。 */
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val NO_PLAYBACK = 0L

        /** BV2 的 assets 目录名约定，[languageDirName] 是它和 `LANGUAGE_*` 的唯一映射处 */
        fun languageDirName(language: Int): String? = when (language) {
            LANGUAGE_ZH -> "zh"
            LANGUAGE_EN -> "en"
            LANGUAGE_JP -> "jp"
            LANGUAGE_MIX_ZH_EN -> "mix"
            else -> null
        }
    }
}
