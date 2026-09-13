package com.chatwaifu.mobile.ui.channellist

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.VITSLoadStatus
import com.chatwaifu.mobile.ui.modelmanager.ModelManagerContent
import com.chatwaifu.mobile.ui.modelmanager.ModelManagerEvent
import com.chatwaifu.mobile.ui.modelmanager.ModelManagerViewModel
import com.chatwaifu.mobile.ui.modelmanager.toMessage
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import com.chatwaifu.vits.utils.permission.PermissionUtils

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
                        is ModelManagerEvent.CurrentChanged ->
                            activityViewModel.selectCharacter(event.character)
                        ModelManagerEvent.ConfigSaved -> Unit
                    }
                }
            }

            ChatWaifu_MobileTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
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
                    )

                    val loadVitsResult by activityViewModel.loadVITSModelLiveData
                        .collectAsStateWithLifecycle(VITSLoadStatus.STATE_DEFAULT)
                    when (loadVitsResult) {
                        VITSLoadStatus.STATE_SUCCESS -> {
                            Log.d(TAG, "navigate to chat")
                            findNavController().navigate(R.id.nav_chat)
                        }
                        VITSLoadStatus.STATE_FAILED ->
                            showToast("load vits model failed....")
                        else -> Unit
                    }

                    val loading = activityViewModel.loadingUILiveData.observeAsState().value
                    if (loading?.first == true) {
                        LoadingIndicator(loadingText = loading.second)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermission()
        characterViewModel.refresh()
    }

    private fun checkPermission() {
        if (!PermissionUtils.checkNetPermission(requireActivity())) {
            PermissionUtils.requestNetPermission(requireActivity())
        }
        if (!PermissionUtils.checkRecordPermission(requireActivity())) {
            PermissionUtils.requestRecordPermission(requireActivity())
        }
    }

    companion object {
        private const val TAG = "ChannelListFragment"
    }
}
