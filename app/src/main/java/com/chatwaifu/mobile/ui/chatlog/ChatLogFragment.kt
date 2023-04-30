package com.chatwaifu.mobile.ui.chatlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

class ChatLogFragment : Fragment() {
    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel: ChatLogViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initName = resources.getString(R.string.chat_log_item_yuuka)
        fragmentViewModel.loadAllChatMessage(initName)
        return ComposeView(requireContext()).apply {
            setContent {
                ChatWaifu_MobileTheme {
                    ChatLogContent(
                        channelName = initName,
                        chatLogViewModel = fragmentViewModel,
                        onNavIconPressed = {
                            activityViewModel.openDrawer()
                        }
                    )
                }
            }
        }
    }
}