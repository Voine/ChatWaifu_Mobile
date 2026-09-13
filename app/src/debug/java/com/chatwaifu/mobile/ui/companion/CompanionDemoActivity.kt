package com.chatwaifu.mobile.ui.companion

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.chatwaifu.mobile.data.model.ModelProvider
import com.chatwaifu.mobile.ChatActivity
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import kotlinx.coroutines.launch

class CompanionDemoActivity : AppCompatActivity() {
    private val companionViewModel: CompanionViewModel by viewModels {
        CompanionViewModel.factory(
            responseDriverFactory = { CompanionRealResponseDriver(applicationContext) },
        )
    }

    private lateinit var rendererHost: CharacterRendererHost
    private var rendererReleased = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        rendererHost = CharacterRendererHost(
            activity = this,
            onLoadDone = {
                companionViewModel.setRendererReady()
            },
            onLoadError = companionViewModel::setRendererFailed,
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
        loadCharacter()
    }

    private fun loadCharacter() {
        companionViewModel.setRendererLoading()
        lifecycleScope.launch {
            val characters = ModelProvider.repository(this@CompanionDemoActivity)
                .loadCharacters()
            val character = if (companionViewModel.hasInitializedCharacter) {
                characters.firstOrNull {
                    it.name == companionViewModel.uiState.value.characterName
                }
            } else {
                characters.firstOrNull()
            }
            if (character == null) {
                companionViewModel.setRendererFailed("没有可用角色资源")
            } else {
                if (!companionViewModel.hasInitializedCharacter) {
                    companionViewModel.setCharacter(character.name)
                    companionViewModel.prepareConversation(character)
                }
                rendererHost.setCharacter(character)
            }
        }
    }

    private fun handleEvent(event: CompanionEvent) {
        when (event) {
            CompanionEvent.RetryRenderer -> loadCharacter()
            CompanionEvent.More -> releaseRendererForNavigation()
            else -> companionViewModel.onEvent(event)
        }
    }

    private fun releaseRendererForNavigation() {
        if (rendererReleased) return
        companionViewModel.releaseConversation()
        rendererHost.onPause()
        rendererHost.onStop()
        rendererHost.onDestroy()
        rendererReleased = true
        lifecycleScope.launch {
            companionViewModel.awaitConversationRelease()
            startActivity(Intent(this@CompanionDemoActivity, ChatActivity::class.java))
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
}
