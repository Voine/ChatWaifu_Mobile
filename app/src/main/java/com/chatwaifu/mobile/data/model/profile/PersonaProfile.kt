package com.chatwaifu.mobile.data.model.profile

/**
 * Description: 角色的人格配置。[CharacterPackage.personaProfileId] 引用的就是 [id]。
 *
 * **只描述「她是谁 / 怎么说话」**，不含任何推理侧配置（endpoint / key / model /
 * ProviderConfig）——那一层归 `ChatProviderSettings` 的 `InferenceSelection`。
 * 这条边界由 `CharacterProfileTest` 用反射守着。
 *
 * [prompt] 的落盘位置刻意**不在** profile 的 JSON 记录里，而仍是
 * `CharacterRepository.getSystemPrompt/saveSystemPrompt` 那套老 key
 * （内置 `SAVED_*_SETTING`、导入 `saved_system_prompt_<名字>`）。这样 Phase 2.7 的
 * 迁移对存量数据是 no-op：用户不需要重填，Setting 页那三个内置编辑框也和详情页同源。
 * profile 记录里只存 [name] / [builtIn] 这类纯展示元数据。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
data class PersonaProfile(
    val id: String,
    val name: String,
    val prompt: String,
    val builtIn: Boolean = false,
) {
    /** 没有任何人物设定。**不等于「继承上一个角色」**，见 [CharacterProfileRepository]。 */
    val isEmpty: Boolean get() = prompt.isBlank()

    companion object {
        /** profile id 由稳定角色 id 确定性派生，不随 displayName / storageKey 变化。 */
        fun idFor(characterId: String): String = "persona:$characterId"
    }
}
