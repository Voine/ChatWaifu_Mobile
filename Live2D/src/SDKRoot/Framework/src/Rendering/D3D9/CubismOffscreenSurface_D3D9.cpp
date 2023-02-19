/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismOffscreenSurface_D3D9.hpp"

#include "CubismRenderer_D3D9.hpp"
#include "CubismShader_D3D9.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

CubismOffscreenFrame_D3D9::CubismOffscreenFrame_D3D9()
    : _texture(NULL)
    , _textureSurface(NULL)
    , _depthSurface(NULL)
    , _backupRender(NULL)
    , _backupDepth(NULL)
    , _bufferWidth(0)
    , _bufferHeight(0)
{
}


void CubismOffscreenFrame_D3D9::BeginDraw(LPDIRECT3DDEVICE9 device)
{
    if(_depthSurface==NULL || _texture==NULL)
    {
        return;
    }

    // オフスクリーン描画は一度通常描画を打ち切る必要がある
    device->EndScene();

    device->BeginScene();

    _backupRender = NULL;
    _backupDepth = NULL;

    // バックバッファのサーフェイスを記憶しておく
    device->GetRenderTarget(0, &_backupRender);
    // ステンシルバッファを取得しておく
    device->GetDepthStencilSurface(&_backupDepth);


    LPDIRECT3DSURFACE9 surface;
    _textureSurface = NULL;
    if (SUCCEEDED(_texture->GetSurfaceLevel(0, &surface)))
    {
        // 記憶しておく
        _textureSurface = surface;

        // マスク描画レンダーターゲットセット
        device->SetRenderTarget(0, surface);
        device->SetDepthStencilSurface(_depthSurface);
    }
}

void CubismOffscreenFrame_D3D9::EndDraw(LPDIRECT3DDEVICE9 device)
{
    if (_depthSurface == NULL || _texture == NULL)
    {
        return;
    }

    device->EndScene();

    // 元に戻す
    if (_textureSurface)
    {
        device->SetRenderTarget(0, _backupRender);
        device->SetDepthStencilSurface(_backupDepth);
        // GetSurfaceLevelでincされたものを下げる必要がある
        {
            _textureSurface->Release();
            _textureSurface = NULL;
        }
    }

    // BeginでGetした分のRelease
    if(_backupDepth)
    {
        _backupDepth->Release();
        _backupDepth = NULL;
    }
    if(_backupRender)
    {
        _backupRender->Release();
        _backupRender = NULL;
    }

    device->BeginScene();
}

void CubismOffscreenFrame_D3D9::Clear(LPDIRECT3DDEVICE9 device,  float r, float g, float b, float a)
{
    // マスクをクリアする
    device->Clear(0, NULL, D3DCLEAR_TARGET | D3DCLEAR_ZBUFFER,
        D3DCOLOR_COLORVALUE(r, g, b, a), 1.0f, 0);
}

csmBool CubismOffscreenFrame_D3D9::CreateOffscreenFrame(LPDIRECT3DDEVICE9 device, csmUint32 displayBufferWidth, csmUint32 displayBufferHeight)
{
    // 一旦削除
    DestroyOffscreenFrame();

    if (FAILED(D3DXCreateTexture(
        device,
        displayBufferWidth,
        displayBufferHeight,
        0,
        D3DUSAGE_RENDERTARGET,
        D3DFMT_A8R8G8B8,
        D3DPOOL_DEFAULT,
        &_texture)))
    {
        return false;
    }

    if (FAILED(device->CreateDepthStencilSurface(
        displayBufferWidth,
        displayBufferHeight,
        D3DFMT_D16,
        D3DMULTISAMPLE_NONE,
        0,
        TRUE,
        &_depthSurface,
        NULL)))
    {
        if(_texture)
        {
            _texture->Release();
            _texture = NULL;
        }
        return false;
    }

    _bufferWidth = displayBufferWidth;
    _bufferHeight = displayBufferHeight;

    return true;
}

void CubismOffscreenFrame_D3D9::DestroyOffscreenFrame()
{
    // これらがあるのはEndDrawが来なかった場合
    if(_backupDepth)
    {
        _backupDepth->Release();
        _backupDepth = NULL;
    }
    if (_backupRender)
    {
        _backupRender->Release();
        _backupRender = NULL;
    }
    if (_textureSurface)
    {
        _textureSurface->Release();
        _textureSurface = NULL;
    }


    if(_depthSurface)
    {
        _depthSurface->Release();
        _depthSurface = NULL;
    }
    if (_texture)
    {
        _texture->Release();
        _texture = NULL;
    }
}

LPDIRECT3DTEXTURE9 CubismOffscreenFrame_D3D9::GetTexture() const
{
    return _texture;
}

csmUint32 CubismOffscreenFrame_D3D9::GetBufferWidth() const
{
    return _bufferWidth;
}

csmUint32 CubismOffscreenFrame_D3D9::GetBufferHeight() const
{
    return _bufferHeight;
}

csmBool CubismOffscreenFrame_D3D9::IsValid() const
{
    if (_depthSurface == NULL || _texture == NULL)
    {
        return false;
    }

    return true;
}

}}}}

//------------ LIVE2D NAMESPACE ------------
