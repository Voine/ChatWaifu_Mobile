#include <jni.h>
#include <string>
#include "ovr/include/OVRLipSync.h"
#include <android/log.h>
#include <vector>
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

static jclass g_LipSyncJniBridgeJavaClass;
jmethodID g_OnVisemeLoadDoneMethodId;

jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = env->FindClass("com/chatwaifu/lipsync/LipSyncJNI");
    g_LipSyncJniBridgeJavaClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    g_OnVisemeLoadDoneMethodId = env->GetMethodID(g_LipSyncJniBridgeJavaClass, "onLoadOneViseme",
                                                  "(Ljava/lang/String;)V");
    return JNI_VERSION_1_6;
}

template<unsigned N>
size_t getMaxElementIndex(const float (&array)[N]) {
    auto maxElement = std::max_element(array, array + N);
    return std::distance(array, maxElement);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1Initialize(JNIEnv *env, jobject thiz,
                                                             jint sample_size) {
    auto bufferSize = static_cast<unsigned int>(sample_size * 1e-2);
    auto result = ovrLipSync_Initialize(sample_size, bufferSize);
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
    jclass javaStrClz = env->FindClass("java/lang/String");
    jsize len = env->GetArrayLength(data);
    float *nativeAudioData = env->GetFloatArrayElements(data, nullptr);

    auto bufferSize = static_cast<unsigned int>(sample_size * 1e-2);
    std::vector<std::string> strList;
    for (auto offs(0u); offs + bufferSize < len; offs += bufferSize) {
        auto buffer = nativeAudioData + offs;
        auto rc = ovrLipSync_ProcessFrame(ctx, buffer, &frame);
        if (rc != ovrLipSyncSuccess) {
            LOGE("Failed to process audio frame");
            continue;
        }
        auto maxVisemeIndex = getMaxElementIndex(visemes);
        auto maxViseme = visemeNames[maxVisemeIndex];
        auto aViseme = env->NewStringUTF(maxViseme.c_str());
        env->CallVoidMethod(thiz, g_OnVisemeLoadDoneMethodId, aViseme);
        env->DeleteLocalRef(aViseme);
        strList.push_back(maxViseme);
    }
    jobjectArray result = env->NewObjectArray(strList.size(), javaStrClz, nullptr);

    for (int i = 0; i < strList.size(); i++) {
        jstring javaString = env->NewStringUTF(strList[i].c_str());
        env->SetObjectArrayElement(result, i, javaString);
        env->DeleteLocalRef(javaString);
    }

    return result;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1DestroyContext(JNIEnv *env, jobject thiz) {
    auto rc = ovrLipSync_DestroyContext(ctx);
    if (rc != ovrLipSyncSuccess) {
        LOGE("Failed to process ovrLipSync_DestroyContext:");
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_lipsync_LipSyncJNI_ovrLipSync_1ShutDown(JNIEnv *env, jobject thiz) {
    auto rc = ovrLipSync_Shutdown();
    if (rc != ovrLipSyncSuccess) {
        LOGE("Failed to process ovrLipSync_DestroyContext:");
    }
}