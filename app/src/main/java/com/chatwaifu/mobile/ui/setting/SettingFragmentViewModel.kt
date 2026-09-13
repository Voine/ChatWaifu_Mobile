package com.chatwaifu.mobile.ui.setting

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.core.chat
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.chat.ChatProviderSettings
import com.chatwaifu.mobile.data.chat.LocalNetworkProviderProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SettingFragmentViewModel internal constructor(
    private val providerCreator: (ProviderId, ProviderConfig) -> ChatProvider,
) : ViewModel() {

    constructor() : this(ChatProviderFactory::create)

    private val appContext = ChatWaifuApplication.context
    private val sp: SharedPreferences by lazy {
        appContext.getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }
    private val providerSettings by lazy { ChatProviderSettings(appContext) }
    private var connectionJob: Job? = null

    val state: SettingUIState by lazy {
        SettingUIState(loadInitData(appContext))
    }

    private fun loadInitData(context: Context): SettingUIData = SettingUIData(
        activeProvider = providerSettings.activeProviderId,
        providerProfiles = providerSettings.profiles(),
        translateAppId = sp.getString(Constant.SAVED_TRANSLATE_APP_ID, null).orEmpty(),
        translateAppKey = sp.getString(Constant.SAVED_TRANSLATE_KEY, null).orEmpty(),
        yuukaSetting = sp.getString(Constant.SAVED_YUUKA_SETTING, null)
            ?: context.getString(R.string.default_system_yuuka),
        amaduesSetting = sp.getString(Constant.SAVED_AMADEUS_SETTING, null)
            ?: context.getString(R.string.default_system_amadeus),
        atriSetting = sp.getString(Constant.SAVED_ATRI_SETTING, null)
            ?: context.getString(R.string.default_system_atri),
        translateSwitch = sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true),
        memoryModel = sp.getString(Constant.SAVED_MEMORY_MODEL, null).orEmpty(),
        darkModeSwitch = sp.getBoolean(Constant.SAVED_USE_DARKMODE, false),
    )

    fun saveData() {
        val saved = state.convertState2Data()
        providerSettings.saveAll(
            activeId = saved.activeProvider,
            profiles = saved.providerProfiles.values,
        )
        sp.edit().apply {
            if (saved.translateAppId.isNotBlank() && saved.translateAppKey.isNotBlank()) {
                putString(Constant.SAVED_TRANSLATE_APP_ID, saved.translateAppId)
                putString(Constant.SAVED_TRANSLATE_KEY, saved.translateAppKey)
            }
            if (saved.yuukaSetting.isNotBlank()) {
                putString(Constant.SAVED_YUUKA_SETTING, saved.yuukaSetting)
            }
            if (saved.amaduesSetting.isNotBlank()) {
                putString(Constant.SAVED_AMADEUS_SETTING, saved.amaduesSetting)
            }
            if (saved.atriSetting.isNotBlank()) {
                putString(Constant.SAVED_ATRI_SETTING, saved.atriSetting)
            }
            putString(Constant.SAVED_MEMORY_MODEL, saved.memoryModel)
            putBoolean(Constant.SAVED_USE_TRANSLATE, saved.translateSwitch)
            putBoolean(Constant.SAVED_USE_DARKMODE, saved.darkModeSwitch)
            apply()
        }
    }

    fun testConnection() {
        connectionJob?.cancel()
        val modelState = state.modelSettings
        val descriptor = ChatProviderFactory.descriptor(modelState.editorProvider)
        if (!descriptor.implemented || descriptor.capabilities.embedded) {
            modelState.connectionStatus = ConnectionTestStatus.UNAVAILABLE
            modelState.connectionDetail = null
            return
        }

        val testedProfile = modelState.profileOf()
        val testedProvider = modelState.editorProvider
        val selection = testedProfile.toSelection()
        if (testedProfile is LocalNetworkProviderProfile &&
            selection.providerConfig.baseUrl.isNullOrBlank()
        ) {
            modelState.connectionStatus = ConnectionTestStatus.ENDPOINT_INCOMPATIBLE
            return
        }
        modelState.connectionStatus = ConnectionTestStatus.TESTING
        modelState.connectionDetail = null
        connectionJob = viewModelScope.launch {
            var provider: ChatProvider? = null
            try {
                provider = providerCreator(selection.providerId, selection.providerConfig)
                provider.chat(
                    ChatRequest(
                        messages = listOf(ChatMessage.user(CONNECTION_TEST_PROMPT)),
                        model = selection.sessionOptions.model,
                        maxOutputTokens = CONNECTION_TEST_MAX_TOKENS,
                    )
                )
                if (modelState.editorProvider == testedProvider &&
                    modelState.profileOf(testedProvider) == testedProfile
                ) {
                    modelState.connectionStatus = ConnectionTestStatus.SUCCESS
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (modelState.editorProvider == testedProvider &&
                    modelState.profileOf(testedProvider) == testedProfile
                ) {
                    modelState.connectionStatus = mapConnectionFailure(e)
                    modelState.connectionDetail = null
                }
            } finally {
                provider?.close()
            }
        }
    }

    override fun onCleared() {
        connectionJob?.cancel()
    }

    private companion object {
        const val CONNECTION_TEST_PROMPT = "Reply with OK."
        const val CONNECTION_TEST_MAX_TOKENS = 8
    }
}

internal fun mapConnectionFailure(error: Throwable): ConnectionTestStatus = when (error) {
    is ChatError.Auth -> ConnectionTestStatus.AUTH_ERROR
    is ChatError.ModelNotFound -> ConnectionTestStatus.MODEL_NOT_FOUND
    is ChatError.Network, is ChatError.Timeout -> ConnectionTestStatus.NETWORK_ERROR
    is ChatError.Unknown, is ChatError.ServerError ->
        ConnectionTestStatus.ENDPOINT_INCOMPATIBLE
    else -> ConnectionTestStatus.UNKNOWN_ERROR
}
