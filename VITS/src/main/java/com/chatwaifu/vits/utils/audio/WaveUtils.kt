package com.chatwaifu.vits.utils.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.chatwaifu.vits.data.WavHead
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WaveUtils {
    // write wave file to storage
    fun writeWav(context: Context, audioData: FloatArray, folder: String, samplingRate: Int, name: String): String {
        var path = ""
        path = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // check writing file permission
            val readRequest = Manifest.permission.WRITE_EXTERNAL_STORAGE
            val permission =
                ContextCompat.checkSelfPermission(context, readRequest)
            if (permission != PackageManager.PERMISSION_GRANTED) return ""
            folder.substring(0, folder.lastIndexOf("/")) +
                    "/" + "$name-${System.currentTimeMillis()}.wav"
        } else {
            // check writing file permission
            if (!Environment.isExternalStorageEmulated()) return ""
            folder.substring(0, folder.lastIndexOf("/")) +
                    "/" + "$name-${System.currentTimeMillis()}.wav"
        }
        if (path != "") {
            try {
                val output = File(path)
                val fos = FileOutputStream(output)
                val waveData = convertAudioPCMToWaveByteArray(audioData, samplingRate) ?: return ""
                fos.write(waveData)
                fos.close()
                return path
            } catch (e: Exception) {
                Log.e("FileUtil", e.message.toString())
            }
        }
        return ""
    }

    // load wave file from storage
    fun loadWav(path: String, sr: Int): FloatArray? {
        var pcm: FloatArray? = null
        val file = File(path)
        if (file.length() > Int.MAX_VALUE) throw Exception("音频过长！")
        val fin = FileInputStream(file)
        // read header
        val head = ByteArray(44)
        fin.read(head, 0, 44)

        // parse header
        val wh = parseWavHead(head, sr)
        Log.i("WaveHeader", wh.toString())
        // read pcm
        val pcmByteArray = ByteArray((file.length() - 44).toInt())
        fin.read(pcmByteArray, 0, pcmByteArray.size)

        val order = ByteOrder.LITTLE_ENDIAN
        // encoding format
        if (wh.encoding.toInt() == 3) {
            val tmp = ByteBuffer.wrap(pcmByteArray).order(order).asFloatBuffer()
            pcm = FloatArray(tmp.limit())
            tmp.get(pcm)
        }
        if (wh.encoding.toInt() == 1) {
            val tmp = ByteBuffer.wrap(pcmByteArray).order(order).asShortBuffer()
            val pcmShort = ShortArray(tmp.limit())
            tmp.get(pcmShort)
            pcm = pcmShort.map { it.toFloat() / 32768.0f }.toFloatArray()
        }

        // channels
        if (wh.channels.toInt() == 1) return pcm
        if (wh.channels.toInt() == 2 && pcm != null){
            val leftChannel = pcm.filterIndexed { index, fl -> index % 2 == 0 }.toFloatArray()
            val rightChannel = pcm.filterIndexed { index, fl -> index % 2 != 0 }.toFloatArray()
            if (leftChannel.size >= rightChannel.size){
                return FloatArray(rightChannel.size) {(leftChannel[it] + rightChannel[it]).div(2.0f)}
            } else {
                return FloatArray(leftChannel.size) {(leftChannel[it] + rightChannel[it]).div(2.0f)}
            }
        }
        return pcm
    }

    // parse wave file head
    private fun parseWavHead(head: ByteArray, samplingRate: Int): WavHead {
        if (head.size < 44) throw Exception("文件头小于44字节！")

        val RIFF = String(head, 0, 4)

        val WAVE = String(head, 8, 4)

        if (RIFF != "RIFF" && WAVE != "WAVE") throw Exception("不是wav文件！")

        // little endian order
        val order = ByteOrder.LITTLE_ENDIAN

        // encoding format
        val encoding = ByteBuffer.wrap(head, 20, 2).order(order).short

        // channels
        val channels = ByteBuffer.wrap(head, 22, 2).order(order).short

        // sampling rate
        val sr = ByteBuffer.wrap(head, 24, 4).order(order).int

        if (samplingRate != sr) throw Exception("仅支持采样率为${samplingRate}Hz的音频文件！")

        return WavHead(sr, channels, encoding)
    }

    fun audioLenToDuration(length: Int, samplingRate: Int): String{
        val duration = (length.toFloat() / samplingRate.toFloat()).toLong()
        val h = duration.div(3600)
        val m = (duration - h * 3600).div(60)
        val s = duration - (h * 3600 + m * 60)
        return "%02d:%02d:%02d".format(h, m, s)
    }


    private external fun convertAudioPCMToWaveByteArray(
        jaudio: FloatArray,
        samplingRate: Int
    ): ByteArray?

    init {
        System.loadLibrary("moereng-vits")
    }
}