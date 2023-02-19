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
 * @brief   D3D9基本頂点
 */

struct CubismVertexD3D9
{
    float x, y;     // Position
    float u, v;     // UVs
};
enum CubismD3D9VertexElem
{
    CubismD3D9VertexElem_Position = 0,
    CubismD3D9VertexElem_UV,
    CubismD3D9VertexElem_Max
};

}}}
