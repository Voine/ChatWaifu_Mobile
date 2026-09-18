package com.k2fsa.sherpa.mnn

/**
 * Description: 跨 AIDL 传的错误码。数值和 Sherpa-ncnn 那套保持一致，
 * 这样 app 侧的翻译表可以共用同一组语义。
 *
 * 走 int 而不是序列化异常：原始异常信息里会带模型绝对路径，不该出现在 UI 上。
 *
 * Author: Voine
 * Date: 2026/10/13
 */
object SherpaMnnErrorCodes {
    const val OK = 0
    const val MODEL_UNAVAILABLE = 1
    const val INITIALIZATION_FAILED = 2
    const val AUDIO_RECORD_FAILED = 3
    const val RECOGNITION_FAILED = 4
    const val NOT_PREPARED = 5
    const val PERMISSION_DENIED = 6
    const val UNKNOWN = 99
}
