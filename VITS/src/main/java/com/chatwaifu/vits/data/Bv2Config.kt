package com.chatwaifu.vits.data

/**
 * Description: Bert-VITS2 的 config.json，只取推理侧真正用得到的字段。
 *
 * 完整的 config.json 还带着一大堆训练超参（`train` / `model` 段），端上一个都不需要——
 * BV2 的推理参数已经烧进 6 个 `.mnn` 里了。Gson 会忽略没声明的字段。
 *
 * 和被删掉的老 `Config` 的区别：老的要 `symbols` 和 `text_cleaners`，
 * 因为音素化是我们自己在 Kotlin 里做的；BV2 的 G2P 在 `text-preprocess` 模块里，
 * 所以这里只剩「播放要用的采样率」和「多人模型的 speaker 表」。
 *
 * Author: Voine
 * Date: 2026/9/10
 */
data class Bv2Config(val data: MData?) {
    data class MData(
        val sampling_rate: Int?,
        /** speaker 名 → speaker id。日文底模是 `{"八重神子_JP":0,"宵宫_JP":1,...}` */
        val spk2id: Map<String, Int>?,
    )
}
