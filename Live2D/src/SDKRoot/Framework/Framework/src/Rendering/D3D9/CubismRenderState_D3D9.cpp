/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismRenderState_D3D9.hpp"
#include "CubismShader_D3D9.hpp"
#include "CubismRenderer_D3D9.hpp"
#include "Type/csmVector.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

CubismRenderState_D3D9::CubismRenderState_D3D9()
{
    //
    memset(_stored._valid, 0, sizeof(_stored._valid));
}

CubismRenderState_D3D9::~CubismRenderState_D3D9()
{
    _pushed.Clear();
}

void CubismRenderState_D3D9::StartFrame()
{
    memset(_stored._valid, 0, sizeof(_stored._valid));

    _pushed.Clear();
}


void CubismRenderState_D3D9::Save()
{
    // 現時点のステートをPush
    _pushed.PushBack(_stored);
}

void CubismRenderState_D3D9::Restore(LPDIRECT3DDEVICE9 device)
{
    const csmUint32 size = _pushed.GetSize();

    if (size == 0)
    {
        return;
    }

    //forで辿って最後に設定した個所までチェック
    csmBool isSet[State_Max];
    memset(isSet, 0, sizeof(isSet));

    for (csmInt32 i = static_cast<csmInt32>(_pushed.GetSize())-1; i>=0; i--)
    {
        Stored &current = _pushed[i];

        if (_pushed[i]._valid[State_Blend] && !isSet[State_Blend])
        {
            SetBlend(device, current.BlendEnable, current.BlendAlphaSeparateEnable, current.BlendSourceMul, current.BlendBlendFunc, current.BlendDestMul,
                current.BlendSourceAlpha, current.BlendAlphaFunc, current.BlendDestAlpha, true);
            isSet[State_Blend] = true;
        }
        if (_pushed[i]._valid[State_Viewport] && !isSet[State_Viewport])
        {
            SetViewport(device, current.ViewportX, current.ViewportY, current.ViewportWidth, current.ViewportHeight, current.ViewportMinZ, current.ViewportMaxZ, true);
            isSet[State_Viewport] = true;
        }
        if (_pushed[i]._valid[State_ColorMask] && !isSet[State_ColorMask])
        {
            SetColorMask(device, current.ColorMaskMask, true);
            isSet[State_ColorMask] = true;
        }
        if (_pushed[i]._valid[State_ZEnable] && !isSet[State_ZEnable])
        {
            SetZEnable(device, current.ZEnable, current.ZFunc, true);
            isSet[State_ZEnable] = true;
        }
        if (_pushed[i]._valid[State_CullMode] && !isSet[State_CullMode])
        {
            SetCullMode(device, current.CullModeFaceMode, true);
            isSet[State_CullMode] = true;
        }
        if (_pushed[i]._valid[State_TextureFilterStage0] && !isSet[State_TextureFilterStage0])
        {
            SetTextureFilter(device, 0, current.MinFilter[0], current.MagFilter[0], current.MipFilter[0], current.AddressU[0], current.AddressV[0], current.Anisotropy[0], true);
            isSet[State_TextureFilterStage0] = true;
        }
        if (_pushed[i]._valid[State_TextureFilterStage1] && !isSet[State_TextureFilterStage1])
        {
            SetTextureFilter(device, 1, current.MinFilter[1], current.MagFilter[1], current.MipFilter[1], current.AddressU[1], current.AddressV[1], current.Anisotropy[1], true);
            isSet[State_TextureFilterStage1] = true;
        }
    }

    Stored store = _pushed[size - 1];
    _pushed.Remove(size - 1);
    if(_pushed.GetSize()==0)
    {
        _pushed.Clear();
    }
    _stored = store;
}


void CubismRenderState_D3D9::SetBlend(LPDIRECT3DDEVICE9 device, bool enable, bool alphaSeparateEnable,
    D3DBLEND srcmul, D3DBLENDOP blendFunc, D3DBLEND destmul,
    D3DBLEND srcalpha, D3DBLENDOP alphaFunc, D3DBLEND destalpha,
    csmBool force)
{
    if (!_stored._valid[State_Blend] || force ||
        _stored.BlendEnable != enable || _stored.BlendAlphaSeparateEnable != alphaSeparateEnable ||
        _stored.BlendSourceMul != srcmul || _stored.BlendBlendFunc != blendFunc || _stored.BlendDestMul != destmul ||
        _stored.BlendSourceAlpha != srcalpha || _stored.BlendAlphaFunc != alphaFunc || _stored.BlendDestAlpha != destalpha)
    {
        device->SetRenderState(D3DRS_SRCBLEND, srcmul);
        device->SetRenderState(D3DRS_BLENDOP, blendFunc);
        device->SetRenderState(D3DRS_DESTBLEND, destmul);

        device->SetRenderState(D3DRS_SRCBLENDALPHA, srcalpha);
        device->SetRenderState(D3DRS_BLENDOPALPHA, alphaFunc);
        device->SetRenderState(D3DRS_DESTBLENDALPHA, destalpha);

        device->SetRenderState(D3DRS_SEPARATEALPHABLENDENABLE, alphaSeparateEnable);
        device->SetRenderState(D3DRS_ALPHABLENDENABLE, enable);
    }

    _stored.BlendEnable = enable;
    _stored.BlendAlphaSeparateEnable = alphaSeparateEnable;
    _stored.BlendSourceMul = srcmul;
    _stored.BlendBlendFunc = blendFunc;
    _stored.BlendDestMul = destmul;
    _stored.BlendSourceAlpha = srcalpha;
    _stored.BlendAlphaFunc = alphaFunc;
    _stored.BlendDestAlpha = destalpha;

    _stored._valid[State_Blend] = true;
}

