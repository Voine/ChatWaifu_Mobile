package com.chatwaifu.mobile.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chatwaifu.mobile.BuildConfig
import com.chatwaifu.mobile.ChatActivity
import com.chatwaifu.mobile.databinding.ActivityLoginBinding
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.showToast

/**
 * 不要问为甚么登陆页面不用 compose 画，问就是懒
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    val sp:SharedPreferences by lazy {
        getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root)

        if (jumpToNextInBuildIfNeed()) {
            return
        }

        if (sp.getString(Constant.SAVED_CHAT_KEY, null) != null) {
            jumpToChat()
            return
        }

        binding.done.setOnClickListener {
            val chatKey = binding.chatGptText.text.toString().trim()
            if (chatKey.isEmpty()) {
                showToast("need chat gpt key..")
                return@setOnClickListener
            }
            val translateKey = binding.translateKey.text.toString().trim()
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

    /**
     * 这一屏是 View 布局，edge-to-edge 下系统不会再自动补 status/navigation bar 的 inset，
     * 得自己监听。注意是**叠加**到布局原有 padding 上而不是覆盖，否则
     * activity_login.xml 里的 activity_horizontal_margin 会被冲掉。
     * ime 也一起吃掉：decorFitsSystemWindows=false 之后窗口不再自动 resize，
     * 键盘弹出时只会来 inset，不加的话三个输入框会被挡住。
     */
    private fun applyWindowInsets(root: View) {
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(
                baseLeft + insets.left,
                baseTop + insets.top,
                baseRight + insets.right,
                baseBottom + insets.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun jumpToChat() {
        Intent(this, ChatActivity::class.java).apply {
            startActivity(this)
        }
        finish()
    }

    private fun jumpToNextInBuildIfNeed(): Boolean {
        if (BuildConfig.CHAT_CHPT_KEY.isNotBlank()) {
            val editor = sp.edit()
            editor.apply {
                putString(Constant.SAVED_CHAT_KEY, BuildConfig.CHAT_CHPT_KEY)
                putString(Constant.SAVED_TRANSLATE_APP_ID, BuildConfig.TRANSLATE_APP_ID)
                putString(Constant.SAVED_TRANSLATE_KEY, BuildConfig.TRANSLATE_KEY)
            }
            if (!editor.commit()) {
                editor.apply()
            } else {
                jumpToChat()
            }
            return true
        }
        return false
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
