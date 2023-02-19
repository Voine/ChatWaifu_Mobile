/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismNativeInclude_D3D11.hpp"

#include "Math/CubismMatrix44.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {


/**
 * @brief  オフスクリーン描画用構造体
 */
class CubismOffscreenFrame_D3D11
{
public:

    CubismOffscreenFrame_D3D11();

    /**
     * @brief   指定の描画ターゲットに向けて描画開始
     *
     * @param   renderContext[in]     描画コンテキスト
     */
    void BeginDraw(ID3D11DeviceContext* renderContext);

    /**
     * @brief   描画終了
     *
     * @param   renderContext[in]     描画コンテキスト
     */
    void EndDraw(ID3D11DeviceContext* renderContext);

    /**
     * @brief   レンダリングターゲットのクリア
     *           呼ぶ場合はBeginDrawの後で呼ぶこと
     * @param   r   赤(0.0~1.0)
     * @param   g   緑(0.0~1.0)
     * @param   b   青(0.0~1.0)
     * @param   a   α(0.0~1.0)
     */
    void Clear(ID3D11DeviceContext* renderContext, float r, float g, float b, float a);

    /**
     *  @brief  CubismOffscreenFrame作成
     *  @param  device[in]                 D3dデバイス
     *  @param  displayBufferWidth[in]     作成するバッファ幅
     *  @param  displayBufferHeight[in]    作成するバッファ高さ
     */
    csmBool CreateOffscreenFrame(ID3D11Device* device, csmUint32 displayBufferWidth, csmUint32 displayBufferHeight);

    /**
     * @brief   CubismOffscreenFrameの削除
     */
    void DestroyOffscreenFrame();

    /**
     * @brief   クリアカラーの上書き
     */
    void SetClearColor(float r, float g, float b, float a);

    /**
     * @brief   テクスチャビューへのアクセッサ
     */
    ID3D11ShaderResourceView* GetTextureView() const;

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
    ID3D11Texture2D*            _texture;          ///< 生成テクスチャ
    ID3D11ShaderResourceView*   _textureView;      ///< テクスチャのシェーダーリソースとしてのビュー
    ID3D11RenderTargetView*     _renderTargetView; ///< テクスチャのレンダーターゲットとしてのビュー
    ID3D11Texture2D*            _depthTexture;     ///< Zテクスチャ
    ID3D11DepthStencilView*     _depthView;        ///< Zビュー

    ID3D11RenderTargetView*     _backupRender;     ///< 元々のターゲットを退避
    ID3D11DepthStencilView*     _backupDepth;      ///< 元々のZを退避

    csmUint32                   _bufferWidth;      ///< Create時に指定されたサイズ
    csmUint32                   _bufferHeight;     ///< Create時に指定されたサイズ

};


}}}}

//------------ LIVE2D NAMESPACE ------------
