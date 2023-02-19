/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismNativeInclude_D3D11.hpp"

#include "Type/csmVector.hpp"
#include "Type/csmMap.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

class CubismRenderer_D3D11;

/**
 * @brief  CubismDX11内部で設定するステートのうち、途中で変更する可能性のあるものを管理。
 *          CubismRenderer_D3D11がシングルトンとして管理。
 */
class CubismRenderState_D3D11
{
    friend class CubismRenderer_D3D11;
public:

    enum
    {
        State_None,
        State_Blend,        ///< ブレンドモード
        State_Viewport,     ///< ビューポート
        State_ZEnable,      ///< Z有効無効
        State_CullMode,     ///< カリングモード
        State_Sampler,      ///< テクスチャフィルター
        State_Max,
    };

    // ブレンドステート D3D11ではオブジェクト単位での管理
    enum Blend
    {
        Blend_Origin,
        Blend_Zero,
        Blend_Normal,
        Blend_Add,
        Blend_Mult,
        Blend_Mask,
        Blend_Max,
    };

    // カリング D3D11ではオブジェクト単位での管理
    enum Cull
    {
        Cull_Origin,///< 元々の設定
        Cull_None,  ///< カリング無し
        Cull_Ccw,   ///< CCW表示
        Cull_Max,
    };

    // Z D3D11ではオブジェクト単位での管理
    enum Depth
    {
        Depth_Origin,   ///< 元々の設定
        Depth_Disable,  ///< Zoff
        Depth_Enable,   ///< Zon
        Depth_Max,
    };

    // サンプラーステート D3D11ではオブジェクト単位での管理
    enum Sampler
    {
        Sampler_Origin,     ///< 元々の設定
        Sampler_Normal,     ///< 使用ステート
        Sampler_Anisotropy, ///< 異方性フィルタリング使用
        Sampler_Max,
    };

    /**
     * @notice  デフォルトの=でコピーします
     */
    struct Stored
    {
        Stored()
        {
            _blendState = Blend_Zero;
            _blendFactor.x = _blendFactor.y = _blendFactor.z = _blendFactor.w = 0.0f;
            _blendMask = 0xffffffff;

            _cullMode = Cull_None;

            _depthEnable = Depth_Disable;
            _depthRef = 0;

            _viewportX = 0;
            _viewportY = 0;
            _viewportWidth = 0;
            _viewportHeight = 0;
            _viewportMinZ = 0.0f;
            _viewportMaxZ = 0.0f;

            _sampler = Sampler_Normal;
            _anisotropy = 0.0;

            memset(_valid, 0, sizeof(_valid));
        }

        // State_Blend
        Blend _blendState;
        DirectX::XMFLOAT4 _blendFactor;
        UINT _blendMask;

        // State_CullMode
        Cull _cullMode;

        // State_Viewport
        FLOAT _viewportX;
        FLOAT _viewportY;
        FLOAT _viewportWidth;
        FLOAT _viewportHeight;
        FLOAT _viewportMinZ;
        FLOAT _viewportMaxZ;

        // State_ZEnable
        Depth _depthEnable;
        UINT _depthRef;

        // State_Sampler
        Sampler _sampler;
        FLOAT _anisotropy;

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
     * @notice  Saveの際にCubismRenderState_D3D11で設定していなかった項目は戻せないことに注意
     */
    void Restore(ID3D11DeviceContext* renderContext);

    /**
     * @brief   ブレンドモードセット
     *
     * @param   renderContext[in]    描画コンテキスト
     * @param   blendState[in]  予め用意したブレンドオブジェクトの番号(enum CubismRenderState_D3D11::Blend)
     * @param   blendFactor[in] D3D11_BLEND_BLEND_FACTOR指定時の係数
     * @param   mask[in]        ピクセルマスク
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetBlend(ID3D11DeviceContext* renderContext, Blend blendState, DirectX::XMFLOAT4 blendFactor, UINT mask,
        csmBool force=false);

    /**
     * @brief   カリングモードセット
     *
     * @param   device[in]     描画コンテキスト
     * @param   cullFace[in]    切除するべき面 指定された面を切る Cwだと時計回りを切除
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetCullMode(ID3D11DeviceContext* renderContext, Cull cullFace, csmBool force = false);

    /**
     * @brief   ビューポートセット
     *
     * @param   renderContext[in]    描画コンテキスト
     * @param   left[in]        ビューポート左座標
     * @param   top[in]         ビューポート上座標
     * @param   width[in]       ビューポート右座標
     * @param   height[in]      ビューポート下座標
     * @param   zMin[in]        Z最小値
     * @param   zMax[in]        Z最大値
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetViewport(ID3D11DeviceContext* renderContext, FLOAT left, FLOAT top, FLOAT width, FLOAT height, FLOAT zMin, FLOAT zMax, csmBool force = false);

    /**
     * @brief   Z有効無効セット
     *
     * @param   renderContext[in]    描画コンテキスト
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetZEnable(ID3D11DeviceContext* renderContext, Depth enable, UINT stelcilRef, csmBool force = false);

    /**
     * @brief  サンプラーステートセット
     *
     * @param   renderContext[in]    描画コンテキスト
     * @param   force[in]       trueの場合は今の設定が何であろうと強制設定
     */
    void SetSampler(ID3D11DeviceContext* renderContext, Sampler sample, csmFloat32 anisotropy = 0.0, csmBool force = false);

private:
    CubismRenderState_D3D11();
    ~CubismRenderState_D3D11();

    /**
     * @brief   各種オブジェクトを作成する
     */
    void Create(ID3D11Device* device);

    /**
     * @brief  D3DDeviceから、Cubismに関係する値を取得したうえでCubismRenderState_D3D11に反映しSaveを呼び出し、_pushedは破棄
     *          Cubismフレーム処理の最初、StartFrameの後で呼んでいる
     */
    void SaveCurrentNativeState(ID3D11Device* device, ID3D11DeviceContext* renderContext);

    /**
     * @brief  最初にPushしたステートまで戻す
     */
    void RestoreNativeState(ID3D11Device* device, ID3D11DeviceContext* renderContext);

    Stored  _stored;           ///< ストアされた各種設定

    csmVector<Stored> _pushed;


    csmVector<ID3D11BlendState*>        _blendStateObjects;
    csmVector<ID3D11RasterizerState*>   _rasterizeStateObjects;
    csmVector<ID3D11DepthStencilState*> _depthStencilState;
    csmVector<ID3D11SamplerState*>      _samplerState;
};

}}}}
//------------ LIVE2D NAMESPACE ------------