void CubismRenderState_D3D9::SetViewport(LPDIRECT3DDEVICE9 device, DWORD left, DWORD top, DWORD width, DWORD height, float zMin, float zMax, csmBool force)
{
    if (!_stored._valid[State_Blend] || force ||
        _stored.ViewportX != left || _stored.ViewportY != top || _stored.ViewportWidth != width || _stored.ViewportHeight != height ||
        _stored.ViewportMinZ != zMin || _stored.ViewportMaxZ != zMax)
    {
        // コンテキストにセット
        D3DVIEWPORT9 viewport;
        viewport.X = left;
        viewport.Y = top;
        viewport.Width = width;
        viewport.Height = height;
        viewport.MinZ = zMin;
        viewport.MaxZ = zMax;
        device->SetViewport(&viewport);
    }

    _stored.ViewportX = left;
    _stored.ViewportY = top;
    _stored.ViewportWidth = width;
    _stored.ViewportHeight = height;
    _stored.ViewportMinZ = zMin;
    _stored.ViewportMaxZ = zMax;

    _stored._valid[State_Viewport] = true;
}

void CubismRenderState_D3D9::SetColorMask(LPDIRECT3DDEVICE9 device, DWORD mask, csmBool force)
{
    if (!_stored._valid[State_Blend] || force ||
        _stored.ColorMaskMask != mask)
    {
        device->SetRenderState(D3DRS_COLORWRITEENABLE, mask);
    }

    _stored.ColorMaskMask = mask;

    _stored._valid[State_ColorMask] = true;
}

void CubismRenderState_D3D9::SetZEnable(LPDIRECT3DDEVICE9 device, D3DZBUFFERTYPE enable, D3DCMPFUNC zfunc, csmBool force)
{
    if (!_stored._valid[State_ZEnable] || force ||
        _stored.ZEnable != enable || _stored.ZFunc != zfunc)
    {
        device->SetRenderState(D3DRS_ZENABLE, enable);
        device->SetRenderState(D3DRS_ZFUNC, zfunc);
    }

    _stored.ZEnable = enable;
    _stored.ZFunc = zfunc;

    _stored._valid[State_ZEnable] = true;
}

void CubismRenderState_D3D9::SetCullMode(LPDIRECT3DDEVICE9 device, D3DCULL cullFace, csmBool force)
{
    if (!_stored._valid[State_CullMode] || force ||
        _stored.CullModeFaceMode != cullFace)
    {
        device->SetRenderState(D3DRS_CULLMODE, cullFace);
    }

    _stored.CullModeFaceMode = cullFace;

    _stored._valid[State_CullMode] = true;
}

void CubismRenderState_D3D9::SetTextureFilter(LPDIRECT3DDEVICE9 device, csmInt32 stage, D3DTEXTUREFILTERTYPE minFilter, D3DTEXTUREFILTERTYPE magFilter, D3DTEXTUREFILTERTYPE mipFilter, D3DTEXTUREADDRESS addressU, D3DTEXTUREADDRESS addressV, csmFloat32 anisotropy, csmBool force)
{
    const csmInt32 stateIndex = State_TextureFilterStage0 + stage;

    // ステージインデックスの範囲内検知
    CSM_ASSERT((stateIndex == State_TextureFilterStage0) || (stateIndex == State_TextureFilterStage1));

    if (!_stored._valid[stateIndex] || force ||
        _stored.MinFilter[stage] != minFilter ||
        _stored.MagFilter[stage] != magFilter ||
        _stored.MipFilter[stage] != mipFilter ||
        _stored.AddressU[stage] != addressU ||
        _stored.AddressV[stage] != addressV ||
        _stored.Anisotropy[stage] != anisotropy)
    {

        device->SetSamplerState(stage, D3DSAMP_MINFILTER, minFilter);
        device->SetSamplerState(stage, D3DSAMP_MAGFILTER, magFilter);
        device->SetSamplerState(stage, D3DSAMP_ADDRESSU, addressU);
        device->SetSamplerState(stage, D3DSAMP_ADDRESSV, addressV);
        if (anisotropy > 0.0f)
        {
            device->SetSamplerState(stage, D3DSAMP_MIPFILTER, D3DTEXF_NONE);
            device->SetSamplerState(stage, D3DSAMP_MAXANISOTROPY, anisotropy);
        }
    }

    _stored.MinFilter[stage] = minFilter;
    _stored.MagFilter[stage] = magFilter;
    _stored.MipFilter[stage] = mipFilter;
    _stored.AddressU[stage] = addressU;
    _stored.AddressV[stage] = addressV;
    _stored.Anisotropy[stage] = anisotropy;

    _stored._valid[stateIndex] = true;
}

