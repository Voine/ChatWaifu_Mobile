package com.chatwaifu.mobile.data.model

/**
 * Description: 角色模型的唯一数据源。
 *
 * 内置模型和导入模型对外没有区别，调用方只看 [CharacterPackage]，
 * 不需要知道它是从 assets 解出来的还是用户导入的。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
interface CharacterRepository {

    /**
     * 列出所有可用角色。首次调用会把内置模型从 assets 解到磁盘，因此可能耗时较久，
     * 调用方需要放在 IO 线程并给 loading 态。
     */
    suspend fun loadCharacters(): List<CharacterPackage>

    /**
     * 仅准备角色展示所需的 Live2D 文件，不安装共享声库。
     * 用于不允许初始化 TTS 或正式聊天链路的隔离演示。
     */
    suspend fun loadCharactersForDisplay(): List<CharacterPackage>

    /** 按稳定角色 ID 获取；不要传 displayName 或 storageKey。 */
    suspend fun getCharacter(id: String): CharacterPackage?

    suspend fun getCurrentCharacter(): CharacterPackage?

    suspend fun getCurrentCharacterForDisplay(): CharacterPackage?

    /** 保存稳定角色 ID，并返回实际选中的可用角色。 */
    suspend fun setCurrentCharacter(id: String): CharacterPackage?

    /**
     * 删除一个已导入的角色。内置角色不允许删除，会返回 false。
     */
    suspend fun delete(id: String): Boolean

    /**
     * 取角色的人物设定（system prompt）。内置角色在用户没改过时返回内置默认文案。
     */
    fun getSystemPrompt(storageKey: String): String?

    fun saveSystemPrompt(storageKey: String, prompt: String)

    /** 更新多人模型的 speaker id，写回该角色的 meta.json */
    suspend fun updateSpeakerId(id: String, speakerId: Int)
}
