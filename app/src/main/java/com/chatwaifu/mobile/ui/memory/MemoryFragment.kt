package com.chatwaifu.mobile.ui.memory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

class MemoryFragment : Fragment() {

    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel: MemoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(inflater.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContent {
            val uiState by fragmentViewModel.uiState.collectAsStateWithLifecycle()
            val event by fragmentViewModel.events.collectAsStateWithLifecycle()

            // 聊天链路在 Companion 里，回去时会按角色重建（注入缓存随之清空），
            // 这里不需要再通知谁，只把事件消费掉
            LaunchedEffect(event) {
                if (event is MemoryEvent.Changed) fragmentViewModel.consumeEvent()
            }

            ChatWaifu_MobileTheme {
                MemoryContent(
                    uiState = uiState,
                    onNavIconPressed = { activityViewModel.openDrawer() },
                    onUpdateContent = fragmentViewModel::updateContent,
                    onDelete = fragmentViewModel::delete,
                    onTogglePinned = fragmentViewModel::togglePinned,
                    onAdd = fragmentViewModel::add,
                    onUpdateEpisode = fragmentViewModel::updateEpisode,
                    onDeleteEpisode = fragmentViewModel::deleteEpisode,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 记忆按角色隔离，没选角色时这一页是空的。当前角色是异步恢复的，跟着它走
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activityViewModel.currentCharacter.collect {
                    fragmentViewModel.bind(it?.storageKey)
                }
            }
        }
    }
}
