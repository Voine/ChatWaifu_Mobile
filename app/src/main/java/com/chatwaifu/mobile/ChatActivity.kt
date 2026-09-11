package com.chatwaifu.mobile

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import com.chatwaifu.mobile.ui.base.ChatWaifuRootView
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

class ChatActivity : AppCompatActivity() {

    private val chatViewModel: ChatActivityViewModel by lazy {
        ViewModelProvider(this)[ChatActivityViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk >= 35 系统已经强制 edge-to-edge，这里显式调用有两个作用：
        // 让 minSdk 24~34 上行为一致，以及把系统栏图标的明暗对比交给 androidx 处理。
        // 各屏幕的 inset 由 Compose 侧的 Scaffold contentWindowInsets 消费。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    ChatWaifu_MobileTheme {
                        ChatWaifuRootView(
                            chatViewModel = chatViewModel,
                            onChannelListClick = {
                                findNavController().navigate(R.id.nav_channel_list)
                            },
                            onChatLogClick = {
                                findNavController().navigate(R.id.nav_chat_log)
                            },
                            onSettingClick = {
                                findNavController().navigate(R.id.nav_setting)
                            },
                            onModelManagerClick = {
                                findNavController().navigate(R.id.nav_model_manager)
                            },
                            onMemoryClick = {
                                findNavController().navigate(R.id.nav_memory)
                            }
                        )
                    }
                }
            }
        )
        chatViewModel.refreshAllKeys()
        chatViewModel.mainLoop()
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

    override fun onSupportNavigateUp(): Boolean {
        return findNavController().navigateUp() || super.onSupportNavigateUp()
    }

    private fun findNavController(): NavController {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }
}