void CubismRenderState_D3D9::SaveCurrentNativeState(LPDIRECT3DDEVICE9 device)
{
    // まずは全破棄
    _pushed.Clear();
    // 未設定扱いに
    memset(_stored._valid, 0, sizeof(_stored._valid));

    DWORD setting[16]; // 数は適当
// Blend
    device->GetRenderState(D3DRS_ALPHABLENDENABLE, &setting[0]);
    device->GetRenderState(D3DRS_SEPARATEALPHABLENDENABLE, &setting[1]);
    device->GetRenderState(D3DRS_SRCBLEND, &setting[2]);
    device->GetRenderState(D3DRS_BLENDOP, &setting[3]);
    device->GetRenderState(D3DRS_DESTBLEND, &setting[4]);
    device->GetRenderState(D3DRS_SRCBLENDALPHA, &setting[5]);
    device->GetRenderState(D3DRS_BLENDOPALPHA, &setting[6]);
    device->GetRenderState(D3DRS_DESTBLENDALPHA, &setting[7]);
    SetBlend(device,
        (setting[0] ? true : false),
        (setting[1] ? true : false),
        static_cast<D3DBLEND>(setting[2]), static_cast<D3DBLENDOP>(setting[3]), static_cast<D3DBLEND>(setting[4]),
        static_cast<D3DBLEND>(setting[5]), static_cast<D3DBLENDOP>(setting[6]), static_cast<D3DBLEND>(setting[7]), true);

// Viewport
    D3DVIEWPORT9 viewport;
    device->GetViewport(&viewport);
    SetViewport(device, viewport.X, viewport.Y, viewport.Width, viewport.Height, viewport.MinZ, viewport.MaxZ, true);

// ColorMask
    device->GetRenderState(D3DRS_COLORWRITEENABLE, &setting[0]);
    SetColorMask(device, setting[0], true);

// ZEnable
    device->GetRenderState(D3DRS_ZENABLE, &setting[0]);
    device->GetRenderState(D3DRS_ZFUNC, &setting[1]);
    SetZEnable(device, static_cast<D3DZBUFFERTYPE>(setting[0]), static_cast<D3DCMPFUNC>(setting[1]), true);

// Cull
    device->GetRenderState(D3DRS_CULLMODE, &setting[0]);
    SetCullMode(device, static_cast<D3DCULL>(setting[0]), true);

// TextureFilter
    for (csmInt32 stage = 0; stage < 2; stage++) {
        device->GetSamplerState(stage, D3DSAMP_MINFILTER, &setting[0]);
        device->GetSamplerState(stage, D3DSAMP_MAGFILTER, &setting[1]);
        device->GetSamplerState(stage, D3DSAMP_MIPFILTER, &setting[2]);
        device->GetSamplerState(stage, D3DSAMP_ADDRESSU, &setting[3]);
        device->GetSamplerState(stage, D3DSAMP_ADDRESSV, &setting[4]);
        device->GetSamplerState(stage, D3DSAMP_MAXANISOTROPY, &setting[5]);
        SetTextureFilter(device, stage, static_cast<D3DTEXTUREFILTERTYPE>(setting[0]), static_cast<D3DTEXTUREFILTERTYPE>(setting[1]), static_cast<D3DTEXTUREFILTERTYPE>(setting[2]),
            static_cast<D3DTEXTUREADDRESS>(setting[3]), static_cast<D3DTEXTUREADDRESS>(setting[4]), static_cast<D3DTEXTUREADDRESS>(setting[5]), true);
    }

    // 最後に上記の値を保存
    Save();
}

void CubismRenderState_D3D9::RestoreNativeState(LPDIRECT3DDEVICE9 device)
{
    // 全て再現
    for (csmInt32 i = static_cast<csmInt32>(_pushed.GetSize()) - 1; i >= 0; i--)
    {
        Restore(device);
    }
}

}}}}

//------------ LIVE2D NAMESPACE ------------
