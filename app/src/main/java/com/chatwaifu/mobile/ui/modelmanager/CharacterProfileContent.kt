package com.chatwaifu.mobile.ui.modelmanager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.model.profile.VoiceSourceType
import com.chatwaifu.vits.utils.SoundGenerateHelper

/**
 * Description: 人格页与语音页。
 *
 * 延续 Companion 的冷色半透明视觉语言（[ProfileGlass] / [ProfileAccent] 和
 * `ModelManagerContent` 里的那组常量同色），系统文字统一 [FontFamily.SansSerif]。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
private val ProfileGlass = Color(0xB51A2A43)
private val ProfileGlassBorder = Color(0x526FA8D8)
private val ProfileAccent = Color(0xFF8CBFFF)

/**
 * 两个 Profile 页的回调集合。
 *
 * 打包成一个对象而不是十来个独立参数：`ModelManagerContent` 的参数列表已经很长，
 * 而这些回调的生命周期完全一致（都由 [ModelManagerViewModel] 提供）。
 */
data class CharacterProfileActions(
    val openPersona: () -> Unit = {},
    val openVoice: () -> Unit = {},
    /** 请求离开当前 profile 页；有未保存改动时由 ViewModel 弹确认 */
    val back: () -> Unit = {},
    val dismissDiscard: () -> Unit = {},
    val discardAndLeave: () -> Unit = {},
    val editPersonaName: (String) -> Unit = {},
    val editPersonaPrompt: (String) -> Unit = {},
    val savePersona: () -> Unit = {},
    val resetPersona: () -> Unit = {},
    val editVoiceName: (String) -> Unit = {},
    val selectSpeaker: (Int) -> Unit = {},
    val saveVoice: () -> Unit = {},
    val togglePreview: () -> Unit = {},
)

@Composable
internal fun PersonaContent(
    state: PersonaEditorState,
    confirmDiscard: Boolean,
    actions: CharacterProfileActions,
) {
    ProfileScaffold(
        title = stringResource(R.string.persona_title),
        // 未保存确认由 ViewModel 判定 —— 返回键和这个箭头必须走同一条路，
        // 只在 Composable 里挡会漏掉返回键
        onBack = actions.back,
    ) {
        FieldLabel(stringResource(R.string.persona_name_label))
        OutlinedTextField(
            value = state.name,
            onValueChange = actions.editPersonaName,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(6.dp))
        FieldLabel(stringResource(R.string.persona_prompt_label))
        OutlinedTextField(
            value = state.prompt,
            onValueChange = actions.editPersonaPrompt,
            placeholder = {
                Text(
                    stringResource(R.string.persona_prompt_hint),
                    fontFamily = FontFamily.SansSerif,
                )
            },
            // 多行编辑区给一个下限高度再让它自然增长，内部由 OutlinedTextField
            // 自己滚动；外层 Column 也可滚，键盘弹出时不会把保存按钮顶出屏幕
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )

        Text(
            stringResource(R.string.persona_note),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            fontFamily = FontFamily.SansSerif,
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = actions.savePersona,
            enabled = state.dirty && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.persona_save))
        }
        if (state.hasDefault) {
            TextButton(
                onClick = actions.resetPersona,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.persona_reset))
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = actions.dismissDiscard,
            title = { Text(stringResource(R.string.persona_discard_title)) },
            text = { Text(stringResource(R.string.persona_discard_message)) },
            confirmButton = {
                TextButton(onClick = actions.discardAndLeave) {
                    Text(stringResource(R.string.persona_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.dismissDiscard) {
                    Text(stringResource(R.string.persona_keep_editing))
                }
            },
        )
    }
}

