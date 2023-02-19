package com.chatwaifu.mobile.ui

import android.widget.Toast
import com.chatwaifu.mobile.application.ChatWaifuApplication

fun showToast(text: String){
    Toast.makeText(ChatWaifuApplication.context, text, Toast.LENGTH_SHORT).show()
}

val Int.dp : Int
get() {
    if (this <= 0) return 0
    val scale = ChatWaifuApplication.context.resources?.displayMetrics?.density ?: 0f
    return (this * scale + 0.5f).toInt()
}