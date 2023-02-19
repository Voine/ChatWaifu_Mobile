package com.chatwaifu.vits.data

data class Config(val data: MData?, val symbols: List<String>?, val speakers: List<String>?) {
    data class MData(
        val text_cleaners: List<String>?,
        val sampling_rate: Int?,
        val n_speakers: Int?,
    )
}
