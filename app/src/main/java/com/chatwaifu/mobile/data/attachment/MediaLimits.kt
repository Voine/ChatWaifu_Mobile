package com.chatwaifu.mobile.data.attachment

/**
 * Description: 归一化的目标参数和闸门。各家的具体数值会随文档漂移，集中在这里改。
 *
 * 两类常量要分清：
 *
 * - `*_SOURCE_MAX_BYTES` 是**入口闸门**，判的是用户选中的原文件，目的是拦住
 *   「转码它要花十分钟」和「解码它会 OOM」。这一层判定必须在**读字节之前**完成
 *   （靠 `OpenableColumns.SIZE`），否则闸门形同虚设
 * - 其余是**产物目标**，判的是归一化之后落盘那份。落盘那份就是模型看到的东西
 *
 * 产物参数一律按**最严的 provider** 取：按最严的压，切到任何一家重放都合法；
 * 按宽松的压，切到严格的一家就重放失败。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
object MediaLimits {

    // ---- 图像 ----

    /** 长边上限。再大对识别精度没有收益，只是白烧 token。 */
    const val IMAGE_MAX_EDGE = 1568

    /** 单张图字节上限，压到这个以下。取的是各家里最紧的那一档再留些余量。 */
    const val IMAGE_MAX_BYTES = 4L * 1024 * 1024

    /** JPEG 质量的起点、地板和退让步长。 */
    const val JPEG_QUALITY_START = 88
    const val JPEG_QUALITY_FLOOR = 55
    const val JPEG_QUALITY_STEP = 11

    /**
     * 原图的入口闸门，比 [IMAGE_MAX_BYTES] 宽得多——原图可以很大，是**压完**才要求 4MB。
     * 这里拦的是「解码它会 OOM」，不是「发不出去」。
     */
    const val IMAGE_SOURCE_MAX_BYTES = 100L * 1024 * 1024

    // ---- 音频 ----

    /**
     * 规范采样率和声道数：**16kHz 单声道**。
     *
     * 这个数字是 OpenAI 划出来的：它的音频输入只收 `wav` / `mp3`，而 Android 平台
     * **没有 MP3 编码器**（MediaCodec 只有 decoder），要出 mp3 得引 LAME。
     * 所以规范形态是 PCM WAV。16k mono 是语音理解够用的下限，
     * 也正好和本机 ASR 那条链路（Sherpa 吃 16k mono）一致。
     */
    const val AUDIO_SAMPLE_RATE = 16_000
    const val AUDIO_CHANNELS = 1

    /** 16bit PCM，即每个采样 2 字节。WAV 头和体积估算都要用。 */
    const val AUDIO_BYTES_PER_SAMPLE = 2

    /**
     * 时长上限。16k/mono/16bit 是 32KB/s，10 分钟约 19MB——
     * 正好落在各家内联/上传的可接受区间里。
     */
    const val AUDIO_MAX_DURATION_MS = 10 * 60 * 1000L

    /** 原音频的入口闸门。 */
    const val AUDIO_SOURCE_MAX_BYTES = 200L * 1024 * 1024

    // ---- 视频 ----

    /**
     * 视频**不转码**，走抽帧：采样成 N 张图 + 一条音轨，然后各自走图像/音频管线。
     *
     * 这么做的理由是能力面：视频输入目前只有 Gemini 一家支持，而抽帧之后
     * 「看视频」在**任何有 vision 的基座上都能用**，且一行转码代码都不用写。
     * 代价是丢掉了帧间连续性——对「这段视频里发生了什么」够用，
     * 对「数一下他挥了几次手」不够。
     */
    const val VIDEO_FRAME_INTERVAL_MS = 2_000L

    /**
     * 抽帧数上限。超过就拉大间隔而不是多抽——N 张图是 N 倍 token，
     * 24 帧已经能把一个短视频讲清楚了。
     */
    const val VIDEO_MAX_FRAMES = 24

    /**
     * 帧的长边上限，**刻意比 [IMAGE_MAX_EDGE] 小得多**。
     *
     * 单张图用 1568 是因为它就一张；帧有几十张，token 成本是分辨率的函数乘以帧数。
     * 768 长边一帧约 400~450 token，24 帧一万出头，是能接受的量级；
     * 按 1568 抽 24 帧能到六万 token，一次就把上下文烧光了。
     */
    const val VIDEO_FRAME_MAX_EDGE = 768

    /** 时长上限。抽帧本身很快，但太长的视频抽出来的帧也讲不清事。 */
    const val VIDEO_MAX_DURATION_MS = 5 * 60 * 1000L

    /** 原视频的入口闸门。 */
    const val VIDEO_SOURCE_MAX_BYTES = 500L * 1024 * 1024

    // ---- 文档 ----

    /** PDF 之类**不转码**，只做体积闸门后原样落地。 */
    const val DOC_MAX_BYTES = 20L * 1024 * 1024
}
