/*
See LICENSE folder for this sample’s licensing information.

Abstract:
Metal shaders used for this sample
*/

#include <metal_stdlib>
#include <simd/simd.h>

using namespace metal;

// Include header shared between this Metal shader code and C code executing Metal API commands
#include "MetalShaderTypes.h"

//
struct NormalRasterizerData
{
    float4 position [[ position ]];
    float2 texCoord; //v2f.texcoord
};

//
struct MaskedRasterizerData
{
    float4 position [[ position ]];
    float2 texCoord; //v2f.texcoord
    float4 myPos;
};

vertex MaskedRasterizerData
VertShaderSrcSetupMask(uint vertexID [[ vertex_id ]],
             constant float2 *vertexArray [[ buffer(MetalVertexInputIndexVertices) ]],
             constant float2 *uvArray [[ buffer(MetalVertexInputUVs) ]],
             constant CubismSetupMaskedShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]])
{
    MaskedRasterizerData out;
    float2 vert = vertexArray[vertexID];
    float2 uv = uvArray[vertexID];

    float4 pos = float4(vert.x, vert.y, 0.0, 1.0);
    out.position = uniforms.clipMatrix * pos;
    out.myPos = uniforms.clipMatrix * pos;
    out.texCoord = uv;
    out.texCoord.y = 1.0 - out.texCoord.y;

    return out;
}

fragment float4
FragShaderSrcSetupMask(MaskedRasterizerData in [[stage_in]],
                       texture2d<float> texture [[ texture(0) ]],
                       constant CubismSetupMaskedShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]],
                       sampler smp [[sampler(0)]])
{
    float isInside =
        step(uniforms.baseColor.x, in.myPos.x/in.myPos.w)
        * step(uniforms.baseColor.y, in.myPos.y/in.myPos.w)
        * step(in.myPos.x/in.myPos.w, uniforms.baseColor.z)
        * step(in.myPos.y/in.myPos.w, uniforms.baseColor.w);

    float4 gl_FragColor = float4(uniforms.channelFlag * texture.sample(smp, in.texCoord).a * isInside);
    return gl_FragColor;
}

////----- バーテックスシェーダプログラム -----
//// Normal & Add & Mult 共通
vertex NormalRasterizerData
VertShaderSrc(uint vertexID [[ vertex_id ]],
              constant float2 *vertexArray [[ buffer(MetalVertexInputIndexVertices) ]],
              constant float2 *uvArray [[ buffer(MetalVertexInputUVs) ]],
             constant CubismNormalShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]])

{
    NormalRasterizerData out;
    float2 vert = vertexArray[vertexID];
    float2 uv = uvArray[vertexID];

    float4 pos = float4(vert.x, vert.y, 0.0, 1.0);
    out.position = uniforms.matrix * pos;
    out.texCoord = uv;
    out.texCoord.y = 1.0 - out.texCoord.y;

    return out;
}

//// Normal & Add & Mult 共通（クリッピングされたものの描画用）
vertex MaskedRasterizerData
VertShaderSrcMasked(uint vertexID [[ vertex_id ]],
            constant float2 *vertexArray [[ buffer(MetalVertexInputIndexVertices) ]],
            constant float2 *uvArray [[ buffer(MetalVertexInputUVs) ]],
            constant CubismMaskedShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]])
{
    MaskedRasterizerData out;
    float2 vert = vertexArray[vertexID];
    float2 uv = uvArray[vertexID];

    float4 pos = float4(vert.x, vert.y, 0.0, 1.0);
    out.position = uniforms.matrix * pos;
    out.myPos = uniforms.clipMatrix * pos;
    out.myPos = float4(out.myPos.x, 1.0 - out.myPos.y, out.myPos.zw);
    out.texCoord = uv;
    out.texCoord.y = 1.0 - out.texCoord.y;

    return out;
}

////----- フラグメントシェーダプログラム -----
//// Normal & Add & Mult 共通
fragment float4
FragShaderSrc(NormalRasterizerData in [[stage_in]],
              texture2d<float> texture [[ texture(0) ]],
              constant CubismNormalShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]],
              sampler smp [[sampler(0)]])
{
    float4 texColor = texture.sample(smp, in.texCoord);
    texColor.rgb = texColor.rgb * uniforms.multiplyColor.rgb;
    texColor.rgb = texColor.rgb + uniforms.screenColor.rgb - (texColor.rgb * uniforms.screenColor.rgb);
    float4 color = texColor * uniforms.baseColor;
    float4 gl_FragColor = float4(color.rgb * color.a,  color.a);

    return gl_FragColor;
}

