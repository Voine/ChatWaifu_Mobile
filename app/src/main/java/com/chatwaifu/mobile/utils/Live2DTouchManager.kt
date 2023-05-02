package com.chatwaifu.mobile.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.chatwaifu.live2d.JniBridgeJava

/**
 * Description: Live2DTouchManager
 * Author: Voine
 * Date: 2023/5/2
 */
class Live2DTouchManager(context: Context){

    private val scaleGestureDetector: ScaleGestureDetector
    private val mGestureDetector: GestureDetector
    private var currentTranslateX: Float = 0f
    private var currentTranslateY: Float = 0f
    private var currentScale: Float = 1f
    private var xLastPos: Float = 0f
    private var yLastPos: Float = 0f
    private var mLastPointerIndex: Int = -1

    init {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                return onScaleDetect(detector)
            }
        })
        mGestureDetector = GestureDetector(context, object: SimpleOnGestureListener(){})
    }

    private fun onScaleDetect(detector: ScaleGestureDetector): Boolean {
        val currentScale = detector.scaleFactor
        Log.d(TAG, "current scale is $currentScale")
        this.currentScale *= currentScale
        JniBridgeJava.nativeProjectScale(this.currentScale)
        return true
    }

    /**
     * handle origin touch event
     */
    private fun onTransformDetect(
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        currentTranslateY += distanceY
        currentTranslateX += distanceX
        Log.d(TAG, "transform detect $distanceX $distanceY $currentTranslateX $currentTranslateY")
        JniBridgeJava.nativeProjectTransformX(currentTranslateX)
        JniBridgeJava.nativeProjectTransformY(currentTranslateY)
        return false
    }

    fun handleTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        val actionMask = event.actionMasked
        val count = event.pointerCount
        if (actionMask == MotionEvent.ACTION_UP || (actionMask == MotionEvent.ACTION_POINTER_UP && mLastPointerIndex == event.actionIndex)) {
            mLastPointerIndex = -1
        }
        if (actionMask == MotionEvent.ACTION_DOWN) {
            mLastPointerIndex = -1
        }
        if (count >= 2) {
            return scaleGestureDetector.onTouchEvent(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_MOVE && count == 1) {
            // 当前正在移动;
            if (mLastPointerIndex == -1) {
                mLastPointerIndex = event.getPointerId(event.actionIndex)
                xLastPos = event.x
                yLastPos = event.y
            } else {
                val mCurrentIndex = event.getPointerId(event.actionIndex)
                if (mCurrentIndex != mLastPointerIndex) {
                    // 重新标记位置
                    xLastPos = event.x
                    yLastPos = event.y
                    mLastPointerIndex = mCurrentIndex
                } else {
                    // 移动模型
                    val currentX = event.x
                    val currentY = event.y
                    val xDiff = currentX - xLastPos
                    val yDiff = currentY - yLastPos
                    Log.i("onTranslate",
                        "raw $xLastPos, $yLastPos, $currentX, $currentY, $xDiff, $yDiff, $width, $height")
                    xLastPos = currentX
                    yLastPos = currentY
                    if (width > 0 || height > 0) {
                        // opengl坐标系和view坐标系有一点差异
                        onTransformDetect(xDiff / width, -yDiff / height)
                    }
                }
            }
        }

        // 手指抬起的时候销毁之前的数据
        if (count == 1 && event.action == MotionEvent.ACTION_UP) {
            mLastPointerIndex = -1
        }
        return true
    }

    fun getLive2DModelTouchParam(): List<Float> {
        return listOf(currentTranslateX, currentTranslateY, currentScale)
    }

    fun resetParams() {
        currentTranslateX = 0f
        currentTranslateY = 0f
        currentScale = 1f
        JniBridgeJava.nativeProjectTransformX(currentTranslateX)
        JniBridgeJava.nativeProjectTransformY(currentTranslateY)
        JniBridgeJava.nativeProjectScale(currentScale)
    }

    fun setInitParams(translateX: Float, translateY: Float, scale: Float) {
        currentTranslateX = translateX
        currentTranslateY = translateY
        currentScale = scale
        JniBridgeJava.nativeProjectTransformX(currentTranslateX)
        JniBridgeJava.nativeProjectTransformY(currentTranslateY)
        JniBridgeJava.nativeProjectScale(currentScale)
    }

    companion object {
        private const val TAG = "Live2DTouchManager"
    }
}