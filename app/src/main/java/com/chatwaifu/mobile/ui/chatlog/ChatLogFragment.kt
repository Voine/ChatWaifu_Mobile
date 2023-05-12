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
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import com.chatwaifu.mobile.utils.LocalModelManager

class ChatLogFragment : Fragment() {
    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel: ChatLogViewModel by viewModels()
    private val localModelManager: LocalModelManager by lazy { LocalModelManager() }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initName = Constant.LOCAL_MODEL_YUUKA
        fragmentViewModel.loadAllChatMessage(initName)
        return ComposeView(requireContext()).apply {
            setContent {
                ChatWaifu_MobileTheme {
                    ChatLogContent(
                        channelName = initName,
                        chatLogViewModel = fragmentViewModel,
                        onNavIconPressed = {
                            activityViewModel.openDrawer()
                        },
                        externalModelList = localModelManager.getChatLogExternalItemList()
                    )
                }
            }
        }
    }
}