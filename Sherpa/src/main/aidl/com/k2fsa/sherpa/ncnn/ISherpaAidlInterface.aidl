// ISherpaAidlInterface.aidl
package com.k2fsa.sherpa.ncnn;
import com.k2fsa.sherpa.ncnn.ISherpaResultAidlCallback;
import com.k2fsa.sherpa.ncnn.ISherpaSessionCallback;

interface ISherpaAidlInterface {

    /**
     * 老接口：同步构造模型、无返回值、失败只能靠日志。
     * 保留是为了兼容仍在用它的旧聊天页；新代码走 prepare()。
     */
    void initSherpa();

    void startRecord();

    void finishRecord(ISherpaResultAidlCallback callback);

    /**
     * 装载模型，**返回是否成功**（SherpaAsrErrorCodes.OK 或某个错误码）。
     * 调用方应该放在后台线程 —— 首次会真的去读上百 MB 的 ncnn 权重。
     */
    int prepare();

    /**
     * 开一次带 partial 的录音。sessionId 由调用方分配，回调会带回来。
     * 重复调用会先把上一个 session 作废。
     */
    int startSession(long sessionId, boolean endpointDetection, ISherpaSessionCallback callback);

    /** 正常停止：把已识别内容作为 onFinalResult 发出 */
    void stopSession(long sessionId);

    /** 取消：不发 onFinalResult，只发 onEnded */
    void cancelSession(long sessionId);
}
