package com.chatwaifu.mobile.data.model

/**
 * Description: 一个可用角色的领域模型。
 *
 * 刻意不含 R.drawable 之类的资源 id —— 头像映射是 UI 层的事，由 UI 层用 [name] 去映射。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
data class CharacterModel(
    val name: String,
    val source: ModelSource,
    /** live2d 资源目录绝对路径 */
    val live2dDir: String,
    /**
     * live2d 入口文件名，如 `Yuuka.model3.json`。
     * 从 meta.json 读出来的实际文件名，不再靠「文件名必须和目录名一致」的约定去猜。
     */
    val live2dEntryFileName: String,
    /**
     * 声库根目录绝对路径，null 表示这个角色没有语音。
     *
     * 内置角色指向 [ModelStorage.bv2Root] 那份共享的 BV2 权重（单模型多 speaker，
     * 靠 [speakerId] 区分），导入角色指向自己的 `vits/` 目录。
     */
    val vitsDir: String?,
    /**
     * BERT 编码器目录绝对路径。**按语种共享**、随包走，所以和 [vitsDir] 分开：
     * 导入模型只需要自带声学模型，不用背一份 40MB 的 BERT。
     */
    val bertDir: String?,
    /** BV2 config.json `spk2id` 里的 speaker id，单人模型为 0 */
    val speakerId: Int,
    /** `LANGUAGE_ZH/EN/JP/MIX_ZH_EN` 之一 */
    val language: Int,
) {
    val hasVoice: Boolean get() = vitsDir != null
}

enum class ModelSource {
    /** 随包内置，从 assets 解出来的 */
    BUILT_IN,

    /** 用户自己导入的 */
    IMPORTED,
}
