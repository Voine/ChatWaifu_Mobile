package com.k2fsa.sherpa.mnn

import android.media.AudioRecord
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Description: 解码循环的 PCM 来源。
 *
 * 抽出来只为一件事：**让同一条解码链路既能吃麦克风，也能吃 WAV 文件**。
 * 模拟器没有真实麦克风输入（只有静音），而 partial transcript 恰恰是
 * Phase 2.8 唯一没能在设备上验证的东西。喂文件可以把
 * 「解码循环 → partial → AIDL → AsrEngine → 状态机 → UI」整条路跑通，
 * 只有 AudioRecord 本身不被覆盖。
 *
 * 换 ASR runtime 时它还是回归工具：同一段音频喂两个引擎，直接比对识别结果。
 *
 * Author: Voine
 * Date: 2026/10/13
 */
internal interface PcmSource {

    /** 16kHz 单声道 16bit。返回读到的采样数，<=0 表示这一轮没数据，<0 表示结束。 */
    fun read(buffer: ShortArray): Int

    fun start()

    fun stop()

    fun release()
}

/** 麦克风。生产路径用的就是它，行为和改造前完全一致。 */
internal class AudioRecordSource(private val record: AudioRecord) : PcmSource {

    override fun read(buffer: ShortArray): Int = record.read(buffer, 0, buffer.size)

    override fun start() {
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "AudioRecord did not enter recording state"
        }
    }

    override fun stop() {
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }.onFailure { Log.w(TAG, "stop AudioRecord failed", it) }
    }

    override fun release() {
        runCatching { record.release() }.onFailure { Log.w(TAG, "release failed", it) }
    }

    private companion object {
        const val TAG = "AudioRecordSource"
    }
}

/**
 * WAV 文件。**只给验证用**，由 debug 构建的调试入口触发，正式链路不会创建它。
 *
 * 按真实时长节流（每帧 sleep 掉这一帧对应的时长）而不是一口气喂完：
 * 不节流的话 endpoint 的静音规则（2.4s / 1.4s）会在几十毫秒内被跑完，
 * partial 也会一次性跳到终态，验证不出流式行为。
 *
 * 只认 16kHz 单声道 16bit PCM —— 和 recognizer 的 fbank 配置一致，
 * 不做重采样（要做就该在采集侧做，不该藏在测试工具里）。
 */
internal class WavFileSource(private val path: String) : PcmSource {

    private var file: RandomAccessFile? = null
    private var remainingBytes: Long = 0
    private var finished = false

    override fun start() {
        val wav = File(path)
        require(wav.isFile) { "wav not found" }
        val raf = RandomAccessFile(wav, "r")
        val header = parseHeader(raf)
        require(header.sampleRate == EXPECTED_SAMPLE_RATE) {
            "expected ${EXPECTED_SAMPLE_RATE}Hz, got ${header.sampleRate}"
        }
        require(header.channels == 1) { "expected mono, got ${header.channels}" }
        require(header.bitsPerSample == 16) { "expected 16bit, got ${header.bitsPerSample}" }
        raf.seek(header.dataOffset)
        remainingBytes = header.dataSize
        file = raf
        finished = false
        Log.i(TAG, "feeding wav: ${wav.name}, ${header.dataSize / 2} samples")
    }

    override fun read(buffer: ShortArray): Int {
        if (finished) return -1
        val raf = file ?: return -1
        val wantBytes = (buffer.size * 2).toLong().coerceAtMost(remainingBytes).toInt()
        if (wantBytes <= 0) {
            finished = true
            return -1
        }
        val bytes = ByteArray(wantBytes)
        val read = raf.read(bytes)
        if (read <= 0) {
            finished = true
            return -1
        }
        remainingBytes -= read
        val shorts = read / 2
        ByteBuffer.wrap(bytes, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            .get(buffer, 0, shorts)
        // 按真实时长节流，让 endpoint 的静音规则和 partial 节奏保持真实
        Thread.sleep((shorts * 1000L / EXPECTED_SAMPLE_RATE).coerceAtLeast(1L))
        return shorts
    }

    override fun stop() {
        finished = true
    }

    override fun release() {
        runCatching { file?.close() }.onFailure { Log.w(TAG, "close wav failed", it) }
        file = null
    }

    /** 只认标准 RIFF/WAVE，按 chunk 走到 `data`（有些文件在 fmt 和 data 之间还有 LIST）。 */
    private fun parseHeader(raf: RandomAccessFile): WavHeader {
        val riff = ByteArray(12)
        raf.readFully(riff)
        require(String(riff, 0, 4) == "RIFF" && String(riff, 8, 4) == "WAVE") { "not a RIFF/WAVE file" }

        var sampleRate = 0
        var channels = 0
        var bits = 0
        while (true) {
            val chunkHeader = ByteArray(8)
            val got = raf.read(chunkHeader)
            if (got < 8) error("unexpected end of wav before data chunk")
            val id = String(chunkHeader, 0, 4)
            val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(size.toInt())
                    raf.readFully(fmt)
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    bb.short // audio format
                    channels = bb.short.toInt()
                    sampleRate = bb.int
                    bb.int // byte rate
                    bb.short // block align
                    bits = bb.short.toInt()
                }

                "data" -> return WavHeader(sampleRate, channels, bits, raf.filePointer, size)
                else -> raf.seek(raf.filePointer + size)
            }
        }
    }

    private data class WavHeader(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Long,
    )

    private companion object {
        const val TAG = "WavFileSource"
        const val EXPECTED_SAMPLE_RATE = 16000
    }
}