//// Normal & Add & Mult 共通 （PremultipliedAlpha）
fragment float4
FragShaderSrcPremultipliedAlpha(MaskedRasterizerData in [[stage_in]],
                        texture2d<float> texture [[ texture(0) ]],
                        constant CubismNormalShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]],
                        sampler smp [[sampler(0)]])
{
    float4 texColor = texture.sample(smp, in.texCoord);
    texColor.rgb = texColor.rgb * uniforms.multiplyColor.rgb;
    texColor.rgb = (texColor.rgb + uniforms.screenColor.rgb * texColor.a) - (texColor.rgb * uniforms.screenColor.rgb);
    float4 gl_FragColor = texColor * uniforms.baseColor;

    return gl_FragColor;
}

//// Normal & Add & Mult 共通（クリッピングされたものの描画用）
fragment float4
FragShaderSrcMask(MaskedRasterizerData in [[stage_in]],
                    texture2d<float> texture0 [[ texture(0) ]],
                    texture2d<float> texture1 [[ texture(1) ]],
                    constant CubismFragMaskedShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]],
                    sampler smp [[sampler(0)]])
{
    float4 texColor = texture0.sample(smp, in.texCoord);
    texColor.rgb = texColor.rgb * uniforms.multiplyColor.rgb;
    texColor.rgb = texColor.rgb + uniforms.screenColor.rgb - (texColor.rgb * uniforms.screenColor.rgb);
    float4 col_formask = texColor * uniforms.baseColor;
    col_formask.rgb = col_formask.rgb  * col_formask.a ;
    float4 clipMask = (1.0 - texture1.sample(smp, in.myPos.xy / in.myPos.w)) * uniforms.channelFlag;
    float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;
    col_formask = col_formask * maskVal;
    float4 gl_FragColor = col_formask;
    return gl_FragColor;
}

//// Normal & Add & Mult 共通（クリッピングされて反転使用の描画用）
fragment float4
FragShaderSrcMaskInverted(MaskedRasterizerData in [[stage_in]],
                    texture2d<float> texture0 [[ texture(0) ]],
                    texture2d<float> texture1 [[ texture(1) ]],
                    constant CubismFragMaskedShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]],
                    sampler smp [[sampler(0)]])
{
    float4 texColor = texture0.sample(smp, in.texCoord);
    texColor.rgb = texColor.rgb * uniforms.multiplyColor.rgb;
    texColor.rgb = texColor.rgb + uniforms.screenColor.rgb - (texColor.rgb * uniforms.screenColor.rgb);
    float4 col_formask = texColor * uniforms.baseColor;
    col_formask.rgb = col_formask.rgb  * col_formask.a ;
    float4 clipMask = (1.0 - texture1.sample(smp, in.myPos.xy / in.myPos.w)) * uniforms.channelFlag;
    float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;
    col_formask = col_formask * (1.0 - maskVal);
    float4 gl_FragColor = col_formask;
    return gl_FragColor;
}

//// Normal & Add & Mult 共通（クリッピングされたものの描画用、PremultipliedAlphaの場合）
fragment float4
FragShaderSrcMaskPremultipliedAlpha(MaskedRasterizerData in [[stage_in]],
                    texture2d<float> texture0 [[ texture(0) ]],
                    texture2d<float> texture1 [[ texture(1) ]],
                                    constant CubismFragMaskedShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]],
                                    sampler smp [[sampler(0)]])
{
    float4 texColor = texture0.sample(smp, in.texCoord);
    texColor.rgb = texColor.rgb * uniforms.multiplyColor.rgb;
    texColor.rgb = (texColor.rgb + uniforms.screenColor.rgb * texColor.a) - (texColor.rgb * uniforms.screenColor.rgb);
    float4 col_formask = texColor * uniforms.baseColor;
    float4 clipMask = (1.0 - texture1.sample(smp, in.myPos.xy / in.myPos.w)) * uniforms.channelFlag;
    float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;
    col_formask = col_formask * maskVal;
    float4 gl_FragColor = col_formask;
    return gl_FragColor;
}

//// Normal & Add & Mult 共通（クリッピングされて反転使用の描画用、PremultipliedAlphaの場合）
fragment float4
FragShaderSrcMaskInvertedPremultipliedAlpha(MaskedRasterizerData in [[stage_in]],
                    texture2d<float> texture0 [[ texture(0) ]],
                    texture2d<float> texture1 [[ texture(1) ]],
                    constant CubismFragMaskedShaderUniforms &uniforms  [[ buffer(MetalVertexInputIndexUniforms) ]],
                    sampler smp [[sampler(0)]])
{
    float4 texColor = texture0.sample(smp, in.texCoord);
    texColor.rgb = texColor.rgb * uniforms.multiplyColor.rgb;
    texColor.rgb = (texColor.rgb + uniforms.screenColor.rgb * texColor.a) - (texColor.rgb * uniforms.screenColor.rgb);
    float4 col_formask = texColor * uniforms.baseColor;
    float4 clipMask = (1.0 - texture1.sample(smp, in.myPos.xy / in.myPos.w)) * uniforms.channelFlag;
    float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;
    col_formask = col_formask * (1.0 - maskVal);
    float4 gl_FragColor = col_formask;
    return gl_FragColor;
}
