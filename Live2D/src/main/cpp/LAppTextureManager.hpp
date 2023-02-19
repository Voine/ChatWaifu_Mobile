/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <string>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <Type/csmVector.hpp>

/**
* @brief テクスチャ管理クラス
*
* 画像読み込み、管理を行うクラス。
*/
class LAppTextureManager
{
public:

    /**
    * @brief 画像情報構造体
    */
    struct TextureInfo
    {
        GLuint id;              ///< テクスチャID
        int width;              ///< 横幅
        int height;             ///< 高さ
        std::string fileName;   ///< ファイル名
    };

    /**
    * @brief コンストラクタ
    */
    LAppTextureManager();

    /**
    * @brief デストラクタ
    *
    */
    ~LAppTextureManager();


    /**
    * @brief プリマルチプライ処理
    *
    * @param[in] red  画像のRed値
    * @param[in] green  画像のGreen値
    * @param[in] blue  画像のBlue値
    * @param[in] alpha  画像のAlpha値
    *
    * @return プリマルチプライ処理後のカラー値
    */
    inline unsigned int Premultiply(unsigned char red, unsigned char green, unsigned char blue, unsigned char alpha)
    {
        return static_cast<unsigned>(\
            (red * (alpha + 1) >> 8) | \
            ((green * (alpha + 1) >> 8) << 8) | \
            ((blue * (alpha + 1) >> 8) << 16) | \
            (((alpha)) << 24)   \
            );
    }

    /**
    * @brief 画像読み込み
    *
    * @param[in] fileName  読み込む画像ファイルパス名
    * @return 画像情報。読み込み失敗時はNULLを返す
    */
    TextureInfo* CreateTextureFromPngFile(std::string fileName);

    /**
    * @brief 画像の解放
    *
    * 配列に存在する画像全てを解放する
    */
    void ReleaseTextures();

    /**
     * @brief 画像の解放
     *
     * 指定したテクスチャIDの画像を解放する
     * @param[in] textureId  解放するテクスチャID
     **/
    void ReleaseTexture(Csm::csmUint32 textureId);

    /**
    * @brief 画像の解放
    *
    * 指定した名前の画像を解放する
    * @param[in] fileName  解放する画像ファイルパス名
    **/
    void ReleaseTexture(std::string fileName);

private:
    Csm::csmVector<TextureInfo*> _textures;
};
