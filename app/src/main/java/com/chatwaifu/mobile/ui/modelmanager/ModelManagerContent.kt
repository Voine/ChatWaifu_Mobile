package com.chatwaifu.mobile.ui.modelmanager

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.ModelSource
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import com.chatwaifu.mobile.ui.common.avatarResOf
import com.chatwaifu.mobile.ui.setting.SettingEditText

/**
 * Description: 模型管理页。列出已装角色，导入 / 删除 / 按角色配设定和 speaker id。
 *
 * 设定和 speaker id 以前是全局一份（所有外部模型共用），放在 Setting 页；
 * 现在按角色存在各自的 meta.json / pref key 里，所以挪到这里。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerContent(
    uiState: ModelManagerUiState,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onDelete: (CharacterModel) -> Unit = {},
    onSaveConfig: (CharacterModel, String, Int) -> Unit = { _, _, _ -> },
    systemPromptOf: (String) -> String? = { null },
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    var pendingDelete by remember { mutableStateOf<CharacterModel?>(null) }

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = stringResource(id = R.string.model_manager_title),
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                externalActions = {
                    IconButton(
                        onClick = onImportClick,
                        // 导入进行中不允许再点，避免并发写同一个暂存目录
                        enabled = uiState.importing == null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(id = R.string.model_manager_import),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.navigationBars)
            .exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            uiState.importing?.let { ImportingBanner(it) }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = stringResource(id = R.string.model_manager_hint_layout),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(uiState.characters, key = { it.name }) { character ->
                    Divider(
                        modifier = Modifier.height(1.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                    ModelManagerItem(
                        character = character,
                        initialPrompt = systemPromptOf(character.name),
                        onDeleteClick = { pendingDelete = character },
                        onSaveConfig = { prompt, speakerId ->
                            onSaveConfig(character, prompt, speakerId)
                        },
                    )
                }
                if (uiState.characters.none { it.source == ModelSource.IMPORTED } && !uiState.loading) {
                    item {
                        Text(
                            text = stringResource(id = R.string.model_manager_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(id = R.string.model_manager_delete)) },
            text = {
                Text(stringResource(id = R.string.model_manager_delete_confirm, target.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(target)
                }) { Text(stringResource(id = R.string.model_manager_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ImportingBanner(state: ImportingState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val text = when (state) {
            is ImportingState.Extracting ->
                stringResource(id = R.string.model_manager_importing, state.percent)

            ImportingState.Validating -> stringResource(id = R.string.model_manager_validating)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        when (state) {
            is ImportingState.Extracting -> LinearProgressIndicator(
                progress = state.percent / 100f,
                modifier = Modifier.fillMaxWidth(),
            )

            ImportingState.Validating -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ModelManagerItem(
    character: CharacterModel,
    initialPrompt: String?,
    onDeleteClick: () -> Unit,
    onSaveConfig: (String, Int) -> Unit,
) {
    var expanded by rememberSaveable(character.name) { mutableStateOf(false) }
    var prompt by rememberSaveable(character.name) { mutableStateOf(initialPrompt.orEmpty()) }
    var speakerId by rememberSaveable(character.name) { mutableStateOf(character.speakerId) }
    val isImported = character.source == ModelSource.IMPORTED

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = avatarResOf(character.name)),
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val subtitle = buildString {
                    append(
                        stringResource(
                            id = if (isImported) R.string.model_manager_imported
                            else R.string.model_manager_built_in
                        )
                    )
                    if (!character.hasVoice) {
                        append(" · ")
                        append(stringResource(id = R.string.model_manager_no_voice))
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "▲" else "▼")
            }
            // 内置模型删了下次启动还会解出来，所以不给删除入口
            if (isImported) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(id = R.string.model_manager_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.model_manager_prompt_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingEditText(
                    initValue = prompt,
                    hint = stringResource(id = R.string.model_manager_prompt_hint),
                ) { prompt = it }

                if (character.hasVoice) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.model_manager_speaker_id_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingEditText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        initValue = speakerId.toString(),
                        hint = "0",
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                    ) { speakerId = it.toIntOrNull() ?: 0 }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onSaveConfig(prompt, speakerId) }) {
                        Text(stringResource(id = R.string.model_manager_save))
                    }
                }
            }
        }
    }
}
