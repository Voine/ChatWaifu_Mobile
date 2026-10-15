package com.chatwaifu.mobile.ui.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chatwaifu.mobile.ChatActivity
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.ModelProvider
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import com.chatwaifu.mobile.utils.Live2DTouchManager
import kotlinx.coroutines.launch

/**
 * Description: 正式前台。登录之后的根页面，Live2D + 四态对话都在这里。
 *
 * 由 Phase 1~2.8 的 debug-only `CompanionDemoActivity` 提升而来（T1）。
 * 二级页面（角色 / 聊天记录 / 记忆 / 设置）仍是 [ChatActivity] 里的 Fragment，
 * 从「更多」菜单进入；**跳转前必须释放 renderer 和 TTS**：
 * - Live2D 的 native renderer 是单例，GL context 不能和新 Activity 抢
 * - BV2 的 loader 是进程级单例，`ChatActivity` 的试听会再建一份
 *
 * 所以这里不是「压栈」而是「交棒」：去二级页面时 finish 自己，二级页面回来时
 * 再重新拉起本页，并按最新设置 / 角色重建对话链路。
 *
 * manifest 里的 `windowSoftInputMode="adjustResize"` 是 Phase 1 真机修过的 IME 缺陷，必须保留。
 *
 * Author: Voine
 * Date: 2026/10/14
 */
