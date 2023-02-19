/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework {

/**
 * @brief   D3D11基本頂点
 */

struct CubismVertexD3D11
{
    float x, y;     // Position
    float u, v;     // UVs
};

/**
 * @brief   シェーダーコンスタントバッファ
 */
struct CubismConstantBufferD3D11
{
    DirectX::XMFLOAT4X4 projectMatrix;
    DirectX::XMFLOAT4X4 clipMatrix;
    DirectX::XMFLOAT4 baseColor;
    DirectX::XMFLOAT4 channelFlag;
};

}}}
