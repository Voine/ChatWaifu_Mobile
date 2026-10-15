package com.chatwaifu.mobile.ui.chatlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

class ChatLogFragment : Fragment() {
    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel: ChatLogViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val current by activityViewModel.currentCharacter.collectAsStateWithLifecycle()
                val characters by activityViewModel.characters.collectAsStateWithLifecycle()
                // 当前角色是异步恢复的；以前这里写死从 Yuuka 开始，不管用户在跟谁聊
                val initName = current?.storageKey ?: return@setContent
                LaunchedEffect(initName) { fragmentViewModel.loadAllChatMessage(initName) }
                ChatWaifu_MobileTheme {
                    key(initName) {
                        ChatLogContent(
                            channelName = initName,
                            chatLogViewModel = fragmentViewModel,
                            onNavIconPressed = {
                                activityViewModel.openDrawer()
                            },
                            externalModelList = characters
                                .map { it.storageKey }
                                .filterNot { it in BUILT_IN_NAMES },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** 下拉菜单里内置的三个是写死带头像的，这里只补导入的那些 */
        val BUILT_IN_NAMES = setOf(
            Constant.LOCAL_MODEL_YUUKA,
            Constant.LOCAL_MODEL_ATRI,
            Constant.LOCAL_MODEL_AMADEUS,
        )
    }
}
