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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

            // 用户改了记忆 → 让聊天侧那份缓存失效，下一轮请求重新读库。
            // 不做这一步的话，本次会话剩下的轮次仍然在用旧记忆
            LaunchedEffect(event) {
                if (event is MemoryEvent.Changed) {
                    activityViewModel.onMemoryChanged()
                    fragmentViewModel.consumeEvent()
                }
            }

            ChatWaifu_MobileTheme {
                MemoryContent(
                    uiState = uiState,
                    onNavIconPressed = { activityViewModel.openDrawer() },
                    onUpdateContent = fragmentViewModel::updateContent,
                    onDelete = fragmentViewModel::delete,
                    onTogglePinned = fragmentViewModel::togglePinned,
                    onAdd = fragmentViewModel::add,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 记忆按角色隔离，没选角色时这一页是空的
        fragmentViewModel.bind(activityViewModel.currentCharacter?.storageKey)
    }
}
