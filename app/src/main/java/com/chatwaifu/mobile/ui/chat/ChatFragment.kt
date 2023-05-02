package com.chatwaifu.mobile.ui.chat

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.chatwaifu.live2d.GLRenderer
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
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

    var live2DView: GLSurfaceView? = null
    @Volatile
    private var enableTouch: Boolean = false
    private var _live2dLoadInterface: JniBridgeJava.Live2DLoadInterface? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        live2DView = GLSurfaceView(inflater.context).apply {
            setOnTouchListener(View.OnTouchListener { v, event ->
                if (enableTouch) {
                    return@OnTouchListener onLive2DViewTouchEventHandler(v, event)
                }
                return@OnTouchListener false
            })
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        initLive2D(live2DView!!)

        _live2dLoadInterface = object : JniBridgeJava.Live2DLoadInterface {
            override fun onLoadError() {}
            override fun onLoadOneMotion(motionGroup: String?, index: Int, motionName: String?) {}
            override fun onLoadOneExpression(expressionName: String?, index: Int) {}
            override fun onLoadDone() {onLoadModelDone()}
        }
        JniBridgeJava.setLive2DLoadInterface(_live2dLoadInterface)

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
                        },
                        onTouchStart = {
                            enableTouch = true
                        },
                        onTouchEnd = {
                            enableTouch = false
                            fragmentViewModel.saveTouch(activityViewModel.currentLive2DModelName)
                        },
                        onResetModel = {
                            fragmentViewModel.resetModel()
                        }
                    )
                }
            }
        }
    }

    private fun onLoadModelDone() {
        CoroutineScope(Dispatchers.Main).launch{
            fragmentViewModel.initTouch(activityViewModel.currentLive2DModelName)
        }
    }

    private fun initLive2D(renderGLView: GLSurfaceView) {
        JniBridgeJava.SetActivityInstance(requireActivity())
        JniBridgeJava.SetContext(requireContext())
        renderGLView.setEGLContextClientVersion(2)
        renderGLView.setRenderer(GLRenderer())
        renderGLView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun onLive2DViewTouchEventHandler(v: View?, event: MotionEvent?): Boolean {
        Log.d(TAG, "receive touch event $event")
        return fragmentViewModel.handleTouchEvent(event, v?.width, v?.height)
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
        JniBridgeJava.setLive2DLoadInterface(null)
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