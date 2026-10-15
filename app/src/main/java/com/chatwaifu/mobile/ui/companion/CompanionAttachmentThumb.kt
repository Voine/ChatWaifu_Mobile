package com.chatwaifu.mobile.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.chatwaifu.mobile.R

/**
 * 一个附件的方形缩略图。输入条的待发送区和历史面板共用，保证发之前和发之后长得一样。
 *
 * 有预览图的（图片 / 视频首帧）画图，视频再叠一个播放标和时长；
 * 没有预览的（音频 / PDF）画类型图标 + 标签。**不做播放 / 打开**：
 * 这里只是告诉用户「这一轮带了什么」，回放不是陪伴前台该承担的事。
 */
@Composable
fun AttachmentThumb(
    attachment: PendingAttachment,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 64.dp) 10.dp else 12.dp))
            .background(Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        val preview = attachment.previewPath
        if (preview != null) {
            AsyncImage(
                model = java.io.File(preview),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (attachment.type == AttachmentType.VIDEO) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = stringResource(R.string.companion_attachment_video),
                    modifier = Modifier.size(size * 0.36f),
                    tint = Color.White.copy(alpha = 0.88f),
                )
                attachment.durationMs?.let { DurationBadge(it, Modifier.align(Alignment.BottomEnd)) }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    attachment.type.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.36f),
                    tint = Color.White.copy(alpha = 0.76f),
                )
                Text(
                    text = attachment.durationMs
                        ?.takeIf { attachment.type == AttachmentType.AUDIO }
                        ?.let(::formatDuration)
                        ?: stringResource(attachment.type.label()),
                    color = Color.White.copy(alpha = 0.70f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun DurationBadge(durationMs: Long, modifier: Modifier) {
    Text(
        text = formatDuration(durationMs),
        modifier = modifier
            .padding(3.dp)
            .background(Color(0xAA000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp),
        color = Color.White,
        fontFamily = FontFamily.SansSerif,
        fontSize = 9.sp,
        lineHeight = 11.sp,
    )
}

private fun AttachmentType.icon(): ImageVector = when (this) {
    AttachmentType.IMAGE -> Icons.Outlined.Image
    AttachmentType.VIDEO -> Icons.Outlined.PlayCircle
    AttachmentType.AUDIO -> Icons.Outlined.Audiotrack
    AttachmentType.DOC -> Icons.Outlined.PictureAsPdf
}

private fun AttachmentType.label(): Int = when (this) {
    AttachmentType.IMAGE -> R.string.companion_attach_image
    AttachmentType.VIDEO -> R.string.companion_attachment_video
    AttachmentType.AUDIO -> R.string.companion_attachment_audio
    AttachmentType.DOC -> R.string.companion_attachment_doc
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
