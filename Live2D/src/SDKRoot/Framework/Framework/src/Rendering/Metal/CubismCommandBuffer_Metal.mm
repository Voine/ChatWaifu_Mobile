/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismCommandBuffer_Metal.hpp"
#include "CubismFramework.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D {
namespace Cubism {
namespace Framework {
namespace Rendering {
CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand::DrawCommand()
{
}

CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand::~DrawCommand()
{
}

id <MTLCommandBuffer> CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand::GetMTLCommandBuffer()
{
    return _mtlCommandBuffer;
}

void CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand::SetMTLCommandBuffer(id <MTLCommandBuffer> commandBuffer)
{
    _mtlCommandBuffer = commandBuffer;
}

id <MTLRenderPipelineState> CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand::GetRenderPipelineState()
{
    return _pipelineState;
}

void CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand::SetRenderPipelineState(id <MTLRenderPipelineState> renderPipelineState)
{
    _pipelineState = renderPipelineState;
}

CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommandBuffer()
    : _vbStride(0)
    , _vbCount(0)
    , _ibCount(0)
{
}

CubismCommandBuffer_Metal::DrawCommandBuffer::~DrawCommandBuffer()
{
}

void CubismCommandBuffer_Metal::DrawCommandBuffer::CreateVertexBuffer(id<MTLDevice> device, csmSizeInt stride, csmSizeInt count)
{
    _vbStride = stride;
    _vbCount = count;
    _vertices = [device newBufferWithLength:_vbStride * count
                                                options:MTLResourceStorageModeShared];

    _uvs = [device newBufferWithLength:_vbStride * count
                                                options:MTLResourceStorageModeShared];
}

void CubismCommandBuffer_Metal::DrawCommandBuffer::CreateIndexBuffer(id<MTLDevice> device, csmSizeInt count)
{
    _ibCount = count;
    _indices = [device newBufferWithLength:sizeof(csmInt16) * _ibCount
                                                options:MTLResourceStorageModeShared];
}

void CubismCommandBuffer_Metal::DrawCommandBuffer::UpdateVertexBuffer(void* data, void* uvData, csmSizeInt count)
{
    csmSizeInt length = count * _vbStride;
    csmFloat32* destVertices = reinterpret_cast<csmFloat32*>([_vertices contents]);
    csmFloat32* destUvs = reinterpret_cast<csmFloat32*>([_uvs contents]);
    csmFloat32* sourceVertices = reinterpret_cast<csmFloat32*>(data);
    csmFloat32* sourceUvs = reinterpret_cast<csmFloat32*>(uvData);

    memcpy(destVertices, sourceVertices, length);
    memcpy(destUvs, sourceUvs, length);
}

void CubismCommandBuffer_Metal::DrawCommandBuffer::UpdateIndexBuffer(void* data, csmSizeInt count)
{
    csmSizeInt length = count * sizeof(csmInt16);

    csmInt16* dest = reinterpret_cast<csmInt16*>([_indices contents]);

    csmInt16* sourceIndices = reinterpret_cast<csmInt16*>(data);

    for (csmUint32 i = 0, j = 0; i < count; ++i)
    {

        *dest = *sourceIndices;
        dest++;
        sourceIndices++;
    }
}

CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand* CubismCommandBuffer_Metal::DrawCommandBuffer::GetCommandDraw()
{
    return &_drawCommandDraw;
}

void CubismCommandBuffer_Metal::DrawCommandBuffer::SetCommandBuffer(id <MTLCommandBuffer> commandBuffer)
{
    _drawCommandDraw.SetMTLCommandBuffer(commandBuffer);
}

id <MTLBuffer> CubismCommandBuffer_Metal::DrawCommandBuffer::GetVertexBuffer()
{
    return _vertices;
}

id <MTLBuffer> CubismCommandBuffer_Metal::DrawCommandBuffer::GetUvBuffer()
{
    return _uvs;
}

id <MTLBuffer> CubismCommandBuffer_Metal::DrawCommandBuffer::GetIndexBuffer()
{
    return _indices;
}

CubismCommandBuffer_Metal::CubismCommandBuffer_Metal()
{
}

CubismCommandBuffer_Metal::~CubismCommandBuffer_Metal()
{
}

}}}}
