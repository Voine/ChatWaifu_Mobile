// ISherpaSessionCallback.aidl
package com.k2fsa.sherpa.ncnn;

/**
 * 一次录音的回调。以前只有 finishRecord 的一次性 onResult，
 * partial 和 endpoint 在 :sherpa 进程里被丢掉了 —— recognizer 本来每 20ms 就能给。
 *
 * sessionId 由调用方分配：跨进程回调天然可能迟到，收到旧 session 的回调必须能认出来丢弃。
 */
interface ISherpaSessionCallback {

    void onListeningStarted(long sessionId);

    /** 「到目前为止的完整文本」，不是增量 */
    void onPartialResult(long sessionId, String text);

    void onEndpoint(long sessionId);

    void onFinalResult(long sessionId, String text);

    /** code 是 SherpaAsrErrorCodes 里的值，不传原始异常 */
    void onError(long sessionId, int code);

    void onEnded(long sessionId);
}
