package com.chatwaifu.mobile.ui.companion

import android.annotation.SuppressLint
import android.app.Activity
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.View
import com.chatwaifu.live2d.GLRenderer
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.model.CharacterModel
import java.io.File

/**
 * Thin lifecycle adapter around the existing Live2D view and JNI bridge.
 * Only one instance may be active because the native renderer is a singleton.
 */
@SuppressLint("ClickableViewAccessibility")
class CharacterRendererHost(
    private val activity: Activity,
    private val onLoadDone: () -> Unit = {},
    private val onLoadError: (String) -> Unit = {},
    private val onTouch: ((View, MotionEvent) -> Boolean)? = null,
) {
    val view: GLSurfaceView = GLSurfaceView(activity).apply {
        setEGLContextClientVersion(2)
        setRenderer(GLRenderer())
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        onTouch?.let { handler ->
            setOnTouchListener { touchedView, event -> handler(touchedView, event) }
        }
    }

    private var active = true
    private var started = false
    private var character: CharacterModel? = null

    private val callback = object : JniBridgeJava.Live2DLoadInterface {
        override fun onLoadError() {
            dispatchToMain { onLoadError("Live2D 模型加载失败") }
        }

        override fun onLoadOneMotion(
            motionGroup: String?,
            index: Int,
            motionName: String?,
        ) = Unit

        override fun onLoadOneExpression(expressionName: String?, index: Int) = Unit

        override fun onLoadDone() {
            dispatchToMain(onLoadDone)
        }
    }

    init {
        JniBridgeJava.SetActivityInstance(activity)
        JniBridgeJava.SetContext(activity)
        JniBridgeJava.setLive2DLoadInterface(callback)
    }

    fun setCharacter(character: CharacterModel) {
        this.character = character
        if (started) loadCharacter(character)
    }

    fun onStart() {
        started = true
        JniBridgeJava.nativeOnStart()
        character?.let(::loadCharacter)
    }

    fun onResume() {
        view.onResume()
    }

    fun onPause() {
        view.onPause()
        JniBridgeJava.nativeOnPause()
    }

    fun onStop() {
        started = false
        JniBridgeJava.nativeOnStop()
    }

    fun release() {
        active = false
        JniBridgeJava.setLive2DLoadInterface(null)
        view.setOnTouchListener(null)
    }

    fun onDestroy() {
        release()
        JniBridgeJava.nativeOnDestroy()
    }

    private fun loadCharacter(character: CharacterModel) {
        val entry = File(character.live2dDir, character.live2dEntryFileName)
        if (!entry.isFile) {
            onLoadError("找不到 Live2D 入口文件：${character.live2dEntryFileName}")
            return
        }
        JniBridgeJava.nativeProjectChangeTo(
            character.live2dDir + File.separator,
            character.live2dEntryFileName,
        )
        if (character.storageKey == Constant.LOCAL_MODEL_AMADEUS) {
            JniBridgeJava.needRenderBack(false)
            JniBridgeJava.nativeApplyExpression("fix")
        } else {
            JniBridgeJava.needRenderBack(true)
        }
    }

    private fun dispatchToMain(action: () -> Unit) {
        activity.runOnUiThread {
            if (active && !activity.isDestroyed) action()
        }
    }
}
