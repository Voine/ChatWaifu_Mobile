package com.chatwaifu.mobile.utils

import com.chatwaifu.chat.core.ChatContent
import com.chatwaifu.log.AttachmentKind
import com.chatwaifu.log.AttachmentRef

/**
 * Description: 附件 → 模型可见内容的**能力降级**规则。纯函数，从 [ChatHistoryStore] 拆出来好单测。
 *
 * 为什么必须降级：各家 provider 碰到不支持的内容块是**直接抛** `CapabilityUnsupported` 的。
 * 不过滤的话，在 Gemini 下发过一段语音，换到 Claude 之后每一轮都会因为重放这段历史失败；
 * 视频的音轨在 Claude 下也会把整轮打挂。
 *
 * 规则：
 * - 收得了的照常转成内容块
 * - 收不了的、**用户直接给的**（音频 / PDF / 图）换成一句占位文字，让模型知道这里有过东西，
 *   不至于对着「你听听这个」一脸茫然；也保证只有附件的消息不会变成空消息被整条丢掉
 *   （那会打乱 user / assistant 交替）
 * - 收不了的**派生物**：抽帧被拒 = 整个视频看不到了，补一句视频占位；
 *   只有音轨被拒就静默跳过——帧还在，模型仍然「看过」这段视频
 * - 每种占位每条消息最多一句
 *
 * Author: Voine
 * Date: 2026/10/16
 */
object ModelVisibility {

    /**
     * @param visible 已经滤掉派生物父行的附件（见 `ChatHistoryStore.modelVisibleAttachments`）
     * @param toContent 收得了的附件怎么转成内容块（选内联还是 file id、文件丢了返回 null）
     */
    fun degrade(
        visible: List<AttachmentRef>,
        accepts: (AttachmentKind) -> Boolean,
        toContent: (AttachmentRef) -> ChatContent?,
    ): List<ChatContent> {
        val contents = mutableListOf<ChatContent>()
        val degraded = linkedSetOf<AttachmentKind>()
        for (ref in visible) {
            when {
                accepts(ref.kind) -> toContent(ref)?.let(contents::add)
                ref.sourceRelPath == null -> degraded += ref.kind
                // 派生物目前只有视频产出（帧 + 音轨）
                ref.kind == AttachmentKind.IMAGE -> degraded += AttachmentKind.VIDEO
            }
        }
        degraded.forEach { contents += ChatContent.Text(placeholderOf(it)) }
        return contents
    }

    fun placeholderOf(kind: AttachmentKind): String = when (kind) {
        AttachmentKind.IMAGE -> "（这里附了图片，当前模型看不到）"
        AttachmentKind.AUDIO -> "（这里附了一段音频，当前模型听不到）"
        AttachmentKind.VIDEO -> "（这里附了一段视频，当前模型看不到）"
        AttachmentKind.DOC -> "（这里附了一份 PDF，当前模型读不了）"
    }
}