@Composable
internal fun VoiceContent(
    state: VoiceEditorState,
    actions: CharacterProfileActions,
) {
    ProfileScaffold(
        title = stringResource(R.string.voice_title),
        onBack = actions.back,
    ) {
        FieldLabel(stringResource(R.string.voice_current_label))
        OutlinedTextField(
            value = state.name,
            onValueChange = actions.editVoiceName,
            singleLine = true,
            enabled = state.voiceAvailable,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!state.voiceAvailable) {
            InfoText(stringResource(R.string.voice_unavailable))
            return@ProfileScaffold
        }

        ReadOnlyRow(
            stringResource(R.string.voice_language_label),
            stringResource(languageLabelOf(state.profile.language)),
        )
        ReadOnlyRow(
            stringResource(R.string.voice_source_label),
            stringResource(
                when (state.profile.sourceType) {
                    VoiceSourceType.SHARED_BUILT_IN -> R.string.voice_source_shared
                    VoiceSourceType.BUNDLED_IMPORTED -> R.string.voice_source_bundled
                    VoiceSourceType.NONE -> R.string.voice_source_none
                }
            ),
        )

        Spacer(Modifier.height(6.dp))
        FieldLabel(stringResource(R.string.voice_speaker_label))
        when {
            // 多 speaker：可选。列表来自声库 config.json 的 spk2id
            state.speakerEditable -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.speakers.forEach { speaker ->
                    SpeakerRow(
                        label = "${speaker.id} / ${speaker.name}",
                        selected = speaker.id == state.speakerId,
                        onClick = { actions.selectSpeaker(speaker.id) },
                    )
                }
            }

            // 单 speaker 或读不到列表：只读显示，不给一个会写坏 meta.json 的输入框
            else -> {
                ReadOnlyRow(
                    stringResource(R.string.voice_speaker_label),
                    listOfNotNull(state.speakerId?.toString(), state.speakerName)
                        .joinToString(" / "),
                )
                InfoText(
                    stringResource(
                        if (state.speakers.isEmpty()) R.string.voice_speaker_unknown
                        else R.string.voice_speaker_single
                    )
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        FieldLabel(stringResource(R.string.voice_preview))
        Button(
            onClick = actions.togglePreview,
            enabled = state.previewEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (state.previewing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (state.previewing) R.string.voice_preview_stop
                    else R.string.voice_preview_play
                )
            )
        }
        if (!state.isCurrentCharacter) {
            // BV2 是进程级单模型，给非当前角色试听要重载模型，见
            // ChatActivityViewModel.previewVoice
            InfoText(stringResource(R.string.character_profile_only_current))
        }

        AdvancedSection()

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = actions.saveVoice,
            enabled = state.dirty && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.voice_save))
        }
    }
}

/** 高级项只说明「当前引擎没有可调参数」，不摆放无效开关。 */
@Composable
private fun AdvancedSection() {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.voice_advanced),
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.72f),
                fontFamily = FontFamily.SansSerif,
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = ProfileAccent.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(expanded) {
            InfoText(stringResource(R.string.voice_advanced_empty))
        }
    }
}

@Composable
private fun ProfileScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.character_back),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            color = ProfileGlass,
            border = BorderStroke(1.dp, ProfileGlassBorder),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.62f),
        fontFamily = FontFamily.SansSerif,
    )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.58f),
            fontFamily = FontFamily.SansSerif,
        )
        Text(value, color = Color.White, fontFamily = FontFamily.SansSerif)
    }
}

@Composable
private fun InfoText(text: String) {
    Box(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            fontFamily = FontFamily.SansSerif,
        )
    }
}

@Composable
private fun SpeakerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) ProfileAccent.copy(alpha = 0.22f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) ProfileAccent else ProfileGlassBorder,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
            fontFamily = FontFamily.SansSerif,
        )
    }
}

/** `LANGUAGE_*` → 文案。映射只在这里有一份，和 BV2 的目录名映射分开。 */
internal fun languageLabelOf(language: Int?): Int = when (language) {
    null -> R.string.voice_language_unknown
    else -> when (SoundGenerateHelper.languageDirName(language)) {
        "jp" -> R.string.voice_language_jp
        "zh" -> R.string.voice_language_zh
        "en" -> R.string.voice_language_en
        "mix" -> R.string.voice_language_mix
        else -> R.string.voice_language_unknown
    }
}
