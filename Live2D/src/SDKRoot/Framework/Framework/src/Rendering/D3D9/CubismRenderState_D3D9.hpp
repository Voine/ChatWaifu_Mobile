/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismNativeInclude_D3D9.hpp"

#include "Type/csmVector.hpp"
#include "Type/csmMap.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

class CubismRenderer_D3D9;

/**
 * @brief   CubismD3D9内部で設定するステートのうち、途中で変更する可能性のあるものを管理。
 *          CubismRenderer_D3D9がシングルトンとして管理。
 */
class CubismRenderState_D3D9
{
    friend class CubismRenderer_D3D9;
public:
    enum
    {
        State_None,
        State_Blend,        ///< ブレンドモード
        State_Viewport,     ///< ビューポート
        State_ColorMask,    ///< カラーマスク
        State_RenderTarget, ///< レンダーターゲット
        State_DepthTarget,  ///< 深度ターゲット
        State_ZEnable,      ///< Z有効無効
        State_CullMode,     ///< カリングモード
        State_TextureFilterStage0, ///< テクスチャフィルター（ステージ0）
        State_TextureFilterStage1, ///< テクスチャフィルター（ステージ1）
        State_Max,
    };

    /**
     * @notice  デフォルトの=でコピーします
     */
    struct Stored
    {
        Stored()
        {
            BlendEnable = false;
            BlendAlphaSeparateEnable = false;
            BlendSourceMul = D3DBLEND_ZERO;
            BlendBlendFunc = D3DBLENDOP_ADD;
            BlendDestMul = D3DBLEND_ZERO;
            BlendSourceAlpha = D3DBLEND_ZERO;
            BlendAlphaFunc = D3DBLENDOP_ADD;
            BlendDestAlpha = D3DBLEND_ZERO;

            ViewportX = 0;
            ViewportY = 0;
            ViewportWidth = 0;
            ViewportHeight = 0;
            ViewportMinZ = 0.0f;
            ViewportMaxZ = 0.0f;

            ColorMaskMask = 0;

            ZEnable = D3DZB_FALSE;
            ZFunc = D3DCMP_NEVER;

            CullModeFaceMode = D3DCULL_NONE;

            MinFilter[0] = D3DTEXF_NONE;
            MagFilter[0] = D3DTEXF_NONE;
            MipFilter[0] = D3DTEXF_NONE;
            AddressU[0] = D3DTADDRESS_WRAP;
            AddressV[0] = D3DTADDRESS_WRAP;
            Anisotropy[0] = 0.0f;

            MinFilter[1] = D3DTEXF_NONE;
            MagFilter[1] = D3DTEXF_NONE;
            MipFilter[1] = D3DTEXF_NONE;
            AddressU[1] = D3DTADDRESS_WRAP;
            AddressV[1] = D3DTADDRESS_WRAP;
            Anisotropy[1] = 0.0f;

            memset(_valid, 0, sizeof(_valid));
        }

        // State_Blend
        bool BlendEnable;
        bool BlendAlphaSeparateEnable;
        D3DBLEND BlendSourceMul;
        D3DBLENDOP BlendBlendFunc;
        D3DBLEND BlendDestMul;
        D3DBLEND BlendSourceAlpha;
        D3DBLENDOP BlendAlphaFunc;
        D3DBLEND BlendDestAlpha;

        // State_Viewport
        DWORD ViewportX;
        DWORD ViewportY;
        DWORD ViewportWidth;
        DWORD ViewportHeight;
        float ViewportMinZ;
        float ViewportMaxZ;

        // State_ColorMask
        DWORD ColorMaskMask;

        // State_ZEnable
        D3DZBUFFERTYPE ZEnable;
        D3DCMPFUNC ZFunc;

        // State_CullMode
        D3DCULL CullModeFaceMode; // 消す面を指定 CWだと時計回りが消える

        // State_TextureFilter サンプラーフィルター(0番, 1番)
        D3DTEXTUREFILTERTYPE    MinFilter[2];
        D3DTEXTUREFILTERTYPE    MagFilter[2];
        D3DTEXTUREFILTERTYPE    MipFilter[2];
        D3DTEXTUREADDRESS       AddressU[2];
        D3DTEXTUREADDRESS       AddressV[2];
        float                   Anisotropy[2];

