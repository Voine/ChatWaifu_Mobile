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
            if (recorder?.state == AudioRecord.STATE_UNINITIALIZED) {
                recorder = AudioRecord(
                    audioSource,
                    sampleRate,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    audioFormat,
                    minBufferSize
                )
                if (recorder?.state == AudioRecord.STATE_UNINITIALIZED){
                    recorder = AudioRecord(
                        audioSource,
                        sampleRate,
                        AudioFormat.CHANNEL_CONFIGURATION_DEFAULT,
                        audioFormat,
                        minBufferSize
                    )
                }
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