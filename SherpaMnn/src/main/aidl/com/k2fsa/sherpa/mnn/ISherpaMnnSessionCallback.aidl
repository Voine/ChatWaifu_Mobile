// ISherpaMnnSessionCallback.aidl
package com.k2fsa.sherpa.mnn;

/**
 * 一次录音的回调。形状和 Sherpa-ncnn 那套刻意保持一致，
 * 这样 app 侧的 AsrEngine 适配层只是换一个 binder，不用改语义。
 *
 * sessionId 由调用方分配：跨进程回调天然可能迟到，收到旧 session 的回调必须能认出来丢弃。
 */
interface ISherpaMnnSessionCallback {

    void onListeningStarted(long sessionId);

    /** 「到目前为止的完整文本」，不是增量 */
    void onPartialResult(long sessionId, String text);

    void onEndpoint(long sessionId);

    void onFinalResult(long sessionId, String text);

    /** code 是 SherpaMnnErrorCodes 里的值，不传原始异常 */
    void onError(long sessionId, int code);

    void onEnded(long sessionId);
}
