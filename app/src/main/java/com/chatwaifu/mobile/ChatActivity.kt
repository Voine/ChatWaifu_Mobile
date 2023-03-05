package com.chatwaifu.mobile

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.databinding.ActivityChatBinding
import com.chatwaifu.mobile.ui.channellist.ChannelListFragment
import com.chatwaifu.mobile.ui.chat.ChatFragment
import com.chatwaifu.mobile.ui.chatlog.ChatLogFragment
import com.chatwaifu.mobile.ui.setting.SettingFragment
import com.chatwaifu.mobile.ui.showToast

class ChatActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityChatBinding

    private val chatViewModel: ChatActivityViewModel by lazy {
        ViewModelProvider(this)[ChatActivityViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        showChannelListFragment()
        chatViewModel.refreshAllKeys()
        chatViewModel.mainLoop()
    }

    private fun showChannelListFragment() {
        binding.appBarChat.toolbar.post {
            title = "Character List"
        }
        val channelListFragment = ChannelListFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.host_fragment_content_chat, channelListFragment).commitAllowingStateLoss()
    }
    private fun initView() {
        setSupportActionBar(binding.appBarChat.toolbar)
        binding.appBarChat.toolbar.navigationIcon = resources.getDrawable(R.drawable.ic_nav_dark)
        binding.appBarChat.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navView.setNavigationItemSelectedListener(this)
    }

    fun showChatFragment() {
        binding.appBarChat.toolbar.post {
            title = chatViewModel.currentLive2DModelName
        }
        val chatFragment = ChatFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.host_fragment_content_chat, chatFragment).commitAllowingStateLoss()
    }

    private fun showSetting() {
        binding.appBarChat.toolbar.post {
            title = "Setting"
        }
        val setting = SettingFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.host_fragment_content_chat, setting).commitAllowingStateLoss()
    }

    private fun showChatLog() {
        binding.appBarChat.toolbar.post {
            title = "Chat Log"
        }
        val chatLog = ChatLogFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.host_fragment_content_chat, chatLog).commitAllowingStateLoss()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.drawer_channel_list -> {
                showChannelListFragment()
            }
            R.id.drawer_setting -> {
                showSetting()
            }
            R.id.drawer_chat_log -> {
                showChatLog()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
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