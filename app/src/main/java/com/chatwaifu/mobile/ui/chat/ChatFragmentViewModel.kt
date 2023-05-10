package com.chatwaifu.mobile.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.utils.LipsValueHandler
import com.chatwaifu.mobile.utils.Live2DTouchManager

/**
 * Description: ChatFragmentViewModel
 * Author: Voine
 * Date: 2023/5/2
 */
class ChatFragmentViewModel: ViewModel() {

    private val touchManager: Live2DTouchManager by lazy {
        Live2DTouchManager(ChatWaifuApplication.context)
    }

    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(
            Constant.SAVED_STORE,
            Context.MODE_PRIVATE
        )
    }

    fun initTouch(modelName: String) {
        val translateX = sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_X_PREFIX}$modelName",0f)
        val translateY = sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_Y_PREFIX}$modelName",0f)
        val scale = sp.getFloat("${Constant.LOCAL_MODEL_TRANSLATE_SCALE_PREFIX}$modelName",1f)
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
}