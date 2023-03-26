#include <jni.h>
#include <string>
#include <stdint.h>
#include "ovr/include/OVRLipSync.h"
#include <android/log.h>
#define TAG "LipSync" // ������Զ����LOG�ı�ʶ
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // ����LOGD����
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // ����LOGI����
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // ����LOGW����
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // ����LOGE����
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // ����LOGF����

ovrLipSyncContext ctx;

extern "C" JNIEXPORT jstring JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1Initialize(JNIEnv *env, jobject thiz,
                                                             jint sample_size, jint buffer_size) {
    auto result = ovrLipSync_Initialize(sample_size, buffer_size);
    LOGD("lip sync init result is %d",result);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1CreateContextEx(JNIEnv *env, jobject thiz,
                                                                  jint sample_size,
                                                                  jboolean enable_acceleration) {

}
extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1ProcessFrame(JNIEnv *env, jobject thiz,
                                                               jfloatArray data) {
}