/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include <jni.h>
#include "JniBridgeC.hpp"
#include "LAppDelegate.hpp"
#include "LAppPal.hpp"

using namespace Csm;

static JavaVM *g_JVM; // JavaVM is valid for all threads, so just save it globally
static jclass g_JniBridgeJavaClass;
static jmethodID g_LoadFileMethodId;
static jmethodID g_MoveTaskToBackMethodId;
static jmethodID g_OnLoadErrorMethodId;
static jmethodID g_OnLoadDoneMethodId;
static jmethodID g_OnLoadOneMotionMethodId;
static jmethodID g_OnLoadOneExpressionMethodId;

JNIEnv *GetEnv() {
    JNIEnv *env = NULL;
    g_JVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    return env;
}

// The VM calls JNI_OnLoad when the native library is loaded
jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_JVM = vm;

    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/chatwaifu/live2d/JniBridgeJava");
    g_JniBridgeJavaClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    g_LoadFileMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "LoadFile",
                                                "(Ljava/lang/String;)[B");
    g_MoveTaskToBackMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "MoveTaskToBack",
                                                      "()V");
    g_OnLoadErrorMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "OnLoadError", "()V");
    g_OnLoadDoneMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "OnLoadDone", "()V");
    g_OnLoadOneMotionMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "OnLoadOneMotion",
                                                       "(Ljava/lang/String;ILjava/lang/String;)V");
    g_OnLoadOneExpressionMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass,
                                                           "OnLoadOneExpression",
                                                           "(Ljava/lang/String;I)V");

    return JNI_VERSION_1_6;
}

void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = GetEnv();
    env->DeleteGlobalRef(g_JniBridgeJavaClass);
}

char *JniBridgeC::LoadFileAsBytesFromJava(const char *filePath, unsigned int *outSize) {
    JNIEnv *env = GetEnv();

    // ファイルロード
    jbyteArray obj = (jbyteArray) env->CallStaticObjectMethod(g_JniBridgeJavaClass,
                                                              g_LoadFileMethodId,
                                                              env->NewStringUTF(filePath));
    *outSize = static_cast<unsigned int>(env->GetArrayLength(obj));

    char *buffer = new char[*outSize];
    env->GetByteArrayRegion(obj, 0, *outSize, reinterpret_cast<jbyte *>(buffer));

    return buffer;
}

void JniBridgeC::MoveTaskToBack() {
    JNIEnv *env = GetEnv();

    // アプリ終了
    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_MoveTaskToBackMethodId, NULL);
}

void JniBridgeC::OnLoadError() {
    JNIEnv *env = GetEnv();

    // モデルロードエラー
    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_OnLoadErrorMethodId);
}

void JniBridgeC::OnLoadDone() {
    JNIEnv *env = GetEnv();

    // モデルロード完成
    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_OnLoadDoneMethodId);
}

void JniBridgeC::OnLoadOneMotion(const char *motionGroup, int index, const char *motionName) {
    JNIEnv *env = GetEnv();

    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_OnLoadOneMotionMethodId,
                              env->NewStringUTF(motionGroup), index, env->NewStringUTF(motionName));

}

void JniBridgeC::OnLoadOneExpression(const char *expressionName, int index) {
    JNIEnv *env = GetEnv();
    env->CallStaticVoidMethod(g_JniBridgeJavaClass, g_OnLoadOneExpressionMethodId,
                              env->NewStringUTF(expressionName), index);

}

extern "C"
{
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnStart(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnStart();
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnPause(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnPause();
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnStop(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnStop();
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnDestroy(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnDestroy();
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnSurfaceCreated(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->OnSurfaceCreate();
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnSurfaceChanged(JNIEnv *env, jclass type, jint width,
                                                               jint height) {
    LAppDelegate::GetInstance()->OnSurfaceChanged(width, height);
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnDrawFrame(JNIEnv *env, jclass type) {
    LAppDelegate::GetInstance()->Run();
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnTouchesBegan(JNIEnv *env, jclass type,
                                                             jfloat pointX, jfloat pointY) {
    LAppDelegate::GetInstance()->OnTouchBegan(pointX, pointY);
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnTouchesEnded(JNIEnv *env, jclass type,
                                                             jfloat pointX, jfloat pointY) {
    LAppDelegate::GetInstance()->OnTouchEnded(pointX, pointY);
}

JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeOnTouchesMoved(JNIEnv *env, jclass type,
                                                             jfloat pointX, jfloat pointY) {
    LAppDelegate::GetInstance()->OnTouchMoved(pointX, pointY);
}
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeProjectChangeTo(JNIEnv *env, jclass clazz,
                                                              jstring model_path,
                                                              jstring model_json_file_name) {
    auto modelPathStr = env->GetStringUTFChars(model_path, nullptr);
    auto modelJsonFileNameStr = env->GetStringUTFChars(model_json_file_name, nullptr);
    LAppDelegate::GetInstance()->ModelChangeTo(modelPathStr, modelJsonFileNameStr);
    env->ReleaseStringUTFChars(model_path, modelPathStr);
    env->ReleaseStringUTFChars(model_json_file_name, modelJsonFileNameStr);
}
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeApplyExpression(JNIEnv *env, jclass clazz,
                                                              jstring expression_name) {
    auto expressionName = env->GetStringUTFChars(expression_name, nullptr);
    LAppDelegate::GetInstance()->ApplyExpression(expressionName);
    env->ReleaseStringUTFChars(expression_name, expressionName);
}
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_needRenderBack(JNIEnv *env, jclass clazz, jboolean back) {
    LAppDelegate::GetInstance()->NeedRenderBack(back == JNI_TRUE);
}
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeProjectScale(JNIEnv *env, jclass clazz,
                                                           jfloat scale) {
    LAppDelegate::GetInstance()->ModelResize(scale);
}
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeProjectTransformX(JNIEnv *env, jclass clazz,
                                                                jfloat transform) {
    LAppDelegate::GetInstance()->ModelTranslateX(transform);
}
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeProjectTransformY(JNIEnv *env, jclass clazz,
                                                                jfloat transform) {
    LAppDelegate::GetInstance()->ModelTranslateY(transform);
}
}
extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeAutoBlinkEyes(JNIEnv *env, jclass clazz,
                                                            jboolean enabled) {
    LAppDelegate::GetInstance()->ModelAutoBlinkEyes(enabled == JNI_TRUE);

}
extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeProjectMouthForm(JNIEnv *env, jclass clazz,
                                                               jfloat value) {
    LAppDelegate::GetInstance()->ModelMouthForm(value);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_chatwaifu_live2d_JniBridgeJava_nativeProjectMouthOpenY(JNIEnv *env, jclass clazz,
                                                                jfloat value) {
    LAppDelegate::GetInstance()->ModelMouthOpenY(value);
}