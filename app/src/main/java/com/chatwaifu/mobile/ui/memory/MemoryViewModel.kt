package com.chatwaifu.mobile.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemoryRepository
import com.chatwaifu.log.room.RoomMemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Description: 记忆页的 ViewModel。
 *
 * 这一页**不只是给用户看的**：记忆系统出问题时（记错了、记重了、该忘的没忘），
 * 没有它唯一的可观测面就是「角色说话变奇怪了」，根本无法定位。
 * 所以它是 Phase 1 的必要组成，不是锦上添花。见 docs/memory.md 6.6。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository = RoomMemoryRepository(application)

    private val characterId = MutableStateFlow("")

    /** 记忆被改后要通知聊天侧丢缓存，见 [MemoryEvent.Changed] */
    private val _events = MutableStateFlow<MemoryEvent?>(null)
    val events: StateFlow<MemoryEvent?> = _events

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MemoryUiState> = characterId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList()) else repository.observeFacts(id)
        }
        .map { facts -> MemoryUiState(characterName = characterId.value, facts = facts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryUiState())

    fun bind(character: String?) {
        characterId.value = character.orEmpty()
    }

    fun updateContent(fact: MemoryFact, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed == fact.content) return
        mutate {
            // 走 upsert 而不是新建：slot 没变，落到同一行上
            repository.upsert(fact.copy(content = trimmed, updatedAt = System.currentTimeMillis()))
        }
    }

    fun add(slot: String, content: String) {
        val id = characterId.value
        val cleanSlot = slot.trim().take(MemoryFact.MAX_SLOT_LENGTH)
        val cleanContent = content.trim()
        if (id.isEmpty() || cleanSlot.isEmpty() || cleanContent.isEmpty()) return
        val now = System.currentTimeMillis()
        mutate {
            repository.upsert(
                MemoryFact(
                    characterId = id,
                    slot = cleanSlot,
                    content = cleanContent,
                    importance = MANUAL_IMPORTANCE,
                    // 手动加的默认 pin 住：用户特意敲进来的，不该被抽取器判定失效后删掉
                    pinned = true,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    /** 用户手动删，无视 pinned */
    fun delete(fact: MemoryFact) = mutate { repository.deleteById(fact.id) }

    fun togglePinned(fact: MemoryFact) =
        mutate { repository.setPinned(fact.id, !fact.pinned) }

    fun consumeEvent() {
        _events.value = null
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onSuccess { _events.value = MemoryEvent.Changed }
        }
    }

    companion object {
        /** 手动添加的重要度给高一点，超预算时优先留下 */
        private const val MANUAL_IMPORTANCE = 80
    }
}

data class MemoryUiState(
    val characterName: String = "",
    val facts: List<MemoryFact> = emptyList(),
)

sealed interface MemoryEvent {
    /** 记忆被改写，聊天侧的 `FactMemoryContributor` 缓存要失效 */
    data object Changed : MemoryEvent
}