        csmBool _valid[State_Max];    ///< 設定したかどうか。現在はStartFrameで一通りは呼んでいる
    };

    /**
     * @brief   フレーム先頭で呼び出す処理
     */
    void StartFrame();

    /**
     * @brief  管理中のステートをPush
     */
    void Save();


    /**
     * @brief   Push下ステートをPop
     * @notice  Saveの際にCubismRenderState_D3D9で設定していなかった項目は戻せないことに注意
     */
    void Restore(LPDIRECT3DDEVICE9 device);


    /**
     * @brief   ブレンドモードセット
     *
     * @param   device[in]     描画デバイス
     * @param   enable[in]              αブレンド有効無効
     * @param   alphaSeparateEnable[in] αブレンドのうち、rgbとaの計算式を分離するかどうか
     * @param   srcmul[in]      Srcカラー
     * @param   blendFunc[in]   ブレンド関数
     * @param   destmul[in]     Destカラー
     * @param   srcalpha[in]    Srcアルファ
     * @param   alphaFunc[in]   α値ブレンド関数
     * @param   destalpha[in]   Destアルファ
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetBlend(LPDIRECT3DDEVICE9 device, bool enable, bool alphaSeparateEnable,
        D3DBLEND srcmul, D3DBLENDOP blendFunc, D3DBLEND destmul,
        D3DBLEND srcalpha, D3DBLENDOP alphaFunc, D3DBLEND destalpha,
        csmBool force=false);

    /**
     * @brief   ビューポートセット
     *
     * @param   device[in]     描画デバイス
     * @param   left[in]        ビューポート左座標
     * @param   top[in]         ビューポート上座標
     * @param   width[in]       ビューポート右座標
     * @param   height[in]      ビューポート下座標
     * @param   zMin[in]        Z最小値
     * @param   zMax[in]        Z最大値
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetViewport(LPDIRECT3DDEVICE9 device, DWORD left, DWORD top, DWORD width, DWORD height, float zMin, float zMax, csmBool force = false);

    /**
     * @brief   カラーマスクセット
     */
    void SetColorMask(LPDIRECT3DDEVICE9 device, DWORD mask, csmBool force = false);

    /**
     * @brief   Z有効無効セット
     *
     * @param   device[in]     描画デバイス
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetZEnable(LPDIRECT3DDEVICE9 device, D3DZBUFFERTYPE enable, D3DCMPFUNC zfunc, csmBool force = false);

    /**
     * @brief   カリングモードセット
     *
     * @param   device[in]     描画デバイス
     * @param   cullFace[in]    切除するべき面 指定された面を切る Cwだと時計回りを切除
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetCullMode(LPDIRECT3DDEVICE9 device, D3DCULL cullFace, csmBool force = false);

    /**
     * @brief   テクスチャサンプラーにフィルタセット
     *
     * @param   device[in]     描画デバイス
     * @param   stage[in]       サンプラーステージインデックス（サンプラー番号）
     * @param   minFilter[in]   縮小時フィルタ
     * @param   magFilter[in]   拡大時フィルタ
     * @param   mipFilter[in]   ミップフィルタ
     * @param   addressU[in]    アドレッシングモードU
     * @param   addressV[in]    アドレッシングモードV
     */
    void SetTextureFilter(LPDIRECT3DDEVICE9 device, csmInt32 stage, D3DTEXTUREFILTERTYPE minFilter, D3DTEXTUREFILTERTYPE magFilter, D3DTEXTUREFILTERTYPE mipFilter, D3DTEXTUREADDRESS addressU, D3DTEXTUREADDRESS addressV, csmFloat32 anisotropy = 0.0, csmBool force = false);

private:
    CubismRenderState_D3D9();
    ~CubismRenderState_D3D9();

    /**
     * @brief  D3DDeviceから、Cubismに関係する値を取得したうえでCubismRenderState_D3D9に反映しSaveを呼び出し、_pushedは破棄
     *          Cubismフレーム処理の最初、StartFrameの後で呼んでいる
     */
    void SaveCurrentNativeState(LPDIRECT3DDEVICE9 device);

    /**
     * @brief  最初にPushしたステートまで戻す
     */
    void RestoreNativeState(LPDIRECT3DDEVICE9 device);

    Stored  _stored;           ///< ストアされた各種設定

    csmVector<Stored> _pushed;
};

}}}}
//------------ LIVE2D NAMESPACE ------------
