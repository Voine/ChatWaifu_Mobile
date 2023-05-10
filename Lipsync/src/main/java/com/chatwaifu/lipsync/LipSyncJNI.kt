package com.chatwaifu.lipsync

object LipSyncJNI {

    private var _visemeRecognizeCallback: VisemeRecognizeCallback? = null

    fun setVisemeCallback(callback: VisemeRecognizeCallback) {
        _visemeRecognizeCallback = callback
    }

    /**
     * A native method that is implemented by the 'lipsync' native library,
     * which is packaged with this application.
     */
    external fun ovrLipSync_Initialize(sampleSize: Int)

    external fun ovrLipSync_CreateContextEx(sampleSize: Int, enableAcceleration: Boolean): Int

    external fun ovrLipSync_ProcessFrame(sampleSize: Int, data: FloatArray, dataSize: Int): Array<String>

    external fun ovrLipSync_DestroyContext()

    external fun ovrLipSync_ShutDown()

    fun onLoadOneViseme(viseme: String){
        _visemeRecognizeCallback?.recognizeViseme(viseme)
    }

    /**
     * 接受 lip-sync 的实时识别结果
     */
    interface VisemeRecognizeCallback {
        fun recognizeViseme(viseme: String) {}
    }

    init {
        System.loadLibrary("lipsync")
    }
}