class CompanionActivity : AppCompatActivity() {
    /**
     * 麦克风权限。**权限属于 UI/platform 层** —— 只有 Activity 能弹系统对话框，
     * 所以 AsrEngine 和 AsrCompanionVoiceInput 都不碰它，结果通过
     * [CompanionEvent.VoicePermissionResult] 回给状态机。
     */
    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        companionViewModel.onEvent(
            CompanionEvent.VoicePermissionResult(
                granted = granted,
                // 拒绝后 shouldShowRequestPermissionRationale 仍为 true 表示还能再问；
                // 为 false 就是「不再询问」，只能引导去系统设置
                canAskAgain = granted || shouldShowRequestPermissionRationale(
                    Manifest.permission.RECORD_AUDIO
                ),
            )
        )
    }

    /**
     * 系统照片选择器（Android 13+ 原生，29~32 由 androidx 回落到 SAF）。图片和视频都收。
     * **不需要任何存储权限**，和工程「只申请 RECORD_AUDIO + INTERNET」的约束一致。
     */
    private val mediaPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        companionViewModel.onEvent(CompanionEvent.AttachmentPicked(uri?.toString()))
    }

    /**
     * 音频 / PDF 走 SAF（`OpenDocument`）：照片选择器只管图片和视频。同样不需要存储权限——
     * SAF 给的是单个文件的临时读授权，导入时立刻落盘到应用专属目录，不需要持久授权。
     */
    private val documentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        companionViewModel.onEvent(CompanionEvent.AttachmentPicked(uri?.toString()))
    }

    private val companionViewModel: CompanionViewModel by viewModels {
        CompanionViewModel.factory(
            responseDriverFactory = { CompanionRealResponseDriver(applicationContext) },
            voiceInputFactory = {
                AsrCompanionVoiceInput(
                    context = applicationContext,
                    permissionRequester = {
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            },
        )
    }

    private lateinit var rendererHost: CharacterRendererHost
    private lateinit var touchManager: Live2DTouchManager
    private var rendererReleased = false

    /** 当前 renderer 上的角色，摆位按它的 storageKey 存取 */
    private var renderedCharacter: CharacterModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        touchManager = Live2DTouchManager(this)
        rendererHost = CharacterRendererHost(
            activity = this,
            onLoadDone = {
                // 模型加载完才能设摆位：nativeProjectChangeTo 会把 transform 复位
                renderedCharacter?.let(::applySavedPose)
                companionViewModel.setRendererReady()
            },
            onLoadError = companionViewModel::setRendererFailed,
            // 只有「调整位置」态才把手势交给 Live2D，平时点到角色身上不会误拖
            onTouch = { view, event ->
                companionViewModel.uiState.value.overlay == CompanionOverlayState.ADJUST &&
                    touchManager.handleTouch(event, view.width, view.height)
            },
        )
        setContent {
            val state by companionViewModel.uiState.collectAsStateWithLifecycle()
            ChatWaifu_MobileTheme {
                CompanionScreen(
                    state = state,
                    onEvent = ::handleEvent,
                    renderer = {
                        AndroidView(
                            factory = { rendererHost.view },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                companionViewModel.pickRequests.collect { request ->
                    when (request.type) {
                        AttachmentType.AUDIO -> documentPicker.launch(arrayOf("audio/*"))
                        AttachmentType.DOC -> documentPicker.launch(arrayOf("application/pdf"))
                        // 相册入口和菜单里的「图片 / 视频」是同一个选择器
                        null, AttachmentType.IMAGE, AttachmentType.VIDEO -> mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                }
            }
        }
        loadCharacter()
    }

    private fun loadCharacter() {
        companionViewModel.setRendererLoading()
        lifecycleScope.launch {
            val repository = ModelProvider.repository(this@CompanionActivity)
            val characters = repository.loadCharacters()
            val character = if (companionViewModel.hasInitializedCharacter) {
                characters.firstOrNull {
                    it.id == companionViewModel.uiState.value.characterId
                }
            } else {
                repository.getCurrentCharacter()
            }
            if (character == null) {
                companionViewModel.setRendererFailed("没有可用角色资源")
            } else {
                if (!companionViewModel.hasInitializedCharacter) {
                    companionViewModel.setCharacter(character.id, character.displayName)
                    companionViewModel.prepareConversation(character)
                }
                renderedCharacter = character
                rendererHost.setCharacter(character)
            }
        }
    }

    private fun handleEvent(event: CompanionEvent) {
        when (event) {
            CompanionEvent.RetryRenderer -> loadCharacter()
            is CompanionEvent.Navigate -> {
                companionViewModel.onEvent(event)
                navigateTo(event.destination)
            }
            CompanionEvent.FinishAdjust -> {
                renderedCharacter?.let(::savePose)
                companionViewModel.onEvent(event)
            }
            CompanionEvent.ResetPose -> touchManager.resetParams()
            else -> companionViewModel.onEvent(event)
        }
    }

    private fun applySavedPose(character: CharacterModel) {
        val key = character.storageKey
        val default = Live2DTouchManager.getDefaultModelPosition(key)
        val sp = getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
        touchManager.setInitParams(
            sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_X_PREFIX}$key", default[0]),
            sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_Y_PREFIX}$key", default[1]),
            sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_SCALE_PREFIX}$key", default[2]),
        )
    }

    private fun savePose(character: CharacterModel) {
        val key = character.storageKey
        val (x, y, scale) = touchManager.getLive2DModelTouchParam()
        getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE).edit()
            .putFloat("${Constant.LOCAL_MODEL_TRANSLATE_X_PREFIX}$key", x)
            .putFloat("${Constant.LOCAL_MODEL_TRANSLATE_Y_PREFIX}$key", y)
            .putFloat("${Constant.LOCAL_MODEL_TRANSLATE_SCALE_PREFIX}$key", scale)
            .apply()
    }

    private fun navigateTo(destination: CompanionDestination) {
        if (rendererReleased) return
        companionViewModel.releaseConversation()
        rendererHost.onPause()
        rendererHost.onStop()
        rendererHost.onDestroy()
        rendererReleased = true
        lifecycleScope.launch {
            // 等 TTS 真正释放（BV2 loader 是进程级单例）再拉起二级页面
            companionViewModel.awaitConversationRelease()
            startActivity(ChatActivity.intent(this@CompanionActivity, destination))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!rendererReleased) rendererHost.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (!rendererReleased) rendererHost.onResume()
    }

    override fun onPause() {
        if (!rendererReleased) rendererHost.onPause()
        super.onPause()
    }

    override fun onStop() {
        // 进后台必须停录音：麦克风不能在用户离开页面后继续开着
        companionViewModel.onEvent(CompanionEvent.CancelVoiceInput)
        if (!rendererReleased) rendererHost.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        if (!rendererReleased) {
            if (isFinishing) {
                companionViewModel.releaseConversation()
            } else {
                companionViewModel.onRendererUnavailable()
            }
            rendererHost.onDestroy()
        }
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, CompanionActivity::class.java)
    }
}
