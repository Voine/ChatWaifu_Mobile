package com.chatwaifu.mobile.data.model

/**
 * 隔离快速切角色时迟到的历史、语音初始化等异步结果。
 * token 只在进程内有效；持久身份仍由 [CharacterPackage.id] 负责。
 */
internal class CharacterSwitchGuard {
    private var generation = 0L
    private var characterId: String? = null

    @Synchronized
    fun begin(id: String): Long {
        characterId = id
        return ++generation
    }

    @Synchronized
    fun currentToken(id: String): Long? =
        generation.takeIf { id == characterId }

    @Synchronized
    fun isCurrent(token: Long, id: String): Boolean =
        token == generation && id == characterId
}
