/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#ifndef MetalShaderTypes_h
#define MetalShaderTypes_h

#include <simd/simd.h>

typedef enum MetalVertexInputIndex
{
    MetalVertexInputIndexVertices = 0,
    MetalVertexInputUVs = 1,
    MetalVertexInputIndexUniforms = 2,
} MetalVertexInputIndex;

typedef struct
{
    simd::float4x4 matrix;
    vector_float4 baseColor;
    vector_float4 multiplyColor;
    vector_float4 screenColor;

} CubismNormalShaderUniforms;

typedef struct
{
    simd::float4x4 matrix;
    simd::float4x4 clipMatrix;
    vector_float4 baseColor;

} CubismMaskedShaderUniforms;

typedef struct
{
    simd::float4x4 clipMatrix;
    vector_float4 channelFlag;
    vector_float4 baseColor;

} CubismSetupMaskedShaderUniforms;

typedef struct
{
    vector_float4 channelFlag;
    vector_float4 baseColor;
    vector_float4 multiplyColor;
    vector_float4 screenColor;

} CubismFragMaskedShaderUniforms;

#endif /* MetalShaderTypes_h */
