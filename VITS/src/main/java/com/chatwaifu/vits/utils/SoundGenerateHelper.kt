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
     * 合成并播放。**按句切分逐句推理**，和老实现一样：BV2 单句 RTF≈0.36，
     * 整段长回复一次性推完会让首字延迟很难看，而主循环已经串了
     * LLM → 翻译 → TTS 三段等待。
     *
     * @param forwardResult 每句的 PCM + 采样率，转给口型驱动。采样率必须一起给 ——
     *   老实现里口型时长按写死的 22050 估算，44100 的模型会算出两倍长的动画。
     * @return 有任何一句成功出声就算成功
     */
    suspend fun generateAndPlay(
        text: String?,
        forwardResult: (FloatArray, Int) -> Unit,
    ): Boolean {
        if (!modelReady) {
            Log.e(TAG, "model not ready, drop text")
            return false
        }
        val sentences = splitSentences(text.orEmpty())
        if (sentences.isEmpty()) return false

        var anySucceeded = false
        for (sentence in sentences) {
            val pcm = try {
                inferOne(sentence)
            } catch (e: Throwable) {
                // 单句失败不该让整段回复哑掉，继续下一句
                Log.e(TAG, "infer failed for \"$sentence\"", e)
                null
            }
            if (pcm == null || pcm.isEmpty()) continue
            soundHandler.sendSound(pcm)
            forwardResult(pcm, sampleRate)
            anySucceeded = true
        }
        return anySucceeded
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

    /**
     * 按句末标点切分，中日英通吃。
     *
     * 超过 [MAX_SENTENCE_CHARS] 的整块会被硬切 —— 没有标点的长串（模型偶尔会吐）
     * 单次推理时间是线性增长的，不切会卡死几十秒。
     */
    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()

        fun flush() {
            val chunk = buffer.toString().trim()
            if (chunk.isNotEmpty()) result += chunk
            buffer.clear()
        }

        for (ch in text) {
            buffer.append(ch)
            if (ch in SENTENCE_ENDINGS || buffer.length >= MAX_SENTENCE_CHARS) flush()
        }
        flush()
        return result
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

        private const val MAX_SENTENCE_CHARS = 60
        private val SENTENCE_ENDINGS = charArrayOf(
            '。', '！', '？', '；', '\n',
            '.', '!', '?', ';',
        )

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
