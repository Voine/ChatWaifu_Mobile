package com.chatwaifu.mobile.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chatwaifu.log.MemoryEpisode
import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemorySubject
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.memory.MemoryTime
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import com.chatwaifu.mobile.ui.setting.SettingEditText

/**
 * Description: 记忆页。两段：L2 事实（可编辑 / 删除 / 固定 / 手动新增）和
 * L1 日记（可改摘要 / 删除，不能手动新增——日记是从聊天里写出来的）。
 *
 * 这一页同时是**记忆系统唯一的可观测面**：抽取记错了、记重了、该忘的没忘，
 * 只能在这里看出来。见 docs/memory.md 6.6。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryContent(
    uiState: MemoryUiState,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = {},
    onUpdateContent: (MemoryFact, String) -> Unit = { _, _ -> },
    onDelete: (MemoryFact) -> Unit = {},
    onTogglePinned: (MemoryFact) -> Unit = {},
    onAdd: (String, String) -> Unit = { _, _ -> },
    onUpdateEpisode: (MemoryEpisode, String) -> Unit = { _, _ -> },
    onDeleteEpisode: (MemoryEpisode) -> Unit = {},
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    var pendingDelete by remember { mutableStateOf<MemoryFact?>(null) }
    var pendingEpisodeDelete by remember { mutableStateOf<MemoryEpisode?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = uiState.characterName.ifEmpty {
                    stringResource(id = R.string.memory_title)
                },
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                externalActions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(id = R.string.memory_add),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        // 同 ModelManagerContent：底部没有输入框，导航栏 inset 交给 Scaffold 补
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.facts.isEmpty() && uiState.episodes.isEmpty()) {
                EmptyHint()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    FACT_SECTIONS.forEach { (subject, title) ->
                        val group = uiState.facts.filter { it.subject == subject }
                        if (group.isNotEmpty()) {
                            item(key = "facts_header_${subject.name}") {
                                SectionHeader(stringResource(id = title))
                            }
                        }
                        items(group, key = { "fact_${it.id}" }) { fact ->
                            FactRow(
                                fact = fact,
                                onUpdateContent = { onUpdateContent(fact, it) },
                                onDelete = { pendingDelete = fact },
                                onTogglePinned = { onTogglePinned(fact) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (uiState.episodes.isNotEmpty()) {
                        item(key = "diary_header") {
                            SectionHeader(stringResource(id = R.string.memory_section_diary))
                        }
                    }
                    items(uiState.episodes, key = { "episode_${it.id}" }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            onUpdateSummary = { onUpdateEpisode(episode, it) },
                            onDelete = { pendingEpisodeDelete = episode },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { fact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(id = R.string.memory_delete_title)) },
            text = { Text(stringResource(id = R.string.memory_delete_message, fact.slot)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(fact)
                    pendingDelete = null
                }) { Text(stringResource(id = R.string.memory_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(id = R.string.memory_cancel))
                }
            },
        )
    }

    pendingEpisodeDelete?.let { episode ->
        AlertDialog(
            onDismissRequest = { pendingEpisodeDelete = null },
            title = { Text(stringResource(id = R.string.memory_delete_title)) },
            text = {
                Text(
                    stringResource(
                        id = R.string.memory_episode_delete_message,
                        MemoryTime.dayLabel(episode.endedAt),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEpisode(episode)
                    pendingEpisodeDelete = null
                }) { Text(stringResource(id = R.string.memory_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingEpisodeDelete = null }) {
                    Text(stringResource(id = R.string.memory_cancel))
                }
            },
        )
    }

    if (showAdd) {
        AddFactDialog(
            onDismiss = { showAdd = false },
            onConfirm = { slot, content ->
                onAdd(slot, content)
                showAdd = false
            },
        )
    }
}

@Composable
private fun FactRow(
    fact: MemoryFact,
    onUpdateContent: (String) -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fact.dueAt?.let {
                    fact.slot + " · " + stringResource(id = R.string.memory_due, MemoryTime.dayLabel(it))
                } ?: fact.slot,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onTogglePinned) {
                Icon(
                    imageVector = if (fact.pinned) {
                        Icons.Filled.PushPin
                    } else {
                        Icons.Outlined.PushPin
                    },
                    contentDescription = stringResource(id = R.string.memory_pin),
                    tint = if (fact.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(id = R.string.memory_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // key 带上 updatedAt：抽取在后台改写这一行时让输入框重建去读新值。
        // SettingEditText 内部用 rememberSaveable 存草稿，不加 key 的话会一直显示
        // 用户打开页面那一刻的旧内容
        key(fact.updatedAt) {
            SettingEditText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                initValue = fact.content,
                hint = stringResource(id = R.string.memory_content_hint),
                onValueChanged = onUpdateContent,
            )
        }
    }
}

/** 记忆页的分节顺序，和注入 system 区的顺序一致 */
private val FACT_SECTIONS = listOf(
    MemorySubject.USER to R.string.memory_section_facts,
    MemorySubject.PROMISE to R.string.memory_section_promises,
    MemorySubject.SELF to R.string.memory_section_self,
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EpisodeRow(
    episode: MemoryEpisode,
    onUpdateSummary: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // 编号和模型看到的「回忆#12」一致，排查「她为什么想起这件事」时能对上
                text = stringResource(
                    id = R.string.memory_episode_title,
                    MemoryTime.dayLabel(episode.endedAt),
                    episode.id,
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(id = R.string.memory_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        key(episode.updatedAt) {
            SettingEditText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                initValue = episode.summary,
                hint = stringResource(id = R.string.memory_episode_hint),
                onValueChanged = onUpdateSummary,
            )
        }
        if (episode.keywordList.isNotEmpty()) {
            Text(
                text = episode.keywordList.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AddFactDialog(
    onDismiss: () -> Unit,
    onConfirm: (slot: String, content: String) -> Unit,
) {
    var slot by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.memory_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingEditText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    hint = stringResource(id = R.string.memory_slot_hint),
                    singleLine = true,
                    onValueChanged = { slot = it },
                )
                SettingEditText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    hint = stringResource(id = R.string.memory_content_hint),
                    onValueChanged = { content = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(slot, content) },
                enabled = slot.isNotBlank() && content.isNotBlank(),
            ) { Text(stringResource(id = R.string.memory_add_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.memory_cancel))
            }
        },
    )
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.memory_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
