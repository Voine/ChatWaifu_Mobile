package com.chatwaifu.mobile.data.model

/**
 * Description: 一个可用角色包的领域模型。
 *
 * [id] 是产品层稳定身份；[storageKey] 只兼容现有按角色名分区的 Room、Memory、
 * persona 和触控数据。新代码不得用 displayName 或 storageKey 代替 id 做 UI 选择。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
data class CharacterPackage(
    val id: String,
    val displayName: String,
    val storageKey: String,
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
    val preview: CharacterPreview? = null,
    val personaProfileId: String? = null,
    val voiceProfileId: String? = null,
    val behaviorProfileId: String? = null,
    val availability: CharacterAvailability = CharacterAvailability.AVAILABLE,
) {
    val hasVoice: Boolean get() = vitsDir != null

    /** 旧调用点的过渡别名；只用于展示，身份判断必须使用 [id]。 */
    @Deprecated("Use displayName for UI or storageKey for legacy storage")
    val name: String get() = displayName
}

typealias CharacterModel = CharacterPackage

sealed interface CharacterPreview {
    data class FilePath(val path: String) : CharacterPreview
}

enum class CharacterAvailability {
    AVAILABLE,
    MISSING_LIVE2D,
}

enum class ModelSource {
    /** 随包内置，从 assets 解出来的 */
    BUILT_IN,

    /** 用户自己导入的 */
    IMPORTED,
}
