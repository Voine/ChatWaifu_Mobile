package com.chatwaifu.mobile.data.asr

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Description: 录音期间的音频焦点。**最小实现，不是媒体框架。**
 *
 * 改造前全工程一处 AudioFocus 都没有：TTS 的 AudioTrack 和 ASR 的 AudioRecord
 * 谁也不知道对方存在。这里只解决一件事 —— 录音时让别的应用（音乐、视频）让位，
 * 录完还回去。**本应用自己的 TTS 冲突不靠焦点解决**，靠 Companion 侧显式
 * 「先停 TTS 再录音」（见 `CompanionViewModel.startListening`）：
 * 焦点是给外部应用看的协作信号，不是内部互斥锁。
 *
 * 拿 `GAIN_TRANSIENT_EXCLUSIVE`：录音是短暂且排他的，不希望别人这期间还在响。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
class AudioFocusGate(context: Context) {

    private val appContext = context.applicationContext

    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var request: AudioFocusRequest? = null

    /** @return 是否拿到焦点。**拿不到也不阻止录音** —— 用户明确按了麦克风。 */
    fun acquire(): Boolean {
        val manager = audioManager ?: return false
        if (request != null) return true
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            // 焦点丢了不做处理：录音是用户主动发起的短操作，
            // 中途被抢焦点时继续录比突然静默更符合预期
            .setOnAudioFocusChangeListener { }
            .build()
        request = focusRequest
        val result = runCatching { manager.requestAudioFocus(focusRequest) }
            .getOrElse {
                Log.w(TAG, "requestAudioFocus failed", it)
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            request = null
            return false
        }
        return true
    }

    /** 幂等。录音结束的所有路径（停止 / 取消 / 出错 / 页面退出）都必须走到这里。 */
    fun release() {
        val manager = audioManager ?: return
        val focusRequest = request ?: return
        request = null
        runCatching { manager.abandonAudioFocusRequest(focusRequest) }
            .onFailure { Log.w(TAG, "abandonAudioFocus failed", it) }
    }

    private companion object {
        const val TAG = "AudioFocusGate"
    }
}
