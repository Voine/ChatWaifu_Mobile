package com.chatwaifu.mobile.utils

import com.chatwaifu.chat.core.ChatContent
import com.chatwaifu.chat.core.MediaSource
import com.chatwaifu.log.AttachmentKind
import com.chatwaifu.log.AttachmentRef
import org.junit.Assert.assertEquals
import org.junit.Test

/** 换到收不了某类附件的模型后，历史重放不能再把那类块发出去（各家会直接抛错）。 */
class ModelVisibilityTest {

    private fun ref(kind: AttachmentKind, path: String, source: String? = null, pos: Long? = null) =
        AttachmentRef(kind = kind, relPath = path, mime = "x/y", sourceRelPath = source, posMs = pos)

    private val toContent: (AttachmentRef) -> ChatContent = {
        ChatContent.Image(MediaSource.LocalPath("/abs/${it.relPath}", it.mime))
    }

    private fun only(vararg kinds: AttachmentKind): (AttachmentKind) -> Boolean = { it in kinds }

    @Test
    fun `收得了的照常转换`() {
        val out = ModelVisibility.degrade(
            listOf(ref(AttachmentKind.IMAGE, "a.jpg"), ref(AttachmentKind.DOC, "b.pdf")),
            only(AttachmentKind.IMAGE, AttachmentKind.DOC),
            toContent,
        )
        assertEquals(2, out.size)
        assertEquals(0, out.count { it is ChatContent.Text })
    }

    @Test
    fun `收不了的用户附件换成占位文字，每种只说一次`() {
        val out = ModelVisibility.degrade(
            listOf(
                ref(AttachmentKind.AUDIO, "a.wav"),
                ref(AttachmentKind.AUDIO, "b.wav"),
                ref(AttachmentKind.IMAGE, "c.jpg"),
            ),
            only(AttachmentKind.IMAGE),
            toContent,
        )
        assertEquals(1, out.count { it is ChatContent.Image })
        assertEquals(
            listOf(ModelVisibility.placeholderOf(AttachmentKind.AUDIO)),
            out.filterIsInstance<ChatContent.Text>().map { it.text },
        )
    }

    @Test
    fun `视频在只看图的模型下：帧照发，音轨静默跳过`() {
        val out = ModelVisibility.degrade(
            listOf(
                ref(AttachmentKind.IMAGE, "f0.jpg", source = "v.mp4", pos = 0),
                ref(AttachmentKind.IMAGE, "f1.jpg", source = "v.mp4", pos = 2000),
                ref(AttachmentKind.AUDIO, "v.wav", source = "v.mp4"),
            ),
            only(AttachmentKind.IMAGE),
            toContent,
        )
        assertEquals(2, out.size)
        assertEquals(0, out.count { it is ChatContent.Text })
    }

    @Test
    fun `视频在不能看图的模型下：整个视频退成一句占位，不是凭空消失`() {
        val out = ModelVisibility.degrade(
            listOf(
                ref(AttachmentKind.IMAGE, "f0.jpg", source = "v.mp4", pos = 0),
                ref(AttachmentKind.IMAGE, "f1.jpg", source = "v.mp4", pos = 2000),
                ref(AttachmentKind.AUDIO, "v.wav", source = "v.mp4"),
            ),
            only(),
            toContent,
        )
        assertEquals(
            listOf(ModelVisibility.placeholderOf(AttachmentKind.VIDEO)),
            out.filterIsInstance<ChatContent.Text>().map { it.text },
        )
        assertEquals(1, out.size)
    }

    @Test
    fun `文件丢了的附件不发也不占位`() {
        val out = ModelVisibility.degrade(
            listOf(ref(AttachmentKind.IMAGE, "gone.jpg")),
            only(AttachmentKind.IMAGE),
        ) { null }
        assertEquals(0, out.size)
    }
}
