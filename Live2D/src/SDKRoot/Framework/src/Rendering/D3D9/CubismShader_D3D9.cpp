/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismShader_D3D9.hpp"

#include "CubismRenderer_D3D9.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

static const csmChar* CubismShaderEffectSrc =
    "float4x4 projectMatrix;"\
    "float4x4 clipMatrix;"\
    "float4 baseColor;"\
    "float4 channelFlag;"\
    "texture mainTexture;"\
    "texture maskTexture;"\
    \
    "sampler mainSampler = sampler_state{"\
        "texture = <mainTexture>;"\
    "};"\
    "sampler maskSampler = sampler_state{"\
        "texture = <maskTexture>;"\
    "};"\
    \
    "struct VS_IN {"\
        "float2 pos : POSITION;"\
        "float2 uv : TEXCOORD0;"\
    "};"\
    "struct VS_OUT {"\
        "float4 Position : POSITION0;"\
        "float2 uv : TEXCOORD0;"\
        "float4 clipPosition : TEXCOORD1;"\
    "};"\
    \
"/* Setup mask shader */"\
    "VS_OUT VertSetupMask(VS_IN In) {"\
        "VS_OUT Out = (VS_OUT)0;"\
        "Out.Position = mul(float4(In.pos, 0.0f, 1.0f), projectMatrix);"\
        "Out.clipPosition = mul(float4(In.pos, 0.0f, 1.0f), projectMatrix);"\
        "Out.uv.x = In.uv.x;"\
        "Out.uv.y = 1.0 - +In.uv.y;"\
        "return Out;"\
    "}"\
    "float4 PixelSetupMask(VS_OUT In) : COLOR0{"\
        "float isInside ="\
        "step(baseColor.x, In.clipPosition.x / In.clipPosition.w)"\
        "* step(baseColor.y, In.clipPosition.y / In.clipPosition.w)"\
        "* step(In.clipPosition.x / In.clipPosition.w, baseColor.z)"\
        "* step(In.clipPosition.y / In.clipPosition.w, baseColor.w);"\
        "return channelFlag * tex2D(mainSampler, In.uv).a * isInside;"\
    "}"\
    \
"/* Vertex Shader */"\
    "/* normal */"\
    "VS_OUT VertNormal(VS_IN In) {"\
        "VS_OUT Out = (VS_OUT)0;"\
        "Out.Position = mul(float4(In.pos, 0.0f, 1.0f), projectMatrix);"\
        "Out.uv.x = In.uv.x;"\
        "Out.uv.y = 1.0 - +In.uv.y;"\
        "return Out;"\
    "}"\
    "/* masked */"\
    "VS_OUT VertMasked(VS_IN In) {"\
        "VS_OUT Out = (VS_OUT)0;"\
        "Out.Position = mul(float4(In.pos, 0.0f, 1.0f), projectMatrix);"\
        "Out.clipPosition = mul(float4(In.pos, 0.0f, 1.0f), clipMatrix);"\
        "Out.uv.x = In.uv.x;"\
        "Out.uv.y = 1.0 - In.uv.y;"\
        "return Out;"\
    "}"\
    \
"/* Pixel Shader */"\
    "/* normal */"\
    "float4 PixelNormal(VS_OUT In) : COLOR0{"\
        "float4 color = tex2D(mainSampler, In.uv) * baseColor;"\
        "color.xyz *= color.w;"\
        "return color;"\
    "}"\
    \
    "/* normal premult alpha */"\
    "float4 PixelNormalPremult(VS_OUT In) : COLOR0{"\
        "float4 color = tex2D(mainSampler, In.uv) * baseColor;"\
        "return color;"\
    "}"\
    \
    "/* masked */"\
    "float4 PixelMasked(VS_OUT In) : COLOR0{"\
        "float4 color = tex2D(mainSampler, In.uv) * baseColor;"\
        "color.xyz *= color.w;"\
        "float4 clipMask = (1.0 - tex2D(maskSampler, In.clipPosition.xy / In.clipPosition.w)) * channelFlag;"\
        "float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;"\
        "color = color * maskVal;"\
        "return color;"\
    "}"\
    "/* masked inverted */"
    "float4 PixelMaskedInverted(VS_OUT In) : COLOR0{"\
        "float4 color = tex2D(mainSampler, In.uv) * baseColor;"\
        "color.xyz *= color.w;"\
        "float4 clipMask = (1.0 - tex2D(maskSampler, In.clipPosition.xy / In.clipPosition.w)) * channelFlag;"\
        "float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;"\
        "color = color * (1.0 - maskVal);"\
        "return color;"\
    "}"\
    "/* masked premult alpha */"\
    "float4 PixelMaskedPremult(VS_OUT In) : COLOR0{"\
        "float4 color = tex2D(mainSampler, In.uv) * baseColor;"\
        "float4 clipMask = (1.0 - tex2D(maskSampler, In.clipPosition.xy / In.clipPosition.w)) * channelFlag;"\
        "float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;"\
        "color = color * maskVal;"\
        "return color;"\
    "}"\
    "/* masked inverted premult alpha */"\
    "float4 PixelMaskedInvertedPremult(VS_OUT In) : COLOR0{"\
        "float4 color = tex2D(mainSampler, In.uv) * baseColor;"\
        "float4 clipMask = (1.0 - tex2D(maskSampler, In.clipPosition.xy / In.clipPosition.w)) * channelFlag;"\
        "float maskVal = clipMask.r + clipMask.g + clipMask.b + clipMask.a;"\
        "color = color * (1.0 - maskVal);"\
        "return color;"\
    "}"\
    \
