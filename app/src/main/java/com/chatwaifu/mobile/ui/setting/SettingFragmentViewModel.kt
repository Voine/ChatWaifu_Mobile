package com.chatwaifu.mobile.ui.setting

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant

/**
 * Description: SettingFragmentViewModel
 * Author: Voine
 * Date: 2023/4/26
 */
class SettingFragmentViewModel: ViewModel() {
    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }

    fun loadInitData(context: Context): SettingUIData {
        val data = SettingUIData()
        sp.getString(Constant.SAVED_CHAT_KEY, null)?.let {
            data.chatGPTAppId = it
        }
        sp.getString(Constant.SAVED_TRANSLATE_APP_ID, null)?.let {
            data.translateAppId = it
        }
        sp.getString(Constant.SAVED_TRANSLATE_KEY, null)?.let {
            data.translateAppKey = it
        }

        data.yuukaSetting = sp.getString(Constant.SAVED_YUUKA_SETTING, null)
            ?: context.resources.getString(R.string.default_system_yuuka)

        data.amaduesSetting = sp.getString(Constant.SAVED_AMADEUS_SETTING, null)
            ?: context.resources.getString(R.string.default_system_amadeus)

        data.atriSetting = sp.getString(Constant.SAVED_ATRI_SETTING, null)
            ?: context.resources.getString(R.string.default_system_atri)


        sp.getString(Constant.SAVED_EXTERNAL_SETTING, null)?.let {
            data.externalSetting = it
        }

        sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true).let {
            data.translateSwitch = it
        }

        sp.getBoolean(Constant.SAVED_USE_DARKMODE, false).let {
            data.darkModeSwitch = it
        }
        sp.getBoolean(Constant.SAVED_USE_CHATGPT_PROXY, false).let {
            data.gptProxySwitch = it
        }
        sp.getString(Constant.SAVED_USE_CHATGPT_PROXY_URL, null)?.let {
            data.gptProxyUrl = it
        }
        return data
    }

    fun saveData(saved: SettingUIData?) {
        saved ?: return
        sp.edit().apply {
            if (saved.chatGPTAppId.isNotBlank()) {
                putString(Constant.SAVED_CHAT_KEY, saved.chatGPTAppId)
            }
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

            if (saved.externalSetting.isNotBlank()) {
                putString(Constant.SAVED_EXTERNAL_SETTING, saved.externalSetting)
            }

            putBoolean(Constant.SAVED_USE_TRANSLATE, saved.translateSwitch)
            putBoolean(Constant.SAVED_USE_DARKMODE, saved.darkModeSwitch)
            putInt(Constant.SAVED_EXTERNAL_MODEL_SPEAKER_ID, saved.externalModelSpeakerId)
            putBoolean(Constant.SAVED_USE_CHATGPT_PROXY, saved.gptProxySwitch)
            if (!saved.gptProxyUrl.isNullOrBlank()) {
                putString(Constant.SAVED_USE_CHATGPT_PROXY_URL, saved.gptProxyUrl)
            }
            if (!commit()) apply()
        }
    }
}