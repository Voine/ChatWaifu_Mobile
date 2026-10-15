package com.chatwaifu.mobile.ui.channellist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.ui.modelmanager.ModelManagerContent
import com.chatwaifu.mobile.ui.modelmanager.ModelManagerEvent
import com.chatwaifu.mobile.ui.modelmanager.ModelManagerViewModel
import com.chatwaifu.mobile.ui.modelmanager.rememberCharacterProfileActions
import com.chatwaifu.mobile.ui.modelmanager.toMessage
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

class ChannelListFragment : Fragment() {

    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val characterViewModel: ModelManagerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(inflater.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setContent {
            val resources = LocalResources.current
            val uiState by characterViewModel.uiState.collectAsStateWithLifecycle()
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> uri?.let(characterViewModel::import) }

            LaunchedEffect(Unit) {
                characterViewModel.events.collect { event ->
                    when (event) {
                        is ModelManagerEvent.ImportSucceeded ->
                            showToast(
                                resources.getString(
                                    R.string.model_manager_import_done,
                                    event.name,
                                )
                            )
                        is ModelManagerEvent.ImportFailed ->
                            showToast(event.error.toMessage(resources))
                        is ModelManagerEvent.Deleted ->
                            showToast(
                                resources.getString(
                                    R.string.model_manager_delete_done,
                                    event.name,
                                )
                            )
                        // 角色页的「设为当前」= 选好了，回 Companion 跟她聊。
                        // 声库 / persona / 历史都由 Companion 回来后按新角色重建
                        is ModelManagerEvent.CurrentChanged ->
                            activityViewModel.selectCharacter(event.character, returnHome = true)
                        // profile 事件是独立的 CharacterProfileEvent，
                        // 由 rememberCharacterProfileActions 消费，不走这条流
                    }
                }
            }

            val profileActions = rememberCharacterProfileActions(
                uiState = uiState,
                viewModel = characterViewModel,
                activityViewModel = activityViewModel,
            )

            ChatWaifu_MobileTheme {
                ModelManagerContent(
                    uiState = uiState,
                    onNavIconPressed = activityViewModel::openDrawer,
                    onImportClick = {
                        picker.launch(
                            arrayOf(
                                "application/zip",
                                "application/octet-stream",
                            )
                        )
                    },
                    onOpenDetail = characterViewModel::openDetail,
                    onCloseDetail = characterViewModel::closeDetail,
                    onSetCurrent = characterViewModel::setCurrent,
                    onDelete = characterViewModel::delete,
                    profileActions = profileActions,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        characterViewModel.refresh()
    }

    /** 离开页面必须停试听，声音不能跟着用户走回 Companion。 */
    override fun onStop() {
        super.onStop()
        characterViewModel.stopPreview()
    }
}
