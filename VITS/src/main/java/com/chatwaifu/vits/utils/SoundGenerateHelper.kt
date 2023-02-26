package com.chatwaifu.vits.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.chatwaifu.vits.SoundPlayHandler
import com.chatwaifu.vits.Vits
import com.chatwaifu.vits.data.Config
import com.chatwaifu.vits.utils.file.FileUtils
import com.chatwaifu.vits.utils.text.ChineseTextUtils
import com.chatwaifu.vits.utils.text.JapaneseTextUtils
import com.chatwaifu.vits.utils.text.TextUtils

/**
 * Description: SoundGenerateHelper
 * Refactor: Voine
 * Date: 2023/2/19
 */
class SoundGenerateHelper(val context: Context) {
    companion object {
        private const val TAG = "SoundGenerateHelper"
    }

    private var textUtils: TextUtils? = null
    private var config: Config? = null
    private var samplingRate = 22050
    private var n_vocab: Int = 0
    private var maxSpeaker = 1
    private var multi = true
    private var noiseScale: Float = .9f
    private var noiseScaleW: Float = .9f
    private var lengthScale: Float = 1f
    private var sid = 0
    private var modelInitState: Boolean = false
    private var voiceConvert = false
    private var targetFolder: String = ""
    private val currentThreadCount: Int by lazy {
        VitsUtils.checkThreadsCpp()
    }
    private val soundHandler: SoundPlayHandler by lazy {
        SoundPlayHandler()
    }

    // load config file
    fun loadConfigs(path: String?, callback: (isSuccess: Boolean) -> Unit) {
        path ?: return callback.invoke(false)
        config = FileUtils.parseConfig(context, path)
        var type = "single"
        if (config != null && config!!.speakers != null) {
            type = "multi"
        }
        if (VitsUtils.checkConfig(config, type)) {
            samplingRate = config!!.data!!.sampling_rate!!
            n_vocab = config!!.symbols!!.size
            if (config!!.data!!.n_speakers!! > 1) {
                multi = true
                maxSpeaker = config!!.data!!.n_speakers!!
            } else {
                multi = false
            }
            val cleanerName = config!!.data!!.text_cleaners!![0]
            val symbols = config!!.symbols!!

            // init textUtils
            when {
                cleanerName.contains("chinese") -> {
                    textUtils = ChineseTextUtils(symbols, cleanerName, context.assets)
                }
                cleanerName.contains("japanese") -> {
                    textUtils = JapaneseTextUtils(symbols, cleanerName, context.assets)
                }
            }

            if (textUtils == null) {
                throw RuntimeException("暂不支持${cleanerName}")
            }
            soundHandler.setTrackData(config!!.data!!.sampling_rate!!, 1)
            callback.invoke(true)
        } else {
            callback.invoke(false)
        }
    }

    // load model files
    fun loadModel(path: String?, callback: (isSuccess: Boolean) -> Unit) {
        path ?: return callback.invoke(false)
        var folder = ""
        when {
            path.endsWith("dec.ncnn.bin") ->
                folder = path.replace("dec.ncnn.bin", "")
            path.endsWith("dp.ncnn.bin") ->
                folder = path.replace("dp.ncnn.bin", "")
            path.endsWith("flow.ncnn.bin") ->
                folder = path.replace("flow.ncnn.bin", "")
            path.endsWith("flow.reverse.ncnn.bin") ->
                folder = path.replace("flow.reverse.ncnn.bin", "")
            path.endsWith("emb_g.bin") ->
                folder = path.replace("emb_g.bin", "")
            path.endsWith("emb_t.bin") ->
                folder = path.replace("emb_t.bin", "")
            path.endsWith("enc_p.ncnn.bin") ->
                folder = path.replace("enc_p.ncnn.bin", "")
            path.endsWith("enc_q.ncnn.bin") ->
                folder = path.replace("enc_q.ncnn.bin", "")
        }
        if (folder == "") {
            Log.e(TAG, "folder is null $path")
            return callback.invoke(false)
        }
        if (targetFolder == "") targetFolder = folder

        modelInitState = Vits.init_vits(
            context.assets,
            folder,
            voiceConvert,
            multi,
            n_vocab
        )
        Log.d(TAG, "model init status $modelInitState")

        callback.invoke(modelInitState)
    }

    // processing inputs
    @SuppressLint("SetTextI18n")
    fun generateAndPlay(text: String?, callback: (isSuccess: Boolean) -> Unit) {
        text ?: return callback.invoke(false)
        try {
            // convert inputs
            val inputs = textUtils?.convertText(text)
            if (inputs != null && inputs.isNotEmpty()) {

                // inference for each sentence
                for (i in inputs.indices) {
                    // start inference
                    Vits.forward(
                        inputs[i],
                        vulkan = false,
                        multi,
                        sid,
                        noiseScale,
                        noiseScaleW,
                        lengthScale,
                        currentThreadCount
                    )?.let {
                        soundHandler.sendSound(it)
                    }
                }
                return callback.invoke(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TTSFragment", e.message.toString())
            callback.invoke(false)
        }
    }

    fun clear() {
        soundHandler.release()
        modelInitState = false
        config = null
        Log.i("helper", "cleared!")
    }
}