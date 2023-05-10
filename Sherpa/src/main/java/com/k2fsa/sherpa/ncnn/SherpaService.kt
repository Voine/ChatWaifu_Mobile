package com.k2fsa.sherpa.ncnn

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Description: Sherpa Remote Service
 * Author: Voine
 * Date: 2023/5/10
 */
class SherpaService : Service() {
    companion object {
        private const val TAG  = "SherpaService"
    }
    private val sherpaHelper by lazy {
        SherpaHelper(this)
    }

    private val binder: ISherpaAidlInterface.Stub = object : ISherpaAidlInterface.Stub(){
        override fun initSherpa(){
            Log.d(TAG, "init sherpa")
            sherpaHelper
        }

        override fun startRecord() {
            Log.d(TAG, "start record")
            sherpaHelper.startRecord()
        }

        override fun finishRecord(callback: ISherpaResultAidlCallback?) {
            sherpaHelper.stopRecord {
                Log.d(TAG,"recognize finish $it")
                callback?.onResult(it)
            }
        }
    }

    override fun onDestroy() {
        sherpaHelper.releaseRecord()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder
}