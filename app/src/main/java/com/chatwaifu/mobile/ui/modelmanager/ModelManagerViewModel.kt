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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Description: 模型管理页的 ViewModel。
 * Author: Voine
 * Date: 2026/8/5
 */
class ModelManagerViewModel : ViewModel() {

    private val repository by lazy { ModelProvider.repository(ChatWaifuApplication.context) }
    private val importer by lazy { ModelProvider.importer(ChatWaifuApplication.context) }
    private val profiles by lazy { ModelProvider.profiles(ChatWaifuApplication.context) }

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState = _uiState.asStateFlow()

    /** 一次性事件（toast），用 SharedFlow 避免旋转后重放 */
    private val _events = MutableSharedFlow<ModelManagerEvent>()
    val events = _events.asSharedFlow()

    /**
     * Persona / Voice 的事件走独立的流。
     *
     * 和 [events] 分开是因为消费者不同：[events] 由宿主 Fragment 消费（导入、删除、
     * 切当前角色），profile 这几个由 `rememberCharacterProfileActions` 统一消费，
     * 混在一条流里会逼着两个宿主各写一遍 when 分支。
     */
    private val _profileEvents = MutableSharedFlow<CharacterProfileEvent>()
    val profileEvents = _profileEvents.asSharedFlow()
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
        _uiState.update {
            it.copy(
                selectedCharacterId = id,
                detailRoute = CharacterDetailRoute.DETAIL,
                personaEditor = null,
                voiceEditor = null,
                confirmDiscardPersona = false,
                profileSummary = it.characters.firstOrNull { c -> c.id == id }
                    ?.let(::summaryOf),
            )
        }
    }

    fun closeDetail() {
        stopPreview()
        _uiState.update {
            it.copy(
                selectedCharacterId = null,
                detailRoute = CharacterDetailRoute.DETAIL,
                personaEditor = null,
                voiceEditor = null,
                confirmDiscardPersona = false,
                profileSummary = null,
            )
        }
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

    // ---- Persona / Voice profile ----

    /**
     * 进人格页。**每次进入都重新解析 profile**，不用上次留下的草稿 ——
     * 设置页可能在别处（Setting 页那三个内置编辑框）被改过。
     */
    fun openPersona() {
        val character = _uiState.value.selectedCharacter ?: return
        _uiState.update {
            it.copy(
                detailRoute = CharacterDetailRoute.PERSONA,
                personaEditor = PersonaEditorState.of(
                    profile = profiles.personaProfile(character),
                    hasDefault = profiles.hasDefaultPersona(character),
                ),
            )
        }
    }

    fun openVoice() {
        val character = _uiState.value.selectedCharacter ?: return
        viewModelScope.launch {
            // availableSpeakers 读的是 config.json，放到 IO 上
            val speakers = withContext(Dispatchers.IO) { profiles.availableSpeakers(character) }
            val profile = profiles.voiceProfile(character)
            _uiState.update {
                it.copy(
                    detailRoute = CharacterDetailRoute.VOICE,
                    voiceEditor = VoiceEditorState.of(
                        profile = profile,
                        speakers = speakers,
                        isCurrentCharacter = character.id == it.currentCharacterId,
                    ),
                )
            }
        }
    }

    /**
     * 请求离开当前 profile 页。
     *
     * **未保存确认必须在这里判**，不能只在 Compose 里：返回键走的是
     * `rememberCharacterProfileActions` 里的 BackHandler，它不经过页面内的局部状态，
     * 只在 Composable 里挡会漏掉整条返回键路径（实测会直接丢掉未保存的改动）。
     */
    fun requestLeaveProfile() {
        val state = _uiState.value
        if (state.detailRoute == CharacterDetailRoute.PERSONA &&
            state.personaEditor?.dirty == true
        ) {
            _uiState.update { it.copy(confirmDiscardPersona = true) }
            return
        }
        backToDetail()
    }

    fun dismissDiscardPrompt() {
        _uiState.update { it.copy(confirmDiscardPersona = false) }
    }

    /** 用户确认放弃未保存的改动。 */
    fun discardAndLeave() {
        _uiState.update { it.copy(confirmDiscardPersona = false) }
        backToDetail()
    }

    /** 回到角色信息。离开语音页时必须停试听，否则声音会跟着用户走到别的页面。 */
    fun backToDetail() {
        if (_uiState.value.detailRoute == CharacterDetailRoute.VOICE) stopPreview()
        _uiState.update {
            it.copy(
                detailRoute = CharacterDetailRoute.DETAIL,
                personaEditor = null,
                voiceEditor = null,
                confirmDiscardPersona = false,
                // 回来时重算摘要，人格/语音页刚保存的名字要立刻反映在详情页上
                profileSummary = it.selectedCharacter?.let(::summaryOf),
            )
        }
    }

    fun editPersonaName(value: String) = updatePersona { it.copy(name = value) }

    fun editPersonaPrompt(value: String) = updatePersona { it.copy(prompt = value) }

    fun savePersona() {
        val character = _uiState.value.selectedCharacter ?: return
        val editor = _uiState.value.personaEditor ?: return
        updatePersona { it.copy(saving = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                profiles.savePersona(character, editor.name, editor.prompt)
            }
            // 重新解析而不是把草稿当成已保存状态：落盘后的权威值才是新的 profile
            val saved = profiles.personaProfile(character)
            _uiState.update {
                it.copy(
                    personaEditor = PersonaEditorState.of(
                        profile = saved,
                        hasDefault = editor.hasDefault,
                    ),
                )
            }
            _profileEvents.emit(CharacterProfileEvent.PersonaSaved)
            refresh()
        }
    }

    fun resetPersona() {
        val character = _uiState.value.selectedCharacter ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profiles.resetPersona(character) }
            _uiState.update {
                it.copy(
                    personaEditor = PersonaEditorState.of(
                        profile = profiles.personaProfile(character),
                        hasDefault = profiles.hasDefaultPersona(character),
                    ),
                )
            }
            _profileEvents.emit(CharacterProfileEvent.PersonaReset)
            refresh()
        }
    }

    fun editVoiceName(value: String) = updateVoice { it.copy(name = value) }

    fun selectSpeaker(speakerId: Int) = updateVoice { it.copy(speakerId = speakerId) }

    fun saveVoice() {
        val character = _uiState.value.selectedCharacter ?: return
        val editor = _uiState.value.voiceEditor ?: return
        val speakerId = editor.speakerId ?: character.speakerId
        updateVoice { it.copy(saving = true) }
        viewModelScope.launch {
            profiles.saveVoice(character, editor.name, speakerId)
            val characters = repository.loadCharacters()
            val updated = characters.firstOrNull { it.id == character.id }
            _uiState.update { state ->
                state.copy(
                    characters = characters,
                    voiceEditor = updated?.let {
                        VoiceEditorState.of(
                            profile = profiles.voiceProfile(it),
                            speakers = editor.speakers,
                            isCurrentCharacter = it.id == state.currentCharacterId,
                        )
                    } ?: editor.copy(saving = false),
                )
            }
            _profileEvents.emit(CharacterProfileEvent.VoiceSaved)
        }
    }

    /**
     * 试听。**不写历史、不建 session、不触发表现系统**，只借用 Activity 那份
     * 已经装好当前角色模型的 `SoundGenerateHelper`（见
     * `ChatActivityViewModel.previewVoice`）。所以这里只发一个事件，
     * 由 Fragment 转交给 activity 级 ViewModel —— 本 ViewModel 不持有 TTS。
     */
    fun requestPreview() {
        val editor = _uiState.value.voiceEditor ?: return
        val character = _uiState.value.selectedCharacter ?: return
        // 重复点击 = 停止，而不是叠一层播放
        if (editor.previewing) {
            stopPreview()
            return
        }
        if (!editor.previewEnabled) return
        updateVoice { it.copy(previewing = true) }
        viewModelScope.launch { _profileEvents.emit(CharacterProfileEvent.PreviewRequested(character)) }
    }

    /** 试听结束（播完、失败或被打断）后回到可播放态。 */
    fun onPreviewFinished(unavailable: Boolean = false) {
        updateVoice { it.copy(previewing = false) }
        if (unavailable) {
            viewModelScope.launch { _profileEvents.emit(CharacterProfileEvent.PreviewUnavailable) }
        }
    }

    /**
     * 停止试听。**只在真的在试听时才发停止事件** —— 那个事件最终会调到
     * `cancelPlayback()`，而它取消的是聊天共用的那一份 AudioTrack 轮次；
     * 无条件发送会让「关掉角色详情」顺手把正在播报的回复掐掉。
     */
    fun stopPreview() {
        if (_uiState.value.voiceEditor?.previewing != true) return
        updateVoice { it.copy(previewing = false) }
        viewModelScope.launch { _profileEvents.emit(CharacterProfileEvent.PreviewStopRequested) }
    }

    /**
     * 详情页那两行的摘要。**不做「已配置 / 未配置」这种二值展示** ——
     * 用户在意的是「现在用的是哪一份人格 / 哪个声音」，
     * 而且 personaProfileId 非空只说明 ID 补齐过，和内容有没有填过无关。
     */
    private fun summaryOf(character: CharacterModel): CharacterProfileSummary {
        val persona = profiles.personaProfile(character)
        val voice = profiles.voiceProfile(character)
        return CharacterProfileSummary(
            personaName = persona.name.takeIf { !persona.isEmpty },
            voiceName = voice.name.takeIf { voice.available },
        )
    }

    private fun updatePersona(transform: (PersonaEditorState) -> PersonaEditorState) {
        _uiState.update { state ->
            state.personaEditor?.let { state.copy(personaEditor = transform(it)) } ?: state
        }
    }

    private fun updateVoice(transform: (VoiceEditorState) -> VoiceEditorState) {
        _uiState.update { state ->
            state.voiceEditor?.let { state.copy(voiceEditor = transform(it)) } ?: state
        }
    }
}

