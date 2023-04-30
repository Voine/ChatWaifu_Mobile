package com.chatwaifu.mobile.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chatwaifu.live2d.GLRenderer
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.databinding.FragmentHomeBinding
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import java.io.File

class ChatFragment : Fragment() {
    companion object {
        private const val TAG = "ChatFragment"
    }
    private val activityViewModel: ChatActivityViewModel by activityViewModels()

    var live2DView: GLSurfaceView? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        live2DView = GLSurfaceView(inflater.context).apply {
            setOnTouchListener(View.OnTouchListener { v, event ->
                onLive2DViewTouchEventHandler(v, event)
                true
            })
        }

        initLive2D(live2DView!!)

        return ComposeView(inflater.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                ChatWaifu_MobileTheme {
                    ChatContentScaffold(
                        originAndroidView = { live2DView!! },
                        onNavIconPressed = { activityViewModel.openDrawer() },
                        chatTitle = activityViewModel.currentLive2DModelName,
                        defaultChatTitle = resources.getString(R.string.chat_dialog_sender_me),
                        onSendMsgButtonClick = ::onSendBtnClick,
                        chatActivityViewModel = activityViewModel,
                        onErrorOccur = {
                            showToast(it)
                        }
                    )
                }
            }
        }
    }

    private fun initLive2D(renderGLView: GLSurfaceView) {
        JniBridgeJava.SetActivityInstance(requireActivity())
        JniBridgeJava.SetContext(requireContext())
        renderGLView.setEGLContextClientVersion(2)
        renderGLView.setRenderer(GLRenderer())
        renderGLView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun onLive2DViewTouchEventHandler(v: View?, event: MotionEvent?) {
        Log.d(TAG, "receive touch event $event")
    }

    private fun onSendBtnClick(sendText: String) {
        if (sendText.isNotBlank()) {
            Log.d(TAG, "try to send msg $sendText")
            activityViewModel.sendMessage(sendText)
        }
    }

    override fun onStart() {
        super.onStart()
        JniBridgeJava.nativeOnStart()
        val jsonFileName = "${activityViewModel.currentLive2DModelName}.model3.json"
        if (!File(
                activityViewModel.currentLive2DModelPath + File.separator,
                jsonFileName
            ).exists()
        ) {
            showToast("cant find model3.json...")
            return
        }
        JniBridgeJava.nativeProjectChangeTo(
            activityViewModel.currentLive2DModelPath + File.separator,
            jsonFileName
        )
        if (activityViewModel.currentLive2DModelName == Constant.LOCAL_MODEL_KURISU) {
            //fix kurisu live2d bug..
            JniBridgeJava.needRenderBack(false)
            JniBridgeJava.nativeApplyExpression("fix")
        }
    }

    override fun onDestroyView() {
        live2DView = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        live2DView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        live2DView?.onPause()
        JniBridgeJava.nativeOnPause()
    }

    override fun onStop() {
        super.onStop()
        JniBridgeJava.nativeOnStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        JniBridgeJava.nativeOnDestroy()
    }
}