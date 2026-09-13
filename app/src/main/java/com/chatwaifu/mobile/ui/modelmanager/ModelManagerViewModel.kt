package com.chatwaifu.mobile.ui.modelmanager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.ImportError
import com.chatwaifu.mobile.data.model.ImportProgress
import com.chatwaifu.mobile.data.model.ModelProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Description: 模型管理页的 ViewModel。
 * Author: Voine
 * Date: 2026/8/5
 */
class ModelManagerViewModel : ViewModel() {

    private val repository by lazy { ModelProvider.repository(ChatWaifuApplication.context) }
    private val importer by lazy { ModelProvider.importer(ChatWaifuApplication.context) }

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState = _uiState.asStateFlow()

    /** 一次性事件（toast），用 SharedFlow 避免旋转后重放 */
    private val _events = MutableSharedFlow<ModelManagerEvent>()
    val events = _events.asSharedFlow()
    private var setCurrentJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val characters = repository.loadCharacters()
            val current = repository.getCurrentCharacter()
            _uiState.update {
                it.copy(
                    loading = false,
                    characters = characters,
                    currentCharacterId = current?.id,
                    selectedCharacterId = it.selectedCharacterId
                        ?.takeIf { id -> characters.any { character -> character.id == id } },
                )
            }
        }
    }

    fun openDetail(id: String) {
        _uiState.update { it.copy(selectedCharacterId = id) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(selectedCharacterId = null) }
    }

    fun setCurrent(character: CharacterModel) {
        setCurrentJob?.cancel()
        setCurrentJob = viewModelScope.launch {
            val selected = repository.setCurrentCharacter(character.id) ?: return@launch
            _uiState.update {
                it.copy(
                    currentCharacterId = selected.id,
                    selectedCharacterId = selected.id,
                )
            }
            _events.emit(ModelManagerEvent.CurrentChanged(selected))
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            importer.import(uri).collect { progress ->
                when (progress) {
                    is ImportProgress.Extracting ->
                        _uiState.update { it.copy(importing = ImportingState.Extracting(progress.percent)) }

                    ImportProgress.Validating ->
                        _uiState.update { it.copy(importing = ImportingState.Validating) }

                    is ImportProgress.Success -> {
                        _uiState.update { it.copy(importing = null) }
                        _events.emit(ModelManagerEvent.ImportSucceeded(progress.model.displayName))
                        refresh()
                    }

                    is ImportProgress.Failed -> {
                        _uiState.update { it.copy(importing = null) }
                        _events.emit(ModelManagerEvent.ImportFailed(progress.error))
                    }
                }
            }
        }
    }

    fun delete(character: CharacterModel) {
        viewModelScope.launch {
            val deletingCurrent = _uiState.value.currentCharacterId == character.id
            if (repository.delete(character.id)) {
                val characters = repository.loadCharacters()
                val current = repository.getCurrentCharacter()
                _uiState.update {
                    it.copy(
                        characters = characters,
                        currentCharacterId = current?.id,
                        selectedCharacterId = null,
                    )
                }
                _events.emit(ModelManagerEvent.Deleted(character.displayName))
                if (deletingCurrent && current != null) {
                    _events.emit(ModelManagerEvent.CurrentChanged(current))
                }
            }
        }
    }

    fun saveCharacterConfig(character: CharacterModel, prompt: String, speakerId: Int) {
        viewModelScope.launch {
            repository.saveSystemPrompt(character.storageKey, prompt)
            if (speakerId != character.speakerId) {
                repository.updateSpeakerId(character.id, speakerId)
            }
            _events.emit(ModelManagerEvent.ConfigSaved)
            refresh()
        }
    }

    fun getSystemPrompt(storageKey: String): String? =
        repository.getSystemPrompt(storageKey)
}

data class ModelManagerUiState(
    val loading: Boolean = false,
    val characters: List<CharacterModel> = emptyList(),
    val currentCharacterId: String? = null,
    val selectedCharacterId: String? = null,
    /** 非 null 表示正在导入 */
    val importing: ImportingState? = null,
) {
    val currentCharacter: CharacterModel?
        get() = characters.firstOrNull { it.id == currentCharacterId }

    val selectedCharacter: CharacterModel?
        get() = characters.firstOrNull { it.id == selectedCharacterId }
}

sealed interface ImportingState {
    data class Extracting(val percent: Int) : ImportingState
    data object Validating : ImportingState
}

sealed interface ModelManagerEvent {
    data class ImportSucceeded(val name: String) : ModelManagerEvent
    data class ImportFailed(val error: ImportError) : ModelManagerEvent
    data class Deleted(val name: String) : ModelManagerEvent
    data class CurrentChanged(val character: CharacterModel) : ModelManagerEvent
    data object ConfigSaved : ModelManagerEvent
}
