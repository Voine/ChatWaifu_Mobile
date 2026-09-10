package com.chatwaifu.mobile.utils

import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import com.chatwaifu.lipsync.LipSyncJNI
import com.chatwaifu.live2d.JniBridgeJava
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Description: render live2d lip sync values
 * 由于各种值对应以及处理的延时对应等问题，实际感觉体验不咋地，先默认就播个循环动画算了....
 * Author: Voine
 * Date: 2023/5/2
 */
class LipsValueHandler() {
    companion object {
        //开启，则使用 meta lip sync
        const val USE_REAL_LIP_SYNC = false
        /**
         * meta-lipSync 初始化用的采样率。它是**引擎上下文参数**，
         * 和某个具体音频的采样率是两件事，所以保持固定值。
         * 假动画的时长估算不能用它 —— 见 [playDefaultAnimation]。
         */
        private const val LIP_SYNC_SAMPLE_RATE = 22050
        private const val TAG = "LipsValueHandler"

        private const val INVALID_TONE = "sil"

        //key copy from lipsync.cpp，记录有效发音字符串到 live2d mouth 值的映射, 值是随便写的。。。
        private val mouthValueMap = hashMapOf<String, Float>(
            "PP" to 1f,
            "FF" to 0.5f,
            "TH" to 0.2f,
            "DD" to 0.4f,
            "kk" to 0.5f,
            "CH" to 0.6f,
            "SS" to 0.3f,
            "nn" to 0.3f,
            "RR" to 0.1f,
            "aa" to 1f,
            "E" to 0.5f,
            "ih" to 0.5f,
            "oh" to 0.8f,
            "ou" to 0.5f,
        )
    }
    private val handler: Handler
    private var animationJob: Job? = null
    private var animationTimer: CountDownTimer? = null
    init {
        val handlerThread = HandlerThread("LipsValueHandler")
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                onHandleMessage(msg)
            }
        }
        LipSyncJNI.setVisemeCallback(object : LipSyncJNI.VisemeRecognizeCallback{
            override fun recognizeViseme(viseme: String) {
                Log.d(TAG, "recognize per viseme: $viseme")
                if (viseme != INVALID_TONE) {
                    onRecognizePerViseme(viseme)
                }
            }
        })
    }

    private fun onRecognizePerViseme(viseme: String) {
        JniBridgeJava.nativeProjectMouthOpenY(mouthValueMap[viseme] ?: 0f)
    }

    fun initLipSync() {
        LipSyncJNI.ovrLipSync_Initialize(LIP_SYNC_SAMPLE_RATE)
    }

    fun createContext() {
        LipSyncJNI.ovrLipSync_CreateContextEx(LIP_SYNC_SAMPLE_RATE, true)
    }

    private fun onHandleMessage(msg: Message) {
        val payload = msg.obj as LipsPayload
        if (!USE_REAL_LIP_SYNC) {
            playDefaultAnimation(payload.pcm, payload.sampleRate)
            return
        }
        val result = LipSyncJNI
            .ovrLipSync_ProcessFrame(LIP_SYNC_SAMPLE_RATE, payload.pcm, payload.pcm.size)
            .toList()
        //this is total result
        Log.d(TAG, "$result")
        JniBridgeJava.nativeProjectMouthOpenY(0f)
    }

    /**
     * @param sampleRate **必须是这段 PCM 自己的采样率**。改造前这里用的是写死的 22050，
     *   而 BV2 日文底模出的是 44100 —— 动画时长会算成音频实际长度的两倍，
     *   嘴一直动到早就播完了还在动。
     */
    private fun playDefaultAnimation(data: FloatArray, sampleRate: Int) {
        //根据 data 长度粗略估计一下动画时长...
        val time = data.size.toLong() * 1000 / sampleRate.coerceAtLeast(1)
        Log.d(TAG, "try to play animation in $time")
        animationJob?.cancel()
        animationJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            while (true) {
                animationList.forEach {
                    delay(10)
                    JniBridgeJava.nativeProjectMouthOpenY(it * 0.8f)
                }
            }
        }
        animationTimer?.cancel()
        animationTimer = object : CountDownTimer(time, 1000){
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                animationJob?.cancel()
                JniBridgeJava.nativeProjectMouthOpenY(0f)
            }
        }
        animationTimer?.start()
    }

    fun sendLipsValues(array: FloatArray, sampleRate: Int) {
        handler.sendMessage(Message.obtain(handler, 0, LipsPayload(array, sampleRate)))
    }

    /** PCM 和它的采样率必须一起过 Handler，分开传就会错配到别的那一段音频上 */
    private data class LipsPayload(val pcm: FloatArray, val sampleRate: Int)

    fun destroyContext() {
        LipSyncJNI.ovrLipSync_DestroyContext()
    }

    fun shutDown() {
        LipSyncJNI.ovrLipSync_ShutDown()
    }

    private val animationList = listOf<Float>(
        0f,
        0.1f,
        0.2f,
        0.3f,
        0.4f,
        0.5f,
        0.6f,
        0.7f,
        0.8f,
        0.9f,
        1f,
        0.9f,
        0.8f,
        0.7f,
        0.6f,
        0.5f,
        0.4f,
        0.3f,
        0.2f,
        0.1f,
        0f
    )
}