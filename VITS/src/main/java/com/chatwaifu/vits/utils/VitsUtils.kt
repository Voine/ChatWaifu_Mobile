package com.chatwaifu.vits.utils

import com.chatwaifu.vits.data.Config


object VitsUtils {
    fun checkConfig(config: Config?, type: String): Boolean =
        when(type){
            "vc"-> config?.data?.n_speakers != null
                    && config.data.sampling_rate != null
            "multi" -> config?.data?.n_speakers != null
                    && config.data.sampling_rate != null
                    && !config.data.text_cleaners.isNullOrEmpty()
                    && !config.speakers.isNullOrEmpty()
                    && !config.symbols.isNullOrEmpty()
            "single" -> config?.data?.sampling_rate != null
                    && !config.data.text_cleaners.isNullOrEmpty()
                    && !config.symbols.isNullOrEmpty()
            else -> false
        }


    external fun testGpu(): Boolean

    external fun checkThreadsCpp(): Int

    init {
        System.loadLibrary("moereng-vits")
    }
}