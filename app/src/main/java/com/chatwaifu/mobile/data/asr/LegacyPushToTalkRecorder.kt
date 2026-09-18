package com.chatwaifu.mobile.data.asr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.k2fsa.sherpa.ncnn.ISherpaAidlInterface
import com.k2fsa.sherpa.ncnn.ISherpaResultAidlCallback
import com.k2fsa.sherpa.ncnn.SherpaService

/**
 * Description: 老聊天页「按住说话」的 Sherpa 细节。
 *
 * **为什么不直接迁到 [AsrEngine]**：老页面的交互是「按住-松手-一次性出结果」，
 * 而新接口是为流式 partial 设计的；硬套过去等于给它加一堆用不到的语义。
 * 那一页在 Companion 正式接管后就会退场，所以这里只做一件事 ——
 * 把 `ISherpaAidlInterface` / `ServiceConnection` 从 ViewModel 里挪出来，
 * 让「上层不 import Sherpa 具体类型」这条规则在整个 app 成立。
 *
 * 新代码不要用这个类，用 [AsrEngine]。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
internal class LegacyPushToTalkRecorder {

    private var service: ISherpaAidlInterface? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ISherpaAidlInterface.Stub.asInterface(binder).also {
                runCatching { it.initSherpa() }
                    .onFailure { e -> Log.e(TAG, "init sherpa failed", e) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun bind(context: Context) {
        runCatching {
            context.bindService(
                Intent(context, SherpaService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        }.onFailure { Log.e(TAG, "bind sherpa failed", it) }
    }

    fun unbind(context: Context) {
        runCatching { context.unbindService(connection) }
            .onFailure { Log.w(TAG, "unbind sherpa failed", it) }
        service = null
    }

    fun start() {
        runCatching { service?.startRecord() }
            .onFailure { Log.e(TAG, "start record failed", it) }
    }

    fun finish(onResult: (String?) -> Unit) {
        runCatching {
            service?.finishRecord(object : ISherpaResultAidlCallback.Stub() {
                override fun onResult(result: String?) {
                    Log.d(TAG, "record result length=${result?.length}")
                    onResult(result)
                }
            })
        }.onFailure { Log.e(TAG, "finish record failed", it) }
    }

    private companion object {
        const val TAG = "LegacyPushToTalk"
    }
}
