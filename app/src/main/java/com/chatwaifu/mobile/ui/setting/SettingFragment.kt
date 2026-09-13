package com.chatwaifu.mobile.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatwaifu.mobile.ui.theme.globalDarkTheme

class SettingFragment : Fragment() {

    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel: SettingFragmentViewModel by viewModels()
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
            val settingState = fragmentViewModel.state
            var currentDarkMode by rememberSaveable {
                mutableStateOf(settingState.darkModeSwitch)
            }
            globalDarkTheme = currentDarkMode
            ChatWaifu_MobileTheme(darkTheme = currentDarkMode) {
                SettingContentScaffold(
                    settingUIState = settingState,
                    onNavIconPressed = {
                        activityViewModel.openDrawer()
                    },
                    onSave = {
                        fragmentViewModel.saveData()
                        activityViewModel.refreshAllKeys(rebuildSession = false)
                        currentDarkMode = settingState.darkModeSwitch
                        showToast("save key success...")
                    },
                    onTestConnection = fragmentViewModel::testConnection,
                )
            }
        }
    }
}