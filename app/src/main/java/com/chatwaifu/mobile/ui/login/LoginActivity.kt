package com.chatwaifu.mobile.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chatwaifu.mobile.BuildConfig
import com.chatwaifu.mobile.ChatActivity
import com.chatwaifu.mobile.databinding.ActivityLoginBinding
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.vits.utils.permission.PermissionUtils

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    val sp:SharedPreferences by lazy {
        getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sp.getString(Constant.SAVED_CHAT_KEY, null) != null) {
            jumpToChat()
            return
        }

        jumpToNextInBuildIfNeed()

        binding.done.setOnClickListener {
            val chatKey = binding.chatGptText.text.toString().trim()
            if (chatKey.isEmpty()) {
                showToast("need chat gpt key..")
                return@setOnClickListener
            }
            if (!PermissionUtils.checkStoragePermission(this)) {
                PermissionUtils.requestStoragePermission(this)
                return@setOnClickListener
            }
            val translateKey = binding.translateKey?.text.toString().trim()
            val translateAppId = binding.translateAppId.text.toString().trim()
            sp.edit().apply {
                putString(Constant.SAVED_CHAT_KEY, chatKey)
                putString(Constant.SAVED_TRANSLATE_APP_ID, translateAppId)
                putString(Constant.SAVED_TRANSLATE_KEY, translateKey)
                apply()
            }
            jumpToChat()
        }
    }

    private fun jumpToChat() {
        Intent(this, ChatActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun jumpToNextInBuildIfNeed() {
        if (BuildConfig.CHAT_CHPT_KEY.isNotBlank()) {
            sp.edit().apply {
                putString(Constant.SAVED_CHAT_KEY, BuildConfig.CHAT_CHPT_KEY)
                putString(Constant.SAVED_TRANSLATE_APP_ID, BuildConfig.TRANSLATE_APP_ID)
                putString(Constant.SAVED_TRANSLATE_KEY, BuildConfig.TRANSLATE_KEY)
                apply()
            }
            jumpToChat()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            showToast("no permission...")
            finish()
        }
    }
}