data class ModelManagerUiState(
    val loading: Boolean = false,
    val characters: List<CharacterModel> = emptyList(),
    val currentCharacterId: String? = null,
    val selectedCharacterId: String? = null,
    /** 非 null 表示正在导入 */
    val importing: ImportingState? = null,
    val detailRoute: CharacterDetailRoute = CharacterDetailRoute.DETAIL,
    val personaEditor: PersonaEditorState? = null,
    val voiceEditor: VoiceEditorState? = null,
    /** 当前选中角色的 profile 摘要，只给详情页那两行用 */
    val profileSummary: CharacterProfileSummary? = null,
    /** 人格页有未保存改动且用户正在尝试离开 */
    val confirmDiscardPersona: Boolean = false,
) {
    val currentCharacter: CharacterModel?
        get() = characters.firstOrNull { it.id == currentCharacterId }

    val selectedCharacter: CharacterModel?
        get() = characters.firstOrNull { it.id == selectedCharacterId }

    val personaSummary: String? get() = profileSummary?.personaName

    val voiceSummary: String? get() = profileSummary?.voiceName
}

data class CharacterProfileSummary(
    val personaName: String?,
    val voiceName: String?,
)

sealed interface ImportingState {
    data class Extracting(val percent: Int) : ImportingState
    data object Validating : ImportingState
}

sealed interface ModelManagerEvent {
    data class ImportSucceeded(val name: String) : ModelManagerEvent
    data class ImportFailed(val error: ImportError) : ModelManagerEvent
    data class Deleted(val name: String) : ModelManagerEvent
    data class CurrentChanged(val character: CharacterModel) : ModelManagerEvent
}

/**
 * Persona / Voice 页的事件。
 *
 * 和 [ModelManagerEvent] 分成两个 sealed 类型而不是合并加 `else`：
 * 消费者不同（宿主 Fragment vs `rememberCharacterProfileActions`），
 * 分开之后两边的 `when` 都能保持穷尽，将来加事件漏处理会编译不过。
 */
sealed interface CharacterProfileEvent {
    data object PersonaSaved : CharacterProfileEvent
    data object PersonaReset : CharacterProfileEvent
    data object VoiceSaved : CharacterProfileEvent

    /** 宿主收到后转给 activity 级 ViewModel —— TTS 不归本 ViewModel 持有 */
    data class PreviewRequested(val character: CharacterModel) : CharacterProfileEvent
    data object PreviewStopRequested : CharacterProfileEvent
    data object PreviewUnavailable : CharacterProfileEvent
}
