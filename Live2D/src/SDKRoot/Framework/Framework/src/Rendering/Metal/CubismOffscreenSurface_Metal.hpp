/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <MetalKit/MetalKit.h>
#include "CubismFramework.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

/**
 * @brief  オフスクリーン描画用構造体
 */
class CubismOffscreenFrame_Metal
{
public:

    CubismOffscreenFrame_Metal();

    /**
     * @brief  CubismOffscreenFrame作成
     * @param  displayBufferWidth     作成するバッファ幅
     * @param  displayBufferHeight    作成するバッファ高さ
     * @param  colorBuffer            0以外の場合、ピクセル格納領域としてcolorBufferを使用する
     */
    csmBool CreateOffscreenFrame(csmUint32 displayBufferWidth, csmUint32 displayBufferHeight, id <MTLTexture> colorBuffer = NULL);

    /**
     * @brief   CubismOffscreenFrameの削除
     */
    void DestroyOffscreenFrame();

    /**
     * @brief   カラーバッファメンバーへのアクセッサ
     */
    id <MTLTexture> GetColorBuffer() const;

    /**
     * @brief   カラー設定
     */
    void SetClearColor(float r, float g, float b, float a);

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

    /**
     * @brief   MTLViewport取得
     */
    const MTLViewport* GetViewport() const;

    /**
     * @brief   MTLRenderPassDescriptor取得
     */
    MTLRenderPassDescriptor* GetRenderPassDescriptor() const;

    /**
     * @brief   MTLPixelFormat指定
     */
    void SetMTLPixelFormat(MTLPixelFormat pixelFormat);

private:
    id <MTLTexture>  _colorBuffer; ///レンダーテクスチャ
    csmBool _isInheritedRenderTexture;
    MTLRenderPassDescriptor *_renderPassDescriptor;
    csmUint32   _bufferWidth;           ///< Create時に指定された幅
    csmUint32   _bufferHeight;          ///< Create時に指定された高さ
    MTLViewport _viewPort;
    MTLPixelFormat _pixelFormat;
    float _clearColorR;
    float _clearColorG;
    float _clearColorB;
    float _clearColorA;
};

}}}}

//------------ LIVE2D NAMESPACE ------------
