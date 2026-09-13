package com.chatwaifu.mobile.ui.chat

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatwaifu.live2d.GLRenderer
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.common.ChatDialogContentUIState
import com.chatwaifu.mobile.ui.companion.CharacterRendererHost
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main Chat Fragment
 */
class ChatFragment : Fragment() {
    companion object {
        private const val TAG = "ChatFragment"
    }
    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel:  ChatFragmentViewModel by viewModels()

    private var live2DView: GLSurfaceView? = null
    private var rendererHost: CharacterRendererHost? = null
    @Volatile
    private var enableTouch: Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rendererHost = CharacterRendererHost(
            activity = requireActivity(),
            onLoadDone = ::onLoadModelDone,
            onLoadError = { showToast(it) },
            onTouch = { v, event ->
                if (enableTouch) {
                    onLive2DViewTouchEventHandler(v, event)
                } else {
                    false
                }
            },
        )
        live2DView = rendererHost!!.view.apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        return ComposeView(inflater.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                var sendMessageContent by rememberSaveable { mutableStateOf("") }
                var sendMessageTitle by rememberSaveable { mutableStateOf("") }
                val chatContentUIStateFlow =
                    activityViewModel.chatContentUIFlow.collectAsStateWithLifecycle(initialValue = ChatDialogContentUIState(isInitState = true))
                val contentDialogUIState = chatContentUIStateFlow.value
                if (!contentDialogUIState.errorMsg.isNullOrEmpty()) {
                    // 错误文案已经在 ChatErrorMessages 里翻译过了，不用再拼 "GPT Error"
                    showToast(
                        contentDialogUIState.errorMsg,
                        type = Toast.LENGTH_LONG
                    )
                } else if (contentDialogUIState.chatContent.isEmpty() &&
                    !contentDialogUIState.isInitState &&
                    // 流式的首帧内容可能是空的，不能当成「回复为空」
                    !contentDialogUIState.isStreaming
                ) {
                    showToast("Error occur...ChatGPT response empty")
                } else {
                    Log.d("ChatContentScaffold", "set response $contentDialogUIState")
                    sendMessageContent = contentDialogUIState.chatContent
                }
                sendMessageTitle =
                    if (contentDialogUIState.isFromMe) resources.getString(R.string.chat_dialog_sender_me) else activityViewModel.currentCharacterName
                ChatWaifu_MobileTheme {
                    ChatContentScaffold(
                        originAndroidView = { live2DView!! },
                        onNavIconPressed = { activityViewModel.openDrawer() },
                        chatTitle = activityViewModel.currentCharacterName,
                        sendMessageTitle = sendMessageTitle,
                        sendMessageContent = sendMessageContent,
                        onSendMsgButtonClick = {
                            activityViewModel.sendMineMsgUIState(it)
                            onSendMessage(it)
                        },
                        chatActivityViewModel = activityViewModel,
                        onErrorOccur = {
                            showToast(it)
                        },
                        onTouchStart = {
                            enableTouch = true
                        },
                        onTouchEnd = {
                            enableTouch = false
                            fragmentViewModel.saveTouch(activityViewModel.currentCharacterStorageKey)
                        },
                        onResetModel = {
                            fragmentViewModel.resetModel()
                        },
                        onRecordStart = {
                            fragmentViewModel.onRecordStart()
                        },
                        onRecordEnd = {
                            fragmentViewModel.onRecordEnd{ result ->
                                result?.let {
                                    sendMessageContent = it
                                    onSendMessage(it)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentViewModel.bindSherpa(requireContext())
    }

    private fun onLoadModelDone() {
        CoroutineScope(Dispatchers.Main).launch{
            fragmentViewModel.initTouch(activityViewModel.currentCharacterStorageKey)
            activityViewModel.lipsValueHandler.createContext()
        }
    }

    private fun onLive2DViewTouchEventHandler(v: View?, event: MotionEvent?): Boolean {
        Log.d(TAG, "receive touch event $event")
        return fragmentViewModel.handleTouchEvent(event, v?.width, v?.height)
    }

    private fun onSendMessage(sendText: String) {
        if (sendText.isNotBlank()) {
            Log.d(TAG, "try to send msg $sendText")
            activityViewModel.sendMessage(sendText)
        }
    }

    override fun onStart() {
        super.onStart()
        rendererHost?.onStart()
        val character = activityViewModel.currentCharacter
        if (character == null) {
            showToast("no character selected...")
            return
        }
        // 入口文件名来自导入时写下的 meta.json，不再假设它和角色名同名
        val jsonFileName = character.live2dEntryFileName
        if (!File(character.live2dDir, jsonFileName).exists()) {
            showToast("cant find $jsonFileName...")
            return
        }
        rendererHost?.setCharacter(character)
    }

    override fun onDestroyView() {
        live2DView = null
        rendererHost?.release()
        rendererHost = null
        activityViewModel.lipsValueHandler.destroyContext()
        fragmentViewModel.unbindSherpa(requireContext())
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        rendererHost?.onResume()
    }

    override fun onPause() {
        super.onPause()
        rendererHost?.onPause()
    }

    override fun onStop() {
        super.onStop()
        rendererHost?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        JniBridgeJava.nativeOnDestroy()
    }
}