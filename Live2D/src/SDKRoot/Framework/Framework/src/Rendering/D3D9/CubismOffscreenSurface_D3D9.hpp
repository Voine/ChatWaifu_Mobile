/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismNativeInclude_D3D9.hpp"

#include "Math/CubismMatrix44.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {


/**
 * @brief  オフスクリーン描画用構造体
 */
class CubismOffscreenFrame_D3D9
{
public:

    CubismOffscreenFrame_D3D9();

    /**
     * @brief   指定の描画ターゲットに向けて描画開始
     *
     * @param   device[in]     D3dデバイス
     */
    void BeginDraw(LPDIRECT3DDEVICE9 device);

    /**
     * @brief   描画終了
     *
     * @param   device[in]     D3dデバイス
     */
    void EndDraw(LPDIRECT3DDEVICE9 device);

    /**
     * @brief   レンダリングターゲットのクリア
     *           呼ぶ場合はBeginDrawの後で呼ぶこと
     * @param   r   赤(0.0~1.0)
     * @param   g   緑(0.0~1.0)
     * @param   b   青(0.0~1.0)
     * @param   a   α(0.0~1.0)
     */
    void Clear(LPDIRECT3DDEVICE9 device, float r, float g, float b, float a);

    /**
     *  @brief  CubismOffscreenFrame作成
     *  @param  device[in]                D3dデバイス
     *  @param  displayBufferWidth[in]     作成するバッファ幅
     *  @param  displayBufferHeight[in]    作成するバッファ高さ
     */
    csmBool CreateOffscreenFrame(LPDIRECT3DDEVICE9 device, csmUint32 displayBufferWidth, csmUint32 displayBufferHeight);

    /**
     * @brief   CubismOffscreenFrameの削除
     */
    void DestroyOffscreenFrame();

    /**
     * @brief   テクスチャメンバーへのアクセッサ
     */
    LPDIRECT3DTEXTURE9 GetTexture() const;

    /**
     * @brief   バッファ幅取得
     */
    csmUint32 GetBufferWidth() const;

    /**
     * @brief   バッファ高さ取得
     */
    csmUint32 GetBufferHeight() const;

    /**
     * @brief   現在有効かどうか
     */
    csmBool IsValid() const;

private:
    LPDIRECT3DTEXTURE9  _texture;           ///< 生成テクスチャ
    LPDIRECT3DSURFACE9  _textureSurface;    ///< レンダリングサーフェス
    LPDIRECT3DSURFACE9  _depthSurface;      ///< Z

    LPDIRECT3DSURFACE9  _backupRender;      ///< 元々のターゲットを退避
    LPDIRECT3DSURFACE9  _backupDepth;       ///< 元々のZを退避

    csmUint32           _bufferWidth;       ///< Create時に指定されたサイズ
    csmUint32           _bufferHeight;      ///< Create時に指定されたサイズ
};


}}}}

//------------ LIVE2D NAMESPACE ------------
