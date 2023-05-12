package com.chatwaifu.mobile.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.utils.LipsValueHandler
import com.chatwaifu.mobile.utils.Live2DTouchManager
import com.chatwaifu.mobile.utils.LocalModelManager
import com.k2fsa.sherpa.ncnn.ISherpaAidlInterface
import com.k2fsa.sherpa.ncnn.ISherpaResultAidlCallback
import com.k2fsa.sherpa.ncnn.SherpaHelper
import com.k2fsa.sherpa.ncnn.SherpaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Description: ChatFragmentViewModel
 * Author: Voine
 * Date: 2023/5/2
 */
class ChatFragmentViewModel: ViewModel() {

    private val touchManager: Live2DTouchManager by lazy {
        Live2DTouchManager(ChatWaifuApplication.context)
    }

    private val localModelManager: LocalModelManager by lazy {
        LocalModelManager()
    }

    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(
            Constant.SAVED_STORE,
            Context.MODE_PRIVATE
        )
    }

    private var sherpaService: ISherpaAidlInterface? = null
    private val sherpaConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sherpaService = ISherpaAidlInterface.Stub.asInterface(service)
            Log.d(TAG,"onServiceConnected, init sherpa")
            sherpaService?.initSherpa()
            Log.d(TAG,"onServiceConnected, init sherpa finish")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sherpaService = null
        }
    }

    fun bindSherpa(context: Context) {
        try {
            val intent = Intent(context, SherpaService::class.java)
            context.bindService(intent, sherpaConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbindSherpa(context: Context) {
        try {
            context.unbindService(sherpaConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initTouch(modelName: String) {
        val defaultPosition = localModelManager.getDefaultModelPosition(modelName)
        val translateX = sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_X_PREFIX}$modelName",defaultPosition[0])
        val translateY = sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_Y_PREFIX}$modelName",defaultPosition[1])
        val scale = sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_SCALE_PREFIX}$modelName",defaultPosition[2])
        touchManager.setInitParams(translateX, translateY, scale)
    }

    /**
     * list: translationX translationY scale
     */
    fun saveTouch(modelName: String){
        val params = touchManager.getLive2DModelTouchParam()
        sp.edit().apply {
            putFloat("${Constant.LOCAL_MODEL_TRANSLATE_X_PREFIX}$modelName", params[0])
            putFloat("${Constant.LOCAL_MODEL_TRANSLATE_Y_PREFIX}$modelName", params[1])
            putFloat("${Constant.LOCAL_MODEL_TRANSLATE_SCALE_PREFIX}$modelName", params[2])
            if(!commit()) apply()
        }
    }

    fun resetModel() {
        touchManager.resetParams()
    }

    fun handleTouchEvent(event: MotionEvent?, width: Int?, height: Int?): Boolean {
        event ?: return false
        width ?: return false
        height ?: return false
        return touchManager.handleTouch(event, width, height)
    }

    fun onRecordStart() {
        Log.d(TAG,"start record")
        try {
            sherpaService?.startRecord()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onRecordEnd(recognizeCallback: (result: String?) -> Unit) {
        try {
            sherpaService?.finishRecord(object : ISherpaResultAidlCallback.Stub() {
                override fun onResult(result: String?) {
                    Log.d(TAG, "record result is $result")
                    if (result.isNullOrBlank()) {
                        showToast(ChatWaifuApplication.context.resources.getString(R.string.chat_record_too_short))
                        return
                    }
                    recognizeCallback(result)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "ChatFragmentViewModel"
    }
}