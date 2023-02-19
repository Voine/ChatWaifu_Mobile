/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <MetalKit/MetalKit.h>
#include "CubismFramework.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

class CubismOffscreenFrame_Metal;

class CubismCommandBuffer_Metal
{
public:
    class DrawCommandBuffer
    {
    public:
        class DrawCommand
        {
        public:
            DrawCommand();
            virtual ~DrawCommand();

            id <MTLCommandBuffer> GetMTLCommandBuffer();
            void SetMTLCommandBuffer(id <MTLCommandBuffer> commandBuffer);

            id <MTLRenderPipelineState> GetRenderPipelineState();
            void SetRenderPipelineState(id <MTLRenderPipelineState> renderPipelineState);

        private:
            DrawCommand& operator=(const DrawCommand&);
            id <MTLCommandBuffer> _mtlCommandBuffer;
            MTLRenderPassDescriptor* _renderPassDescriptor;
            id <MTLRenderPipelineState> _pipelineState;
        };

        DrawCommandBuffer();
        virtual ~DrawCommandBuffer();

        /**
         * @brief  頂点バッファ生成
         * @param  device     MTLDevice
         * @param  stride    頂点ごとのバッファ長
         * @param  count       頂点数
         */
        void CreateVertexBuffer(id<MTLDevice> device, csmSizeInt stride, csmSizeInt count);

        /**
         * @brief  頂点インデックス生成
         * @param  device     MTLDevice
         * @param  count       頂点数
         */
        void CreateIndexBuffer(id<MTLDevice> device, csmSizeInt count);

        /**
         * @brief  頂点バッファ更新
         * @param  data     頂点座標データ
         * @param  uvData    UVデータ
         * @param  count       頂点数
         */
        void UpdateVertexBuffer(void* data, void* uvData, csmSizeInt count);

        /**
         * @brief  頂点インデックス更新
         * @param  data     頂点インデックスデータ
         * @param  count       頂点数
         */
        void UpdateIndexBuffer(void* data, csmSizeInt count);

        void SetCommandBuffer(id <MTLCommandBuffer> commandBuffer);

        DrawCommand* GetCommandDraw();
        id <MTLBuffer> GetVertexBuffer();
        id <MTLBuffer> GetUvBuffer();
        id <MTLBuffer> GetIndexBuffer();

    private:
        DrawCommand _drawCommandDraw;
        csmSizeInt _vbStride;
        csmSizeInt _vbCount;
        csmSizeInt _ibCount;
        csmUint8* _drawBuffer;
        id <MTLBuffer> _vertices;
        id <MTLBuffer> _uvs;
        id <MTLBuffer> _indices;
    };

    CubismCommandBuffer_Metal();
    virtual ~CubismCommandBuffer_Metal();
};

}}}}
