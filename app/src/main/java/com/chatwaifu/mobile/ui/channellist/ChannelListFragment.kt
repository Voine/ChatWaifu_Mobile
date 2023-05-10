package com.chatwaifu.mobile.ui.channellist

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.VITSLoadStatus
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import com.chatwaifu.vits.utils.permission.PermissionUtils

class ChannelListFragment : Fragment() {

    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(inflater.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        setContent {
            ChatWaifu_MobileTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val resultLists = activityViewModel.initModelResultLiveData.observeAsState()
                    ChannelListContent(
                        channelListUiState = ChannelListUiState(
                            messages = resultLists.value ?: emptyList()
                        ),
                        onNavIconPressed = {
                            activityViewModel.openDrawer()
                        },
                        onChannelItemClick = ::onClick
                    )

                    val loadVitsResult by activityViewModel.loadVITSModelLiveData.collectAsStateWithLifecycle(
                        initialValue = VITSLoadStatus.STATE_DEFAULT
                    )
                    when (loadVitsResult) {
                        VITSLoadStatus.STATE_SUCCESS -> {
                            Log.d(TAG, "navigate to chat")
                            findNavController().navigate(R.id.nav_chat)
                        }

                        VITSLoadStatus.STATE_FAILED -> {
                            showToast("load vits model failed....")
                        }

                        else -> {}
                    }

                    val loadingUIResult = activityViewModel.loadingUILiveData.observeAsState()
                    val result = loadingUIResult.value
                    if (result?.first == true) {
                        LoadingIndicator(loadingText = result.second)
                    }
                }
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermission()
        activityViewModel.initModel(requireContext())
    }

    private fun checkPermission() {
        if (!PermissionUtils.checkStoragePermission(requireActivity())) {
            PermissionUtils.requestStoragePermission(requireActivity())
        }
        if (!PermissionUtils.checkNetPermission(requireActivity())) {
            PermissionUtils.requestNetPermission(requireActivity())
        }
        if (!PermissionUtils.checkRecordPermission(requireActivity())) {
            PermissionUtils.requestRecordPermission(requireActivity())
        }
    }

    private fun onClick(item: ChannelListBean) {
        activityViewModel.currentLive2DModelPath = item.characterPath
        activityViewModel.currentLive2DModelName = item.characterName
        activityViewModel.loadModelSystemSetting(item.characterName)
        activityViewModel.currentVITSModelName = item.characterName
        activityViewModel.loadChatListCache(item.characterName)
        activityViewModel.loadVitsModel(item.characterVitsPath)
    }

    companion object {
        private const val TAG = "ChannelListFragment"
    }
}