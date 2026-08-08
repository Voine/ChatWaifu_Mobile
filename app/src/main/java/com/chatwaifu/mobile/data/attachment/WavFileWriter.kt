package com.chatwaifu.mobile.data.attachment

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Description: 往文件里写 RIFF/WAVE，16bit PCM。
 *
 * ## 为什么自己写而不是找库
 *
 * WAV 头是 44 个字节的固定布局，没有任何一个第三方库值得为它进来。
 * Media3 的 `Transformer` 只出 MP4/WebM 容器，要 WAV 得自己实现 `Muxer` 接口——
 * 那比这个类长。工程里 `VITS` 模块的 `WaveUtils` 倒是能写 WAV，但它
 * ①走 native（`convertAudioPCMToWaveByteArray`）②吃 `FloatArray` 整份进内存
 * ③写的是 32bit float 格式（`encoding = 3`），三条都不合用。
 *
 * ## 为什么是 RandomAccessFile
 *
 * WAV 头里有两个长度字段（RIFF chunk size 和 data chunk size），
 * 而**写完最后一个采样才知道总长度**。所以先占位 44 字节、流式写 PCM、
 * [close] 时 seek 回去补上。这样多长的音频都不需要在内存里攒。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
internal class WavFileWriter(
    file: File,
    private val sampleRate: Int,
    private val channels: Int,
) : Closeable {

    private val raf = RandomAccessFile(file, "rw")

    /** 已写入的 PCM 字节数，不含头。 */
    var dataBytes: Long = 0
        private set

    init {
        raf.setLength(0)
        // 占位，close() 时回来补
        raf.write(ByteArray(HEADER_BYTES))
    }

    /** 把 [buffer] 的 remaining 部分全部写进去。返回写了多少字节。 */
    fun write(buffer: ByteBuffer): Int {
        val count = buffer.remaining()
        if (count == 0) return 0
        val bytes = ByteArray(count)
        buffer.get(bytes)
        raf.write(bytes)
        dataBytes += count
        return count
    }

    /** 按已写入的字节数算出的时长，落库要用。 */
    fun durationMs(): Long {
        val bytesPerSecond = sampleRate.toLong() * channels * MediaLimits.AUDIO_BYTES_PER_SAMPLE
        return if (bytesPerSecond <= 0) 0 else dataBytes * 1000 / bytesPerSecond
    }

    override fun close() {
        try {
            raf.seek(0)
            raf.write(header())
        } finally {
            raf.close()
        }
    }

    /**
     * 44 字节标准 PCM 头。全部小端——RIFF 家族的字节序就是小端，
     * 这也是它和 big-endian 的 AIFF 的区别。
     */
    private fun header(): ByteArray {
        val bitsPerSample = MediaLimits.AUDIO_BYTES_PER_SAMPLE * 8
        val blockAlign = channels * MediaLimits.AUDIO_BYTES_PER_SAMPLE
        val byteRate = sampleRate * blockAlign
        return ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            // 整个文件长度减去 "RIFF" 和它自己这 8 个字节
            putInt((dataBytes + HEADER_BYTES - 8).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))

            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                      // fmt chunk 长度，PCM 固定 16
            putShort(1)                     // audioFormat: 1 = 线性 PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())

            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes.toInt())
        }.array()
    }

    companion object {
        const val HEADER_BYTES = 44
    }
}
