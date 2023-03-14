#ifndef MOERENG_MANAGER_H
#define MOERENG_MANAGER_H
#include <iostream>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

using namespace std;

struct AssetJNI {
    JNIEnv* env;
    jobject obj;
    jobject assetManager;
    AssetJNI(JNIEnv* _env, jobject _obj, jobject _assetManager){
        env = _env;
        obj = _obj;
        assetManager = _assetManager;
    }
};

unsigned char* asset_loader(const char* fileName, AssetJNI* asjni, int* fd, size_t *length);

#endif //MOERENG_MANAGER_H
