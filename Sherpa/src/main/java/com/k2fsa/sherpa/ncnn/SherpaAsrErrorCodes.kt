package com.k2fsa.sherpa.ncnn

/**
 * Description: 跨 AIDL 传的错误码。
 *
 * 走 int 而不是把异常序列化过去：Binder 那头的原始异常类型对调用方没有意义，
 * 而且 ncnn 的异常信息里会带模型绝对路径，不该出现在 UI 上。
 * 原始异常在 `:sherpa` 进程里记日志，跨进程只过一个码。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
object SherpaAsrErrorCodes {
    const val OK = 0
    const val MODEL_UNAVAILABLE = 1
    const val INITIALIZATION_FAILED = 2
    const val AUDIO_RECORD_FAILED = 3
    const val RECOGNITION_FAILED = 4
    const val NOT_PREPARED = 5
    const val PERMISSION_DENIED = 6
    const val UNKNOWN = 99
}
