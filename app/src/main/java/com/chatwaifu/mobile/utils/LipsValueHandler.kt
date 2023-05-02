package com.chatwaifu.mobile.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Message


/**
 * Description: render lip sync values
 * Author: Voine
 * Date: 2023/5/2
 */
class LipsValueHandler {
    val handler: Handler
    init {
        val handlerThread = HandlerThread("LipsValueHandler")
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                onHandleMessage(msg)
            }
        }

    }
    fun onHandleMessage(msg: Message) {

    }

    fun sendLipsValues(array: FloatArray) {
        handler.sendMessage(Message.obtain(handler, 0, array))
    }
}