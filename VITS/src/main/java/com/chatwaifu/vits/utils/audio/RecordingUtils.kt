package com.chatwaifu.vits.utils.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log


class RecordingUtils {
    private var recorder: AudioRecord? = null

    private val audioFormat = AudioFormat.ENCODING_PCM_FLOAT

    private val audioSource = MediaRecorder.AudioSource.MIC

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    private val sampleRate = 22050

    var isRecording = false

    private var audio: FloatArray? = null

    private var minBufferSize = 0

    var initialized = false

    @SuppressLint("MissingPermission")
    fun initRecorder() {
        if (!initialized) {
            minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize < 0) {
                initialized = false
                throw Exception("AudioRecorder不可用！")
            }
            Log.d("RecordingUtils", "buffer size = $minBufferSize")
            recorder = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize
            )
            // 原先这里是两级 fallback：CHANNEL_CONFIGURATION_MONO → CHANNEL_CONFIGURATION_DEFAULT。
            // 两个常量都已废弃，而且第一级其实是个 bug —— CHANNEL_CONFIGURATION_MONO 的值是 2，
            // 等于 CHANNEL_OUT_MONO，是**输出**声道掩码，喂给 AudioRecord 没有意义
            // （录音侧的单声道是 CHANNEL_IN_MONO = 16，也就是上面 channelConfig 已经在用的值）。
            // 换成输入侧常量之后第一级和主路径完全重合，所以塌成一级 fallback：
            // CHANNEL_IN_DEFAULT（值 1，和废弃的 CHANNEL_CONFIGURATION_DEFAULT 同值，行为不变）。
            if (recorder?.state == AudioRecord.STATE_UNINITIALIZED) {
                recorder = AudioRecord(
                    audioSource,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    audioFormat,
                    minBufferSize
                )
            }
            if (recorder?.state == AudioRecord.STATE_UNINITIALIZED){
                initialized = false
                throw Exception("AudioRecord不可用，请检查麦克风设备是否正常！")
            }
            initialized = true
            isRecording = true
        }
        isRecording = true
    }

    fun record(): FloatArray? {
        audio = FloatArray(minBufferSize)
        // begin recording
        recorder?.startRecording()
        recorder?.read(audio!!, 0, minBufferSize, AudioRecord.READ_BLOCKING)
        return audio?.map { it * 20 }?.toFloatArray()
    }

    fun stop() {
        if (isRecording) {
            recorder?.stop()
            isRecording = false
        }
    }

    fun release() {
        stop()
        recorder?.release()
        recorder = null
    }
}