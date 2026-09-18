package com.chatwaifu.mobile.ui.chat

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.utils.LipsValueHandler
import com.chatwaifu.mobile.utils.Live2DTouchManager
import com.chatwaifu.mobile.data.asr.LegacyPushToTalkRecorder
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

    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(
            Constant.SAVED_STORE,
            Context.MODE_PRIVATE
        )
    }

    /**
     * 老聊天页的「按住说话」。**不再直接持有 Sherpa 类型和 ServiceConnection** ——
     * 那些细节搬进了 [LegacyPushToTalkRecorder]。
     *
     * 这一页刻意没有迁到 `AsrEngine`：它的交互是「按住-松手」一次性出结果，
     * 而新接口是为流式 partial 设计的。老页面在 Companion 正式接管后就会退场，
     * 现在只把 Sherpa 的具体类型从 ViewModel 里挪走，不改它的行为。
     */
    private val recorder: LegacyPushToTalkRecorder by lazy { LegacyPushToTalkRecorder() }

    fun bindSherpa(context: Context) = recorder.bind(context)

    fun unbindSherpa(context: Context) = recorder.unbind(context)

    fun initTouch(modelName: String) {
        val defaultPosition = Live2DTouchManager.getDefaultModelPosition(modelName)
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
        Log.d(TAG, "start record")
        recorder.start()
    }

    fun onRecordEnd(recognizeCallback: (result: String?) -> Unit) {
        recorder.finish { result ->
            if (result.isNullOrBlank()) {
                showToast(
                    ChatWaifuApplication.context.resources
                        .getString(R.string.chat_record_too_short)
                )
                return@finish
            }
            recognizeCallback(result)
        }
    }

    companion object {
        private const val TAG = "ChatFragmentViewModel"
    }
}