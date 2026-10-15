package com.chatwaifu.log

/**
 * Description: 一条长期记忆事实。L2 层，见 docs/memory.md。
 *
 * **和 [ChatLogEntry] 的关系是「派生」不是「从属」**：一条事实是从某轮对话里抽出来的，
 * 但它一旦成立就独立存在了。所以 [sourceMessageId] 是 `ON DELETE SET NULL` 而不是
 * `CASCADE`——删掉三个月前的一条聊天记录，不该让「他养了只猫叫豆豆」跟着消失。
 * 附件那张表是反过来的（附件是消息的一部分，消息没了字节也该没）。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
data class MemoryFact(
    val id: Long = NO_ID,

    /** 角色隔离的 scope 键。记忆**不跨角色共享**，见 docs/memory.md 4.1 */
    val characterId: String,

    /**
     * 语义槽位键，比如「用户的名字」「用户所在城市」。
     *
     * `(characterId, slot)` 上有 UNIQUE 索引，**upsert 语义是数据库层强制的**，
     * 不是指望抽取器自觉。没有它，事实库会长出「所在城市=北京」和「所在城市=上海」
     * 两行共存，然后模型开始精分。
     */
    val slot: String,

    /** 事实内容本身，注入 prompt 时直接用 */
    val content: String,

    /** 0-100，只用于超预算时排序，不参与检索 */
    val importance: Int = 0,

    /**
     * 用户手动固定。**抽取器的删除对它无效**——这是模型判断失误时用户的兜底手段。
     * 注意只保护删除，内容仍然会被 upsert 更新（否则就成了永远不会变的死数据）。
     */
    val pinned: Boolean = false,

    /** 来源消息，可溯源。消息被删时置 null，不影响本行 */
    val sourceMessageId: Long? = null,

    /** 这条记的是谁 / 什么，决定注入时进哪一节。见 [MemorySubject] */
    val subject: MemorySubject = MemorySubject.USER,

    /**
     * 只对 [MemorySubject.PROMISE] 有意义：大致到期时间（epoch ms），null = 没说具体哪天。
     * 到期前后注入时会被标成「该提起了」
     */
    val dueAt: Long? = null,

    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val NO_ID = 0L

        /** slot 的长度上限。模型偶尔会吐出一整句话当 slot，截断以免把索引撑坏 */
        const val MAX_SLOT_LENGTH = 64
    }
}

/**
 * 事实的三类主体。共用一张表、一套 upsert / pin / 删除规则，只在注入时分节。
 *
 * 存 `name` 不存 ordinal（工程约定，见 ChatMessageEntity）。
 */
enum class MemorySubject {
    /** 关于对方：名字、职业、宠物、喜恶。L2 最初就只有这一类 */
    USER,

    /**
     * 关于「我」（角色）自己在聊天里即兴定下的设定：「我最喜欢下雨天」「我不会做饭」。
     * 不记的话下次被问到可能给出不一样的答案，人设就漂了。
     * 和人设 prompt 冲突时以人设为准（注入时有说明）
     */
    SELF,

    /**
     * 双方的约定和待跟进：「入职那天我给对方唱歌」「问问豆豆喜不喜欢新罐头」。
     * 完成或者失效后由抽取器删掉（受单批删除上限约束）
     */
    PROMISE,
}
