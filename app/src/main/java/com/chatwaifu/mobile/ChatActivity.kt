package com.chatwaifu.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.chatwaifu.mobile.ui.base.ChatWaifuRootView
import com.chatwaifu.mobile.ui.base.NavigationItemType
import com.chatwaifu.mobile.ui.companion.CompanionActivity
import com.chatwaifu.mobile.ui.companion.CompanionDestination
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import kotlinx.coroutines.launch

/**
 * Description: 二级页面宿主。角色 / 聊天记录 / 记忆 / 设置 / 模型管理这几个 Fragment 住在这里。
 *
 * 2.0 起它**不再是聊天页**，也不再是启动后的第一站：根页面是 [CompanionActivity]，
 * 这里只从 Companion 的「更多」菜单进入（[intent] 指定落在哪一页），
 * 栈底再按返回 = 回 Companion。
 *
 * 和 Companion 是「交棒」不是「压栈」：Live2D renderer 和 BV2 loader 都是单例，
 * 两边不能同时持有，所以回去之前要先 [ChatActivityViewModel.releaseVoice]。
 */
class ChatActivity : AppCompatActivity() {

    private val chatViewModel: ChatActivityViewModel by lazy {
        ViewModelProvider(this)[ChatActivityViewModel::class.java]
    }

    private var startDestinationApplied = false
    private var returningHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk >= 35 系统已经强制 edge-to-edge，这里显式调用有两个作用：
        // 让 minSdk 29~34 上行为一致，以及把系统栏图标的明暗对比交给 androidx 处理。
        // 各屏幕的 inset 由 Compose 侧的 Scaffold contentWindowInsets 消费。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 进程恢复时 Navigation 自己会还原返回栈，不能再跳一次
        startDestinationApplied = savedInstanceState?.getBoolean(KEY_START_APPLIED) ?: false
        val destination = destinationOf(intent)

        // 先于 NavHostFragment 注册 = 优先级更低：Fragment 栈还能退时由 Navigation 处理，
        // 退到栈底才轮到这里
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToCompanion()
        })

        setContentView(
            ComposeView(this).apply {
                setContent {
                    ChatWaifu_MobileTheme {
                        ChatWaifuRootView(
                            chatViewModel = chatViewModel,
                            initialSelection = destination.navigationItem,
                            onCompanionClick = ::returnToCompanion,
                            onChannelListClick = { navigate(R.id.nav_channel_list) },
                            onChatLogClick = { navigate(R.id.nav_chat_log) },
                            onSettingClick = { navigate(R.id.nav_setting) },
                            onModelManagerClick = { navigate(R.id.nav_model_manager) },
                            onMemoryClick = { navigate(R.id.nav_memory) },
                        )
                    }
                    // AndroidViewBinding 在这一帧的组合里 inflate 了 NavHostFragment，
                    // effect 跑的时候 navController 已经可用
                    LaunchedEffect(Unit) { applyStartDestination(destination) }
                }
            }
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.navigateHome.collect { returnToCompanion() }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_START_APPLIED, startDestinationApplied)
    }

    private fun applyStartDestination(destination: CompanionDestination) {
        if (startDestinationApplied) return
        startDestinationApplied = true
        val target = destination.navId
        val navController = findNavController()
        if (navController.currentDestination?.id == target) return
        // 把图里默认的起点换掉，栈底就是用户要去的那一页，返回一次直接回 Companion
        navController.navigate(target, null, navOptions {
            popUpTo(navController.graph.id) { inclusive = true }
        })
    }

    private fun navigate(destinationId: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == destinationId) return
        navController.navigate(destinationId)
    }

    private fun returnToCompanion() {
        if (returningHome) return
        returningHome = true
        lifecycleScope.launch {
            // 试听那份 BV2 必须先放掉：Companion 一起来就会装自己那份
            chatViewModel.releaseVoice()
            startActivity(CompanionActivity.intent(this@ChatActivity))
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

    companion object {
        private const val EXTRA_DESTINATION = "destination"
        private const val KEY_START_APPLIED = "start_destination_applied"

        fun intent(context: Context, destination: CompanionDestination): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_DESTINATION, destination.name)

        private fun destinationOf(intent: Intent): CompanionDestination =
            intent.getStringExtra(EXTRA_DESTINATION)
                ?.let { name -> CompanionDestination.entries.firstOrNull { it.name == name } }
                ?: CompanionDestination.CHARACTERS

        private val CompanionDestination.navId: Int
            get() = when (this) {
                CompanionDestination.CHARACTERS -> R.id.nav_channel_list
                CompanionDestination.CHAT_LOG -> R.id.nav_chat_log
                CompanionDestination.MEMORY -> R.id.nav_memory
                CompanionDestination.SETTINGS -> R.id.nav_setting
            }

        private val CompanionDestination.navigationItem: NavigationItemType
            get() = when (this) {
                CompanionDestination.CHARACTERS -> NavigationItemType.TYPE_CHANNEL_LIST
                CompanionDestination.CHAT_LOG -> NavigationItemType.TYPE_CHAT_LOG
                CompanionDestination.MEMORY -> NavigationItemType.TYPE_MEMORY
                CompanionDestination.SETTINGS -> NavigationItemType.TYPE_SETTING
            }
    }
}
