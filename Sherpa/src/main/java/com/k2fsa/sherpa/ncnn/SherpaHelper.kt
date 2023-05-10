package com.k2fsa.sherpa.ncnn

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Description: SherpaHelper
 * Author: Voine
 * Date: 2023/3/7
 */
@SuppressLint("MissingPermission")
class SherpaHelper(val context: Context) {
    companion object {
        private const val TAG = "SherpaHelper"
    }
    private var model: SherpaNcnn
    private var audioRecord: AudioRecord
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    @Volatile
    private var isRecording: Boolean = false
    private var recordJob: Job? = null
    private var results: MutableList<String> = mutableListOf()

    init {
        model = SherpaNcnn(
            assetManager = context.assets,
            modelConfig = getModelConfig(type = 1, useGPU = true)!!,
            decoderConfig = getDecoderConfig(enableEndpoint = true),
            fbankConfig = getFbankConfig(),
        )
        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
        )
    }

    fun startRecord() {
        if (isRecording) {
            return
        }
        isRecording = true
        Log.i(TAG, "state: ${audioRecord.state}")
        audioRecord.startRecording()
        model.reset()
        results.clear()
        Log.i(TAG, "Started recording")
        recordJob?.cancel()
        recordJob = CoroutineScope(Dispatchers.IO).launch {
            processSamples()
        }
    }

    private fun processSamples() {
        Log.i(TAG, "processing samples")

        val interval = 0.02 // i.e., 20 ms
        val bufferSize = (interval * sampleRateInHz).toInt() // in samples
        val buffer = ShortArray(bufferSize)

        while (isRecording) {
            val ret = audioRecord.read(buffer, 0, buffer.size)
            if (ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                model.decodeSamples(samples)
                val isEndpoint = model.isEndpoint()
                val text = model.text


                if (text.isNotBlank()) {
                    if (isEndpoint) {
                        results[results.size - 1] = text
                        results.add("")
                    } else {
                        if (results.isEmpty()) results.add("")
                        results[results.size - 1] = text
                    }
                }
            }
        }
    }

    fun stopRecord(recognizeCallback: (result: String) -> Unit) {
        isRecording = false
        audioRecord.stop()
        recordJob?.cancel()
        recognizeCallback.invoke(results.joinToString())
    }

    fun releaseRecord() {
        audioRecord.release()
    }
}