"/* Technique */"\
    "technique ShaderNames_SetupMask {"\
        "pass p0{"\
            "VertexShader = compile vs_2_0 VertSetupMask();"\
            "PixelShader = compile ps_2_0 PixelSetupMask();"\
        "}"\
    "}"\
    \
    "technique ShaderNames_Normal {"\
        "pass p0{"\
            "VertexShader = compile vs_2_0 VertNormal();"\
            "PixelShader = compile ps_2_0 PixelNormal();"\
        "}"\
    "}"\
    \
    "technique ShaderNames_NormalMasked {"\
        "pass p0{"\
            "VertexShader = compile vs_2_0 VertMasked();"\
            "PixelShader = compile ps_2_0 PixelMasked();"\
        "}"\
    "}"\
    \
    "technique ShaderNames_NormalMaskedInverted {"\
        "pass p0{"\
            "VertexShader = compile vs_2_0 VertMasked();"\
            "PixelShader = compile ps_2_0 PixelMaskedInverted();"\
        "}"\
    "}"\
    \
    "technique ShaderNames_NormalPremultipliedAlpha {"\
        "pass p0{"\
            "VertexShader = compile vs_2_0 VertNormal();"\
            "PixelShader = compile ps_2_0 PixelNormalPremult();"\
        "}"\
    "}"\
    \
    "technique ShaderNames_NormalMaskedPremultipliedAlpha {"\
        "pass p0{"\
            "VertexShader = compile vs_2_0 VertMasked();"\
            "PixelShader = compile ps_2_0 PixelMaskedPremult();"\
        "}"\
    "}"\
    \
    "technique ShaderNames_NormalMaskedInvertedPremultipliedAlpha {"\
        "pass p0{"\
            "VertexShader = compile vs_2_0 VertMasked();"\
            "PixelShader = compile ps_2_0 PixelMaskedInvertedPremult();"\
        "}"\
    "}";


void CubismShader_D3D9::ReleaseShaderProgram()
{
    if(_vertexFormat)
    {
        _vertexFormat->Release();
        _vertexFormat = NULL;
    }
    if(_shaderEffect)
    {
        _shaderEffect->Release();
        _shaderEffect = NULL;
    }
}

CubismShader_D3D9::CubismShader_D3D9()
    : _shaderEffect(NULL)
    , _vertexFormat(NULL)
{
}

CubismShader_D3D9::~CubismShader_D3D9()
{
    ReleaseShaderProgram();
}

void CubismShader_D3D9::GenerateShaders(LPDIRECT3DDEVICE9 device)
{
    if(_shaderEffect || _vertexFormat)
    {// 既に有る
        return;
    }

    if(!device)
    {
        return;
    }

    //
    if(!LoadShaderProgram(device))
    {
        CubismLogError("CreateShader failed");
        CSM_ASSERT(0);
    }


    // この描画で使用する頂点フォーマット
    D3DVERTEXELEMENT9 elems[] = {
        { 0, 0, D3DDECLTYPE_FLOAT2, D3DDECLMETHOD_DEFAULT, D3DDECLUSAGE_POSITION, 0 },
        { 0, sizeof(float) * 2, D3DDECLTYPE_FLOAT2, D3DDECLMETHOD_DEFAULT, D3DDECLUSAGE_TEXCOORD, 0 },
        D3DDECL_END()
    };
    if (device->CreateVertexDeclaration(elems, &_vertexFormat))
    {
        CubismLogError("CreateVertexDeclaration failed");
        CSM_ASSERT(0);
    }
}

Csm::csmBool CubismShader_D3D9::LoadShaderProgram(LPDIRECT3DDEVICE9 device)
{
    if (!device) return false;

    ID3DXBuffer* error = NULL;
    if (FAILED(D3DXCreateEffect(device, CubismShaderEffectSrc, static_cast<UINT>(strlen(CubismShaderEffectSrc)), 0, 0, 0, 0, &_shaderEffect, &error)))
    {
        return false;
    }

    return true;
}

ID3DXEffect* CubismShader_D3D9::GetShaderEffect() const
{
    return _shaderEffect;
}

void CubismShader_D3D9::SetupShader(LPDIRECT3DDEVICE9 device)
{
    // まだシェーダ・頂点宣言未作成ならば作成する
    GenerateShaders(device);

    if (!device || !_vertexFormat) return;

    device->SetVertexDeclaration(_vertexFormat);
}

}}}}

//------------ LIVE2D NAMESPACE ------------

