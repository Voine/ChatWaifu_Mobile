package com.chatwaifu.mobile.ui.modelmanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.model.CharacterAvailability
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.ModelSource
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import com.chatwaifu.mobile.ui.common.avatarResOf

private val CharacterGlass = Color(0xB51A2A43)
private val CharacterGlassBorder = Color(0x526FA8D8)
private val CharacterAccent = Color(0xFF8CBFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerContent(
    uiState: ModelManagerUiState,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onSetCurrent: (CharacterModel) -> Unit = {},
    onDelete: (CharacterModel) -> Unit = {},
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = stringResource(R.string.character_title),
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                externalActions = {
                    IconButton(
                        onClick = onImportClick,
                        enabled = uiState.importing == null,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.character_import),
                        )
                    }
                },
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val selected = uiState.selectedCharacter
            if (selected == null) {
                CharacterGrid(
                    uiState = uiState,
                    onImportClick = onImportClick,
                    onOpenDetail = onOpenDetail,
                )
            } else {
                CharacterDetail(
                    character = selected,
                    isCurrent = selected.id == uiState.currentCharacterId,
                    onBack = onCloseDetail,
                    onSetCurrent = { onSetCurrent(selected) },
                    onDelete = { onDelete(selected) },
                )
            }
            if (uiState.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun CharacterGrid(
    uiState: ModelManagerUiState,
    onImportClick: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        uiState.importing?.let { importing ->
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                ImportingBanner(importing)
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.character_current))
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            val current = uiState.currentCharacter
            if (current == null) {
                EmptyCurrentCard()
            } else {
                CurrentCharacterCard(current) { onOpenDetail(current.id) }
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.character_mine))
        }
        items(uiState.characters, key = { it.id }) { character ->
            CharacterCard(
                character = character,
                selected = character.id == uiState.currentCharacterId,
                onClick = { onOpenDetail(character.id) },
            )
        }
        item {
            ImportCharacterCard(
                enabled = uiState.importing == null,
                onClick = onImportClick,
            )
        }
    }
}

@Composable
private fun CurrentCharacterCard(character: CharacterModel, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clickable(onClick = onClick),
        color = CharacterGlass,
        border = BorderStroke(1.dp, CharacterAccent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CharacterPreview(character, Modifier.size(148.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    character.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.character_current_using),
                    color = CharacterAccent,
                    fontFamily = FontFamily.SansSerif,
                )
                SourceLabel(character)
            }
        }
    }
}

@Composable
private fun EmptyCurrentCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        color = CharacterGlass,
        border = BorderStroke(1.dp, CharacterGlassBorder),
        shape = RoundedCornerShape(22.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.character_no_available),
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) CharacterAccent.copy(alpha = 0.22f) else CharacterGlass,
        border = BorderStroke(
            1.dp,
            if (selected) CharacterAccent else CharacterGlassBorder,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CharacterPreview(
                character,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Text(
                character.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
            )
            SourceLabel(character)
            if (selected) {
                Text(
                    stringResource(R.string.character_selected),
                    color = CharacterAccent,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}

@Composable
private fun ImportCharacterCard(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clickable(enabled = enabled, onClick = onClick),
        color = CharacterGlass.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, CharacterGlassBorder),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = CharacterAccent)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.character_import),
                color = Color.White.copy(alpha = if (enabled) 0.82f else 0.38f),
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}

@Composable
private fun CharacterDetail(
    character: CharacterModel,
    isCurrent: Boolean,
    onBack: () -> Unit,
    onSetCurrent: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember(character.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.character_back),
                )
            }
            Text(
                character.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f),
            color = CharacterGlass,
            border = BorderStroke(1.dp, CharacterGlassBorder),
            shape = RoundedCornerShape(26.dp),
        ) {
            Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                CharacterPreview(character, Modifier.fillMaxSize())
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharacterGlass,
            border = BorderStroke(1.dp, CharacterGlassBorder),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailRow(
                    stringResource(R.string.character_appearance),
                    character.displayName,
                )
                DetailRow(
                    stringResource(R.string.character_source),
                    stringResource(
                        if (character.source == ModelSource.BUILT_IN) {
                            R.string.character_builtin
                        } else {
                            R.string.character_imported
                        }
                    ),
                )
                DetailRow(
                    stringResource(R.string.character_voice),
                    stringResource(
                        if (character.hasVoice) R.string.character_default
                        else R.string.character_not_configured
                    ),
                )
                DetailRow(
                    stringResource(R.string.character_persona),
                    stringResource(
                        if (character.personaProfileId != null) R.string.character_configured
                        else R.string.character_default
                    ),
                )
                DetailRow(
                    stringResource(R.string.character_behavior),
                    stringResource(R.string.character_not_configured),
                )
                if (character.availability != CharacterAvailability.AVAILABLE) {
                    Text(
                        stringResource(R.string.character_resource_missing),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.SansSerif,
                    )
                }
            }
        }
        Button(
            onClick = onSetCurrent,
            enabled = character.availability == CharacterAvailability.AVAILABLE,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isCurrent) stringResource(R.string.character_enter_chat)
                else stringResource(R.string.character_set_current)
            )
        }
        if (character.source == ModelSource.IMPORTED) {
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(stringResource(R.string.model_manager_delete))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.model_manager_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.model_manager_delete_confirm,
                        character.displayName,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.model_manager_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CharacterPreview(character: CharacterModel, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(avatarResOf(character.storageKey)),
        contentDescription = character.displayName,
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun SourceLabel(character: CharacterModel) {
    val label = when {
        character.availability != CharacterAvailability.AVAILABLE ->
            stringResource(R.string.character_unavailable)
        character.source == ModelSource.BUILT_IN ->
            stringResource(R.string.character_builtin)
        else -> stringResource(R.string.character_imported)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.58f),
        fontFamily = FontFamily.SansSerif,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = FontFamily.SansSerif,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ImportingBanner(state: ImportingState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            when (state) {
                is ImportingState.Extracting ->
                    stringResource(R.string.model_manager_importing, state.percent)
                ImportingState.Validating ->
                    stringResource(R.string.model_manager_validating)
            },
            fontFamily = FontFamily.SansSerif,
        )
        Spacer(Modifier.height(6.dp))
        if (state is ImportingState.Extracting) {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
