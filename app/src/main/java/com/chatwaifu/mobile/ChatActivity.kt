package com.chatwaifu.mobile

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import com.chatwaifu.mobile.databinding.ActivityChatBinding
import com.chatwaifu.mobile.ui.ChatFragment
import com.chatwaifu.mobile.ui.PrivateKeyDialogFragment
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.vits.utils.permission.PermissionUtils

class ChatActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityChatBinding

    private val chatViewModel: ChatActivityViewModel by lazy {
        ViewModelProvider(this)[ChatActivityViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarChat.toolbar)
        binding.appBarChat.toolbar.setTitleTextColor(Color.TRANSPARENT)
        binding.appBarChat.toolbar.navigationIcon = resources.getDrawable(R.drawable.ic_nav_dark)
        binding.appBarChat.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navView.setNavigationItemSelectedListener(this)
        showBaseFragment()
        chatViewModel.mainLoop()
        if (!PermissionUtils.checkStoragePermission(this)) {
            PermissionUtils.requestStoragePermission(this)
        } else {
            chatViewModel.initYuuka()
        }
    }

    private fun showBaseFragment() {
        val chatFragment = ChatFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.host_fragment_content_chat, chatFragment).commitAllowingStateLoss()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_add_key ->{
                val dialog = PrivateKeyDialogFragment.newInstance()
                dialog.isCancelable = true
                dialog.show(supportFragmentManager, null)
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
        } else {
            chatViewModel.initYuuka()
        }
    }
}