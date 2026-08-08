package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.nio.ByteBuffer

/**
 * Description: 任意容器的第一条音轨 → 16kHz 单声道 16bit PCM 的 WAV 文件。
 *
 * ## 分工
 *
 * - **解封装 + 解码**：平台的 `MediaExtractor` + `MediaCodec`。这两个能吃的格式
 *   就是这台设备能吃的格式，用第三方库也绕不过底层的编解码器
 * - **重采样 + 声道混合**：Media3 的 `androidx.media3.common.audio`。
 *   这是引 `media3-common` 的唯一理由——重采样自己写会得到一个有可听 aliasing 的
 *   线性插值版本，而 `SonicAudioProcessor` 是 ExoPlayer 用了十年的实现
 *
 * 刻意**没有**用 `AudioProcessingPipeline`（它能把这段串联代码省掉）：它的构造函数只收
 * Guava 的 `ImmutableList`，为一个 list 类型把 Guava 拉进来不值得，
 * 而串联逻辑本身就是下面 [Chain] 那三十行。
 *
 * ## 为什么目标是 16k mono WAV
 *
 * OpenAI 的音频输入**只收 wav / mp3**，而 Android 平台没有 MP3 编码器
 * （`MediaCodec` 只有 decoder），所以规范形态只能是 PCM WAV。
 * 16k 单声道是语音理解够用的下限，也和本机 ASR（Sherpa 吃 16k mono）一致。
 * 详见 [MediaLimits.AUDIO_SAMPLE_RATE]。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
/*
 * 用 androidx 的 @OptIn 而不是 kotlin 的：media3 的 UnstableApi 是用
 * androidx.annotation.RequiresOptIn 标的，kotlin.OptIn 认不出它
 * （编译器会直接警告「'@OptIn' has no effect」），而 Android lint 的
 * UnsafeOptInUsageError 检查认的恰好是 androidx 那个。
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PcmDecoder(private val context: Context) {

    /**
     * 解 [uri] 的第一条音轨写进 [target]。
     *
     * @param onProgress 0..100，按已读取的时间戳占总时长的比例算。
     * @return 实际写出的时长（毫秒）；没有音轨或解码失败返回 null，此时 [target] 已被删掉。
     */
    fun decodeToWav(uri: Uri, target: File, onProgress: (Int) -> Unit = {}): Long? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: WavFileWriter? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = firstAudioTrack(extractor) ?: run {
                Log.w(TAG, "no audio track in $uri")
                return null
            }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
            writer = WavFileWriter(
                file = target,
                sampleRate = MediaLimits.AUDIO_SAMPLE_RATE,
                channels = MediaLimits.AUDIO_CHANNELS,
            )
            pump(extractor, codec, inputFormat, writer, onProgress)
            writer.durationMs()
        } catch (e: Exception) {
            Log.e(TAG, "decode audio failed for $uri", e)
            target.delete()
            null
        } catch (e: OutOfMemoryError) {
            // 畸形文件能让解码器申请一个荒谬的输出缓冲。宁可当失败处理也不要连带整个进程
            Log.e(TAG, "OOM while decoding $uri", e)
            target.delete()
            null
        } finally {
            runCatching { writer?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }

    /**
     * 标准的 `MediaCodec` 同步模式喂料/取料循环。
     *
     * 两个容易写错的地方：
     * - 输出缓冲要先按 `bufferInfo.offset` / `size` 设好 position 和 limit 再交给下游。
     *   直接整块用会把上一轮的残留数据也写进去
     * - `INFO_OUTPUT_FORMAT_CHANGED` 才是**实际** PCM 参数的来源。轨道格式里写的采样率
     *   和解码器真正输出的可以不一样（尤其是 HE-AAC，它会把采样率翻倍）
     */
    private fun pump(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        writer: WavFileWriter,
        onProgress: (Int) -> Unit,
    ) {
        val totalUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            inputFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val info = MediaCodec.BufferInfo()
        var chain: Chain? = null
        var inputDone = false
        var outputDone = false
        var lastPercent = -1

        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)
                    val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val sampleTime = extractor.sampleTime
                        codec.queueInputBuffer(index, 0, size, sampleTime, 0)
                        extractor.advance()
                        if (totalUs > 0) {
                            val percent = (sampleTime * 100 / totalUs).toInt().coerceIn(0, 99)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    chain = Chain(codec.outputFormat.toAudioFormat())

                else -> if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        // 正常情况下 FORMAT_CHANGED 一定先到，这里的兜底是为了那些
                        // 不守规矩的解码器：退回用轨道格式声明的参数
                        val active = chain ?: Chain(inputFormat.toAudioFormat()).also { chain = it }
                        active.process(buffer) { writer.write(it) }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        chain?.endOfStream { writer.write(it) }
        onProgress(100)
    }

    /**
     * `KEY_PCM_ENCODING` 只在非 16bit 时才会出现，缺省就是 16bit。
     * Media3 的 `C.ENCODING_PCM_*` 和平台 `AudioFormat.ENCODING_PCM_*` 是同一批数值，
     * 所以可以直接透传。
     */
    private fun MediaFormat.toAudioFormat(): AudioProcessor.AudioFormat =
        AudioProcessor.AudioFormat(
            getInteger(MediaFormat.KEY_SAMPLE_RATE),
            getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            if (containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                C.ENCODING_PCM_16BIT
            },
        )

    /**
     * 按需串起来的处理器链：位深 → 声道 → 采样率。
     *
     * **只加真正需要的那几级**——已经是 16k mono 16bit 的输入会得到一条空链，
     * 字节原样透传，不白过一遍处理器。
     *
     * 顺序不能换：先把位深统一成 16bit，后面两级才有确定的帧长可算；
     * 先降声道再重采样，因为重采样的开销和声道数成正比。
     */
    private inner class Chain(inputFormat: AudioProcessor.AudioFormat) {

        private val processors: List<AudioProcessor>

        init {
            val list = mutableListOf<AudioProcessor>()
            var format = inputFormat

            if (format.encoding != C.ENCODING_PCM_16BIT) {
                val processor = ToInt16PcmAudioProcessor()
                format = processor.configure(format)
                list += processor
            }
            if (format.channelCount != MediaLimits.AUDIO_CHANNELS) {
                val processor = ChannelMixingAudioProcessor()
                // 恒定增益（系数 1/N 求和）而不是恒定功率（1/√N）：
                // 立体声降单声道时两个声道通常高度相关，用恒定功率会把幅度推高约 3dB 导致削波
                processor.putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantGain(
                        format.channelCount,
                        MediaLimits.AUDIO_CHANNELS,
                    )
                )
                format = processor.configure(format)
                list += processor
            }
            if (format.sampleRate != MediaLimits.AUDIO_SAMPLE_RATE) {
                val processor = SonicAudioProcessor()
                // 必须在 configure 之前设，configure 返回的才是改过的输出格式
                processor.setOutputSampleRateHz(MediaLimits.AUDIO_SAMPLE_RATE)
                format = processor.configure(format)
                list += processor
            }
            // 无参的 flush() 已废弃，走带 StreamMetadata 的那个。
            // DEFAULT 表示「不是从某个时间轴位置续上的流」，一次性转码正是这种情况
            list.forEach { it.flush(AudioProcessor.StreamMetadata.DEFAULT) }
            processors = list
            Log.d(TAG, "chain: $inputFormat -> $format (${processors.size} stage(s))")
        }

        fun process(input: ByteBuffer, sink: (ByteBuffer) -> Unit) {
            if (processors.isEmpty()) {
                sink(input)
                return
            }
            feed(0, input, sink)
        }

        fun endOfStream(sink: (ByteBuffer) -> Unit) {
            processors.forEachIndexed { index, processor ->
                processor.queueEndOfStream()
                drain(index, sink)
            }
        }

        /**
         * `queueInput` 的契约是「能吃多少吃多少」，吃不完要先把输出排空再喂剩下的，
         * 所以这里是 while 而不是一次调用。
         */
        private fun feed(index: Int, input: ByteBuffer, sink: (ByteBuffer) -> Unit) {
            val processor = processors[index]
            while (input.hasRemaining()) {
                val before = input.remaining()
                processor.queueInput(input)
                drain(index, sink)
                if (input.remaining() == before) {
                    // 一个字节都没吃进去而输出也排空了：再循环下去就是死循环。
                    // 正常处理器不会走到这里，出现说明这一级的状态机有问题
                    Log.w(TAG, "processor $index stalled, dropping ${input.remaining()} bytes")
                    input.position(input.limit())
                    return
                }
            }
        }

        private fun drain(index: Int, sink: (ByteBuffer) -> Unit) {
            val processor = processors[index]
            while (true) {
                val output = processor.getOutput()
                if (!output.hasRemaining()) return
                if (index == processors.lastIndex) sink(output) else feed(index + 1, output, sink)
            }
        }
    }

    companion object {
        private const val TAG = "PcmDecoder"

        /** `dequeue*Buffer` 的超时。10ms 够短到不卡进度回调，也够长到不空转烧 CPU。 */
        private const val TIMEOUT_US = 10_000L
    }
}
