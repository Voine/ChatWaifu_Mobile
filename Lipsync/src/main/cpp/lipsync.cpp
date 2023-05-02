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
std::string visemeNames[ovrLipSyncViseme_Count] = {
        "sil", "PP", "FF", "TH", "DD",
        "kk", "CH", "SS", "nn", "RR",
        "aa", "E", "ih", "oh", "ou",
};
template<unsigned N>
size_t getMaxElementIndex(const float (&array)[N]) {
    auto maxElement = std::max_element(array, array + N);
    return std::distance(array, maxElement);
}

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
JNIEXPORT int JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1CreateContextEx(JNIEnv *env, jobject thiz,
                                                                  jint sample_size,
                                                                  jboolean enable_acceleration) {
    auto rc = ovrLipSync_CreateContextEx(&ctx, ovrLipSyncContextProvider_Enhanced, sample_size,
                                         true);
    if (rc !=  ovrLipSyncSuccess) {
        LOGE("Failed to create ovrLipSync context ");
        return -1;
    }
    return 0;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1ProcessFrame(JNIEnv *env, jobject thiz,
                                                               jint sample_size,
                                                               jfloatArray data,
                                                               jint data_size) {
    ovrLipSyncFrame frame = {};
    float visemes[ovrLipSyncViseme_Count] = {0.0f};
    frame.visemes = visemes;
    frame.visemesLength = ovrLipSyncViseme_Count;
    auto rc = ovrLipSync_ProcessFrame(ctx, reinterpret_cast<const float *>(data), &frame);
    if (rc != ovrLipSyncSuccess) {
        LOGE("Failed to create ovrLipSync context ");
        //TODO
    }
    auto bufferSize = static_cast<unsigned int>(sample_size * 1e-2);
    for (auto offs(0u); offs + bufferSize < data_size; offs += bufferSize) {
        rc = ovrLipSync_ProcessFrame(ctx, reinterpret_cast<const float *>(data) + offs, &frame);
        if (rc != ovrLipSyncSuccess) {
            LOGE("Failed to process audio frame:");
            //TODO return -1;
        }
        auto maxViseme = getMaxElementIndex(visemes);
        visemeNames[maxViseme];
        //TODO
    }
}

