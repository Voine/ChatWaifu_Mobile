package com.chatwaifu.vits

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log

/**
 * Description: SoundPlayHandler
 * Author: Voine
 * Date: 2023/2/23
 */
class SoundPlayHandler {
    private val handler: Handler
    private var audioTrack: AudioTrack? = null
    private var sampleRate = 22050 // default sample rate
    private var channels = AudioFormat.CHANNEL_OUT_MONO // default channels
    private var audioFormat = AudioFormat.ENCODING_PCM_FLOAT
    private var bufferSize = 0

    init {
        val handlerThread = HandlerThread("SoundPlayHandler")
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                onHandleMessage(msg)
            }
        }
        bufferSize = AudioTrack.getMinBufferSize(sampleRate, channels, audioFormat)
        if (bufferSize <= 0) throw Exception("AudioTrack不可用！")
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setChannelMask(channels)
                    .setSampleRate(sampleRate).build()
            )
            .setBufferSizeInBytes(bufferSize).build()
        audioTrack?.play()
    }

    fun setTrackData(sr: Int, ch: Int) {
        if (ch == 2) channels = AudioFormat.CHANNEL_OUT_STEREO
        if (ch > 2 || ch < 0) throw Exception("不支持的通道数$ch！")
        if (sampleRate <= 0) throw Exception("不支持的采样率$sr！")
        sampleRate = sr
        Log.i("AudioTrack", "sampling rate:$sr channels:$ch")
    }

    fun sendSound(floatArray: FloatArray) {
        handler.sendMessage(Message.obtain(handler, 0, floatArray))
    }

    fun onHandleMessage(msg: Message) {
        val sound = msg.obj as FloatArray
        try {
            Log.d(TAG, "try to write arr....")
            audioTrack?.write(sound, 0, sound.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        audioTrack?.release()
    }
    companion object {
        private const val TAG = "SoundPlayHandler"
    }
}