package com.chatwaifu.mobile.ui.setting

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.chat.ChatProviderSettings

/**
 * Description: SettingFragmentViewModel
 * Author: Voine
 * Date: 2023/4/26
 */
class SettingFragmentViewModel: ViewModel() {
    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }

    private val providerSettings: ChatProviderSettings by lazy {
        ChatProviderSettings(ChatWaifuApplication.context)
    }

    fun loadInitData(context: Context): SettingUIData {
        val data = SettingUIData()
        data.activeProvider = providerSettings.activeProviderId.key
        data.providerForms = ProviderId.entries.associate { it.key to providerSettings.form(it) }
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


        sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true).let {
            data.translateSwitch = it
        }

        data.memoryModel = sp.getString(Constant.SAVED_MEMORY_MODEL, null).orEmpty()
        sp.getBoolean(Constant.SAVED_USE_DARKMODE, false).let {
            data.darkModeSwitch = it
        }
        return data
    }

    fun saveData(saved: SettingUIData?) {
        saved ?: return

        // 基座配置走 ChatProviderSettings，不和其他设置混在一个 edit() 里
        ProviderId.fromKey(saved.activeProvider)?.let { providerSettings.activeProviderId = it }
        saved.providerForms.forEach { (key, form) ->
            ProviderId.fromKey(key)?.let { providerSettings.save(it, form) }
        }

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

            // 空串是**有意义的值**（= 跟随主模型），所以不像上面那几项那样 isNotBlank 才写，
            // 否则用户清空之后存不回去
            putString(Constant.SAVED_MEMORY_MODEL, saved.memoryModel)

            putBoolean(Constant.SAVED_USE_TRANSLATE, saved.translateSwitch)
            putBoolean(Constant.SAVED_USE_DARKMODE, saved.darkModeSwitch)
            if (!commit()) apply()
        }
    }
}