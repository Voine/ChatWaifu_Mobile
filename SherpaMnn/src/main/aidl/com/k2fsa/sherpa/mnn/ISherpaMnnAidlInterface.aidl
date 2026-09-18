// ISherpaMnnAidlInterface.aidl
package com.k2fsa.sherpa.mnn;
import com.k2fsa.sherpa.mnn.ISherpaMnnSessionCallback;

interface ISherpaMnnAidlInterface {

    /** 装载模型，返回 SherpaMnnErrorCodes。调用方应放在后台线程。 */
    int prepare();

    int startSession(long sessionId, boolean endpointDetection, ISherpaMnnSessionCallback callback);

    /**
     * 音频来自 wavPath 指向的 16k/mono/16bit WAV 而不是麦克风。**只给验证用**，
     * 正式链路不会调它。换引擎时它是回归工具：同一段音频喂两个实现比对识别结果。
     */
    int startSessionFromWav(long sessionId, boolean endpointDetection, String wavPath, ISherpaMnnSessionCallback callback);

    /** 正常停止：把已识别内容作为 onFinalResult 发出 */
    void stopSession(long sessionId);

    /** 取消：不发 onFinalResult，只发 onEnded */
    void cancelSession(long sessionId);
}
