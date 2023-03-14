#include "manager.h"

unsigned char* asset_loader(const char * fileName, AssetJNI* asjni, int* fd, size_t *length){
    AAssetManager* mgr = AAssetManager_fromJava(asjni->env, asjni->assetManager);
    AAsset* asset = AAssetManager_open(mgr, fileName, AASSET_MODE_BUFFER);
    unsigned char* buff = nullptr;
    if (asset) {
        *length = AAsset_getLength(asset);
        buff = new unsigned char[*length + 1];
        buff[*length] = 0;
        AAsset_read(asset, buff, *length);
        AAsset_close(asset);
        *fd = 0;
        return buff;
    }
    *fd = -1;
    return buff;
}