/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismRenderer_Metal.hpp"
#include "Math/CubismMatrix44.hpp"
#include "Model/CubismModel.hpp"
#include "CubismRenderingInstanceSingleton_Metal.h"
#include "MetalShaderTypes.h"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

/*********************************************************************************************************************
*                                      CubismClippingManager_Metal
********************************************************************************************************************/
///< ファイルスコープの変数宣言
namespace {
const csmInt32 ColorChannelCount = 4;   ///< 実験時に1チャンネルの場合は1、RGBだけの場合は3、アルファも含める場合は4
}

CubismClippingManager_Metal::CubismClippingManager_Metal() :
                                                _currentFrameNo(0),
                                                _clippingMaskBufferSize(256, 256)
{
    CubismRenderer::CubismTextureColor* tmp;
    tmp = CSM_NEW CubismRenderer::CubismTextureColor();
    tmp->R = 1.0f;
    tmp->G = 0.0f;
    tmp->B = 0.0f;
    tmp->A = 0.0f;
    _channelColors.PushBack(tmp);
    tmp = CSM_NEW CubismRenderer::CubismTextureColor();
    tmp->R = 0.0f;
    tmp->G = 1.0f;
    tmp->B = 0.0f;
    tmp->A = 0.0f;
    _channelColors.PushBack(tmp);
    tmp = CSM_NEW CubismRenderer::CubismTextureColor();
    tmp->R = 0.0f;
    tmp->G = 0.0f;
    tmp->B = 1.0f;
    tmp->A = 0.0f;
    _channelColors.PushBack(tmp);
    tmp = CSM_NEW CubismRenderer::CubismTextureColor();
    tmp->R = 0.0f;
    tmp->G = 0.0f;
    tmp->B = 0.0f;
    tmp->A = 1.0f;
    _channelColors.PushBack(tmp);

}

CubismClippingManager_Metal::~CubismClippingManager_Metal()
{
    for (csmUint32 i = 0; i < _clippingContextListForMask.GetSize(); i++)
    {
        if (_clippingContextListForMask[i]) CSM_DELETE_SELF(CubismClippingContext, _clippingContextListForMask[i]);
        _clippingContextListForMask[i] = NULL;
    }

    // _clippingContextListForDrawは_clippingContextListForMaskにあるインスタンスを指している。上記の処理により要素ごとのDELETEは不要。
    for (csmUint32 i = 0; i < _clippingContextListForDraw.GetSize(); i++)
    {
        _clippingContextListForDraw[i] = NULL;
    }

    for (csmUint32 i = 0; i < _channelColors.GetSize(); i++)
    {
        if (_channelColors[i]) CSM_DELETE(_channelColors[i]);
        _channelColors[i] = NULL;
    }
}

void CubismClippingManager_Metal::Initialize(CubismModel& model, csmInt32 drawableCount, const csmInt32** drawableMasks, const csmInt32* drawableMaskCounts)
{
    //クリッピングマスクを使う描画オブジェクトを全て登録する
    //クリッピングマスクは、通常数個程度に限定して使うものとする
    for (csmInt32 i = 0; i < drawableCount; i++)
    {
        if (drawableMaskCounts[i] <= 0)
        {
            //クリッピングマスクが使用されていないアートメッシュ（多くの場合使用しない）
            _clippingContextListForDraw.PushBack(NULL);
            continue;
        }

        // 既にあるClipContextと同じかチェックする
        CubismClippingContext* cc = FindSameClip(drawableMasks[i], drawableMaskCounts[i]);
        if (cc == NULL)
        {
            // 同一のマスクが存在していない場合は生成する
            cc = CSM_NEW CubismClippingContext(this, model, drawableMasks[i], drawableMaskCounts[i]);
            _clippingContextListForMask.PushBack(cc);
        }

        cc->AddClippedDrawable(i);

        _clippingContextListForDraw.PushBack(cc);

    }
}

CubismClippingContext* CubismClippingManager_Metal::FindSameClip(const csmInt32* drawableMasks, csmInt32 drawableMaskCounts) const
{
    // 作成済みClippingContextと一致するか確認
    for (csmUint32 i = 0; i < _clippingContextListForMask.GetSize(); i++)
    {
        CubismClippingContext* cc = _clippingContextListForMask[i];
        const csmInt32 count = cc->_clippingIdCount;
        if (count != drawableMaskCounts) continue; //個数が違う場合は別物
        csmInt32 samecount = 0;

        // 同じIDを持つか確認。配列の数が同じなので、一致した個数が同じなら同じ物を持つとする。
        for (csmInt32 j = 0; j < count; j++)
        {
            const csmInt32 clipId = cc->_clippingIdList[j];
            for (csmInt32 k = 0; k < count; k++)
            {
                if (drawableMasks[k] == clipId)
                {
                    samecount++;
                    break;
                }
            }
        }
        if (samecount == count)
        {
            return cc;
        }
    }
    return NULL; //見つからなかった
}

void CubismClippingManager_Metal::SetupClippingContext(CubismModel& model, CubismRenderer_Metal* renderer, CubismOffscreenFrame_Metal* lastColorBuffer, csmRectF lastViewport)
{
    _currentFrameNo++;

    // 全てのクリッピングを用意する
    // 同じクリップ（複数の場合はまとめて１つのクリップ）を使う場合は１度だけ設定する
    csmInt32 usingClipCount = 0;
    for (csmUint32 clipIndex = 0; clipIndex < _clippingContextListForMask.GetSize(); clipIndex++)
    {
        // １つのクリッピングマスクに関して
        CubismClippingContext* cc = _clippingContextListForMask[clipIndex];

        // このクリップを利用する描画オブジェクト群全体を囲む矩形を計算
        CalcClippedDrawTotalBounds(model, cc);

        if (cc->_isUsing)
        {
            usingClipCount++; //使用中としてカウント
        }
    }

    // マスク作成処理
    if (usingClipCount > 0)
    {
        id <MTLRenderCommandEncoder> renderEncoder = nil;
        MTLViewport clipVp = {0, 0, GetClippingMaskBufferSize().X, GetClippingMaskBufferSize().Y, 0.0, 1.0};

        // 前処理方式の場合
        if (!renderer->IsUsingHighPrecisionMask())
        {
            // 生成したFrameBufferと同じサイズでビューポートを設定

            // モデル描画時にDrawMeshNowに渡される変換（モデルtoワールド座標変換）
            CubismMatrix44 modelToWorldF = renderer->GetMvpMatrix();

            renderEncoder = renderer->PreDraw(renderer->s_commandBuffer, renderer->_offscreenFrameBuffer.GetRenderPassDescriptor());
            [renderEncoder setViewport:clipVp];
        }

        // 各マスクのレイアウトを決定していく
        SetupLayoutBounds(renderer->IsUsingHighPrecisionMask() ? 0 : usingClipCount);

        // 実際にマスクを生成する
        // 全てのマスクをどの様にレイアウトして描くかを決定し、ClipContext , ClippedDrawContext に記憶する
        for (csmUint32 clipIndex = 0; clipIndex < _clippingContextListForMask.GetSize(); clipIndex++)
        {
            // --- 実際に１つのマスクを描く ---
            CubismClippingContext* clipContext = _clippingContextListForMask[clipIndex];
            csmRectF* allClippedDrawRect = clipContext->_allClippedDrawRect; //このマスクを使う、全ての描画オブジェクトの論理座標上の囲み矩形
            csmRectF* layoutBoundsOnTex01 = clipContext->_layoutBounds; //この中にマスクを収める
            const csmFloat32 MARGIN = 0.05f;
            csmFloat32 scaleX = 0.0f;
            csmFloat32 scaleY = 0.0f;

            if (renderer->IsUsingHighPrecisionMask())
            {
                const csmFloat32 ppu = model.GetPixelsPerUnit();
                const csmFloat32 maskPixelWidth = clipContext->_owner->_clippingMaskBufferSize.X;
                const csmFloat32 maskPixelHeight = clipContext->_owner->_clippingMaskBufferSize.Y;
                const csmFloat32 physicalMaskWidth = layoutBoundsOnTex01->Width * maskPixelWidth;
                const csmFloat32 physicalMaskHeight = layoutBoundsOnTex01->Height * maskPixelHeight;


                _tmpBoundsOnModel.SetRect(allClippedDrawRect);

                if (_tmpBoundsOnModel.Width * ppu > physicalMaskWidth)
                {
                    _tmpBoundsOnModel.Expand(allClippedDrawRect->Width * MARGIN, 0.0f);
                    scaleX = layoutBoundsOnTex01->Width / _tmpBoundsOnModel.Width;
                }
                else
                {
                    scaleX = ppu / physicalMaskWidth;
                }

                if (_tmpBoundsOnModel.Height * ppu > physicalMaskHeight)
                {
                    _tmpBoundsOnModel.Expand(0.0f, allClippedDrawRect->Height * MARGIN);
                    scaleY = layoutBoundsOnTex01->Height / _tmpBoundsOnModel.Height;
                }
                else
                {
                    scaleY = ppu / physicalMaskHeight;
                }
            }
            else
            {
                // モデル座標上の矩形を、適宜マージンを付けて使う
                _tmpBoundsOnModel.SetRect(allClippedDrawRect);
                _tmpBoundsOnModel.Expand(allClippedDrawRect->Width * MARGIN, allClippedDrawRect->Height * MARGIN);
                //########## 本来は割り当てられた領域の全体を使わず必要最低限のサイズがよい

                // シェーダ用の計算式を求める。回転を考慮しない場合は以下のとおり
                // movePeriod' = movePeriod * scaleX + offX     [[ movePeriod' = (movePeriod - tmpBoundsOnModel.movePeriod)*scale + layoutBoundsOnTex01.movePeriod ]]
                scaleX = layoutBoundsOnTex01->Width / _tmpBoundsOnModel.Width;
                scaleY = layoutBoundsOnTex01->Height / _tmpBoundsOnModel.Height;
            }

            // マスク生成時に使う行列を求める
            {
                // シェーダに渡す行列を求める <<<<<<<<<<<<<<<<<<<<<<<< 要最適化（逆順に計算すればシンプルにできる）
                _tmpMatrix.LoadIdentity();
                {
                    // Layout0..1 を -1..1に変換
                    _tmpMatrix.TranslateRelative(-1.0f, -1.0f);
                    _tmpMatrix.ScaleRelative(2.0f, 2.0f);
                }
                {
                    // view to Layout0..1
                    _tmpMatrix.TranslateRelative(layoutBoundsOnTex01->X, layoutBoundsOnTex01->Y); //new = [translate]
                    _tmpMatrix.ScaleRelative(scaleX, scaleY); //new = [translate][scale]
                    _tmpMatrix.TranslateRelative(-_tmpBoundsOnModel.X, -_tmpBoundsOnModel.Y);
                    //new = [translate][scale][translate]
                }
                // tmpMatrixForMask が計算結果
                _tmpMatrixForMask.SetMatrix(_tmpMatrix.GetArray());
            }

            //--------- draw時の mask 参照用行列を計算
            {
                // シェーダに渡す行列を求める <<<<<<<<<<<<<<<<<<<<<<<< 要最適化（逆順に計算すればシンプルにできる）
                _tmpMatrix.LoadIdentity();
                {
                    _tmpMatrix.TranslateRelative(layoutBoundsOnTex01->X, layoutBoundsOnTex01->Y); //new = [translate]
                    _tmpMatrix.ScaleRelative(scaleX, scaleY); //new = [translate][scale]
                    _tmpMatrix.TranslateRelative(-_tmpBoundsOnModel.X, -_tmpBoundsOnModel.Y);
                    //new = [translate][scale][translate]
                }

                _tmpMatrixForDraw.SetMatrix(_tmpMatrix.GetArray());
            }

            clipContext->_matrixForMask.SetMatrix(_tmpMatrixForMask.GetArray());

            clipContext->_matrixForDraw.SetMatrix(_tmpMatrixForDraw.GetArray());

            if (!renderer->IsUsingHighPrecisionMask())
            {
                const csmInt32 clipDrawCount = clipContext->_clippingIdCount;
                for (csmInt32 i = 0; i < clipDrawCount; i++)
                {
                    const csmInt32 clipDrawIndex = clipContext->_clippingIdList[i];
                    CubismCommandBuffer_Metal::DrawCommandBuffer* drawCommandBufferData = clipContext->_clippingCommandBufferList->At(i);// [i];

                    // 頂点情報が更新されておらず、信頼性がない場合は描画をパスする
                    if (!model.GetDrawableDynamicFlagVertexPositionsDidChange(clipDrawIndex))
                    {
                        continue;
                    }

                    {
                        // Update Vertex / Index buffer.
                        {
                            csmFloat32* vertices = const_cast<csmFloat32*>(model.GetDrawableVertices(clipDrawIndex));
                            Core::csmVector2* uvs = const_cast<Core::csmVector2*>(model.GetDrawableVertexUvs(clipDrawIndex));
                            csmUint16* vertexIndices = const_cast<csmUint16*>(model.GetDrawableVertexIndices(clipDrawIndex));
                            const csmUint32 vertexCount = model.GetDrawableVertexCount(clipDrawIndex);
                            const csmUint32 vertexIndexCount = model.GetDrawableVertexIndexCount(clipDrawIndex);

                            drawCommandBufferData->UpdateVertexBuffer(vertices, uvs, vertexCount);
                            if (vertexIndexCount > 0)
                            {
                                drawCommandBufferData->UpdateIndexBuffer(vertexIndices, vertexIndexCount);
                            }

                            if (vertexCount <= 0)
                            {
                                continue;
                            }

                        }
                    }

                    renderer->IsCulling(model.GetDrawableCulling(clipDrawIndex) != 0);

                    // 今回専用の変換を適用して描く
                    // チャンネルも切り替える必要がある(A,R,G,B)
                    renderer->SetClippingContextBufferForMask(clipContext);

                    renderer->DrawMeshMetal(
                        drawCommandBufferData,
                        model.GetDrawableTextureIndex(clipDrawIndex),
                        model.GetDrawableVertexIndexCount(clipDrawIndex),
                        model.GetDrawableVertexCount(clipDrawIndex),
                        const_cast<csmUint16*>(model.GetDrawableVertexIndices(clipDrawIndex)),
                        const_cast<csmFloat32*>(model.GetDrawableVertices(clipDrawIndex)),
                        reinterpret_cast<csmFloat32*>(const_cast<Core::csmVector2*>(model.GetDrawableVertexUvs(clipDrawIndex))),
                        model.GetMultiplyColor(clipDrawIndex),
                        model.GetScreenColor(clipDrawIndex),
                        model.GetDrawableOpacity(clipDrawIndex),
                        CubismRenderer::CubismBlendMode_Normal,   //クリッピングは通常描画を強制
                        false,   // マスク生成時はクリッピングの反転使用は全く関係がない
                        clipDrawIndex,
                        renderEncoder
                    );
                }
            }
        }

        if (!renderer->IsUsingHighPrecisionMask())
        {
            // --- 後処理 ---
            [renderEncoder endEncoding];
            renderer->SetClippingContextBufferForMask(NULL);
        }
    }
}

void CubismClippingManager_Metal::CalcClippedDrawTotalBounds(CubismModel& model, CubismClippingContext* clippingContext)
{
    // 被クリッピングマスク（マスクされる描画オブジェクト）の全体の矩形
    csmFloat32 clippedDrawTotalMinX = FLT_MAX, clippedDrawTotalMinY = FLT_MAX;
    csmFloat32 clippedDrawTotalMaxX = -FLT_MAX, clippedDrawTotalMaxY = -FLT_MAX;

    // このマスクが実際に必要か判定する
    // このクリッピングを利用する「描画オブジェクト」がひとつでも使用可能であればマスクを生成する必要がある

    const csmInt32 clippedDrawCount = clippingContext->_clippedDrawableIndexList->GetSize();
    for (csmInt32 clippedDrawableIndex = 0; clippedDrawableIndex < clippedDrawCount; clippedDrawableIndex++)
    {
        // マスクを使用する描画オブジェクトの描画される矩形を求める
        const csmInt32 drawableIndex = (*clippingContext->_clippedDrawableIndexList)[clippedDrawableIndex];

        const csmInt32 drawableVertexCount = model.GetDrawableVertexCount(drawableIndex);
        csmFloat32* drawableVertexes = const_cast<csmFloat32*>(model.GetDrawableVertices(drawableIndex));

        csmFloat32 minX = FLT_MAX, minY = FLT_MAX;
        csmFloat32 maxX = -FLT_MAX, maxY = -FLT_MAX;

        csmInt32 loop = drawableVertexCount * Constant::VertexStep;
        for (csmInt32 pi = Constant::VertexOffset; pi < loop; pi += Constant::VertexStep)
        {
            csmFloat32 x = drawableVertexes[pi];
            csmFloat32 y = drawableVertexes[pi + 1];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        //
        if (minX == FLT_MAX) continue; //有効な点がひとつも取れなかったのでスキップする

        // 全体の矩形に反映
        if (minX < clippedDrawTotalMinX) clippedDrawTotalMinX = minX;
        if (minY < clippedDrawTotalMinY) clippedDrawTotalMinY = minY;
        if (maxX > clippedDrawTotalMaxX) clippedDrawTotalMaxX = maxX;
        if (maxY > clippedDrawTotalMaxY) clippedDrawTotalMaxY = maxY;
    }
    if (clippedDrawTotalMinX == FLT_MAX)
    {
        clippingContext->_allClippedDrawRect->X = 0.0f;
        clippingContext->_allClippedDrawRect->Y = 0.0f;
        clippingContext->_allClippedDrawRect->Width = 0.0f;
        clippingContext->_allClippedDrawRect->Height = 0.0f;
        clippingContext->_isUsing = false;
    }
    else
    {
        clippingContext->_isUsing = true;
        csmFloat32 w = clippedDrawTotalMaxX - clippedDrawTotalMinX;
        csmFloat32 h = clippedDrawTotalMaxY - clippedDrawTotalMinY;
        clippingContext->_allClippedDrawRect->X = clippedDrawTotalMinX;
        clippingContext->_allClippedDrawRect->Y = clippedDrawTotalMinY;
        clippingContext->_allClippedDrawRect->Width = w;
        clippingContext->_allClippedDrawRect->Height = h;
    }
}

void CubismClippingManager_Metal::SetupLayoutBounds(csmInt32 usingClipCount) const
{
    if (usingClipCount <= 0)
    {// この場合は一つのマスクターゲットを毎回クリアして使用する
        for (csmUint32 index = 0; index < _clippingContextListForMask.GetSize(); index++)
        {
            CubismClippingContext* cc = _clippingContextListForMask[index];
            cc->_layoutChannelNo = 0; // どうせ毎回消すので固定で良い
            cc->_layoutBounds->X = 0.0f;
            cc->_layoutBounds->Y = 0.0f;
            cc->_layoutBounds->Width = 1.0f;
            cc->_layoutBounds->Height = 1.0f;
        }
        return;
    }

    // ひとつのRenderTextureを極力いっぱいに使ってマスクをレイアウトする
    // マスクグループの数が4以下ならRGBA各チャンネルに１つずつマスクを配置し、5以上6以下ならRGBAを2,2,1,1と配置する

    // RGBAを順番に使っていく。
    const csmInt32 div = usingClipCount / ColorChannelCount; //１チャンネルに配置する基本のマスク個数
    const csmInt32 mod = usingClipCount % ColorChannelCount; //余り、この番号のチャンネルまでに１つずつ配分する

    // RGBAそれぞれのチャンネルを用意していく(0:R , 1:G , 2:B, 3:A, )
    csmInt32 curClipIndex = 0; //順番に設定していく

    for (csmInt32 channelNo = 0; channelNo < ColorChannelCount; channelNo++)
    {
        // このチャンネルにレイアウトする数
        const csmInt32 layoutCount = div + (channelNo < mod ? 1 : 0);

        // 分割方法を決定する
        if (layoutCount == 0)
        {
            // 何もしない
        }
        else if (layoutCount == 1)
        {
            //全てをそのまま使う
            CubismClippingContext* cc = _clippingContextListForMask[curClipIndex++];
            cc->_layoutChannelNo = channelNo;
            cc->_layoutBounds->X = 0.0f;
            cc->_layoutBounds->Y = 0.0f;
            cc->_layoutBounds->Width = 1.0f;
            cc->_layoutBounds->Height = 1.0f;
        }
        else if (layoutCount == 2)
        {
            for (csmInt32 i = 0; i < layoutCount; i++)
            {
                const csmInt32 xpos = i % 2;

                CubismClippingContext* cc = _clippingContextListForMask[curClipIndex++];
                cc->_layoutChannelNo = channelNo;

                cc->_layoutBounds->X = xpos * 0.5f;
                cc->_layoutBounds->Y = 0.0f;
                cc->_layoutBounds->Width = 0.5f;
                cc->_layoutBounds->Height = 1.0f;
                //UVを2つに分解して使う
            }
        }
        else if (layoutCount <= 4)
        {
            //4分割して使う
            for (csmInt32 i = 0; i < layoutCount; i++)
            {
                const csmInt32 xpos = i % 2;
                const csmInt32 ypos = i / 2;

                CubismClippingContext* cc = _clippingContextListForMask[curClipIndex++];
                cc->_layoutChannelNo = channelNo;

                cc->_layoutBounds->X = xpos * 0.5f;
                cc->_layoutBounds->Y = ypos * 0.5f;
                cc->_layoutBounds->Width = 0.5f;
                cc->_layoutBounds->Height = 0.5f;
            }
        }
        else if (layoutCount <= 9)
        {
            //9分割して使う
            for (csmInt32 i = 0; i < layoutCount; i++)
            {
                const csmInt32 xpos = i % 3;
                const csmInt32 ypos = i / 3;

                CubismClippingContext* cc = _clippingContextListForMask[curClipIndex++];
                cc->_layoutChannelNo = channelNo;

                cc->_layoutBounds->X = xpos / 3.0f;
                cc->_layoutBounds->Y = ypos / 3.0f;
                cc->_layoutBounds->Width = 1.0f / 3.0f;
                cc->_layoutBounds->Height = 1.0f / 3.0f;
            }
        }
        else
        {
            CubismLogError("not supported mask count : %d", layoutCount);

            // 開発モードの場合は停止させる
            CSM_ASSERT(0);

            // 引き続き実行する場合、 SetupShaderProgramでオーバーアクセスが発生するので仕方なく適当に入れておく
            // もちろん描画結果はろくなことにならない
            for (csmInt32 i = 0; i < layoutCount; i++)
            {
                CubismClippingContext* cc = _clippingContextListForMask[curClipIndex++];
                cc->_layoutChannelNo = 0;
                cc->_layoutBounds->X = 0.0f;
                cc->_layoutBounds->Y = 0.0f;
                cc->_layoutBounds->Width = 1.0f;
                cc->_layoutBounds->Height = 1.0f;
            }
        }
    }
}

CubismRenderer::CubismTextureColor* CubismClippingManager_Metal::GetChannelFlagAsColor(csmInt32 channelNo)
{
    return _channelColors[channelNo];
}

csmVector<CubismClippingContext*>* CubismClippingManager_Metal::GetClippingContextListForDraw()
{
    return &_clippingContextListForDraw;
}

void CubismClippingManager_Metal::SetClippingMaskBufferSize(csmFloat32 width, csmFloat32 height)
{
    _clippingMaskBufferSize = CubismVector2(width, height);
}

CubismVector2 CubismClippingManager_Metal::GetClippingMaskBufferSize() const
{
    return _clippingMaskBufferSize;
}

/*********************************************************************************************************************
*                                      CubismClippingContext
********************************************************************************************************************/
CubismClippingContext::CubismClippingContext(CubismClippingManager_Metal* manager, CubismModel& model, const csmInt32* clippingDrawableIndices, csmInt32 clipCount)
{
    CubismRenderingInstanceSingleton_Metal *single = [CubismRenderingInstanceSingleton_Metal sharedManager];
    id <MTLDevice> device = [single getMTLDevice];
    CAMetalLayer* metalLayer = [single getMetalLayer];

    _owner = manager;

    // クリップしている（＝マスク用の）Drawableのインデックスリスト
    _clippingIdList = clippingDrawableIndices;

    // マスクの数
    _clippingIdCount = clipCount;

    _layoutChannelNo = 0;

    _allClippedDrawRect = CSM_NEW csmRectF();
    _layoutBounds = CSM_NEW csmRectF();

    _clippedDrawableIndexList = CSM_NEW csmVector<csmInt32>();
    _clippingCommandBufferList = CSM_NEW csmVector<CubismCommandBuffer_Metal::DrawCommandBuffer*>;


    for (csmUint32 i = 0; i < _clippingIdCount; ++i)
    {
        const csmInt32 clippingId = _clippingIdList[i];
        CubismCommandBuffer_Metal::DrawCommandBuffer* drawCommandBuffer = NULL;
        const csmInt32 drawableVertexCount = model.GetDrawableVertexCount(clippingId);
        const csmInt32 drawableVertexIndexCount = model.GetDrawableVertexIndexCount(clippingId);
        const csmSizeInt vertexSize = sizeof(csmFloat32) * 2;


        drawCommandBuffer = CSM_NEW CubismCommandBuffer_Metal::DrawCommandBuffer();
        drawCommandBuffer->CreateVertexBuffer(device, vertexSize, drawableVertexCount * 2);      // Vertices + UVs
        drawCommandBuffer->CreateIndexBuffer(device, drawableVertexIndexCount);


        _clippingCommandBufferList->PushBack(drawCommandBuffer);
    }
}

CubismClippingContext::~CubismClippingContext()
{
    if (_layoutBounds != NULL)
    {
        CSM_DELETE(_layoutBounds);
        _layoutBounds = NULL;
    }

    if (_allClippedDrawRect != NULL)
    {
        CSM_DELETE(_allClippedDrawRect);
        _allClippedDrawRect = NULL;
    }

    if (_clippedDrawableIndexList != NULL)
    {
        CSM_DELETE(_clippedDrawableIndexList);
        _clippedDrawableIndexList = NULL;
    }

    if (_clippingCommandBufferList != NULL)
    {
        for (csmUint32 i = 0; i < _clippingCommandBufferList->GetSize(); ++i)
        {
            CSM_DELETE(_clippingCommandBufferList->At(i));
            _clippingCommandBufferList->At(i) = NULL;
        }

        CSM_DELETE(_clippingCommandBufferList);
        _clippingCommandBufferList = NULL;
    }
}

void CubismClippingContext::AddClippedDrawable(csmInt32 drawableIndex)
{
    _clippedDrawableIndexList->PushBack(drawableIndex);
}

CubismClippingManager_Metal* CubismClippingContext::GetClippingManager()
{
    return _owner;
}

/*********************************************************************************************************************
*                                       CubismShader_Metal
********************************************************************************************************************/
namespace {
    const csmInt32 ShaderCount = 19; ///< シェーダの数 = マスク生成用 + (通常 + 加算 + 乗算) * (マスク無 + マスク有 + マスク有反転 + マスク無の乗算済アルファ対応版 + マスク有の乗算済アルファ対応版 + マスク有反転の乗算済アルファ対応版)
    CubismShader_Metal* s_instance;
}

enum ShaderNames
{
    // SetupMask
    ShaderNames_SetupMask,

    //Normal
    ShaderNames_Normal,
    ShaderNames_NormalMasked,
    ShaderNames_NormalMaskedInverted,
    ShaderNames_NormalPremultipliedAlpha,
    ShaderNames_NormalMaskedPremultipliedAlpha,
    ShaderNames_NormalMaskedInvertedPremultipliedAlpha,

    //Add
    ShaderNames_Add,
    ShaderNames_AddMasked,
    ShaderNames_AddMaskedInverted,
    ShaderNames_AddPremultipliedAlpha,
    ShaderNames_AddMaskedPremultipliedAlpha,
    ShaderNames_AddMaskedPremultipliedAlphaInverted,

    //Mult
    ShaderNames_Mult,
    ShaderNames_MultMasked,
    ShaderNames_MultMaskedInverted,
    ShaderNames_MultPremultipliedAlpha,
    ShaderNames_MultMaskedPremultipliedAlpha,
    ShaderNames_MultMaskedPremultipliedAlphaInverted,
};

//// SetupMask
static const csmChar* VertShaderSrcSetupMask = "VertShaderSrcSetupMask";

static const csmChar* FragShaderSrcSetupMask = "FragShaderSrcSetupMask";

//----- バーテックスシェーダプログラム -----
// Normal & Add & Mult 共通
static const csmChar* VertShaderSrc = "VertShaderSrc";

// Normal & Add & Mult 共通（クリッピングされたものの描画用）
static const csmChar* VertShaderSrcMasked = "VertShaderSrcMasked";

//----- フラグメントシェーダプログラム -----
// Normal & Add & Mult 共通
static const csmChar* FragShaderSrc = "FragShaderSrc";

// Normal & Add & Mult 共通 （PremultipliedAlpha）
static const csmChar* FragShaderSrcPremultipliedAlpha = "FragShaderSrcPremultipliedAlpha";

// Normal & Add & Mult 共通（クリッピングされたものの描画用）
static const csmChar* FragShaderSrcMask = "FragShaderSrcMask";

// Normal & Add & Mult 共通（クリッピングされて反転使用の描画用）
static const csmChar* FragShaderSrcMaskInverted = "FragShaderSrcMaskInverted";

// Normal & Add & Mult 共通（クリッピングされたものの描画用、PremultipliedAlphaの場合）
static const csmChar* FragShaderSrcMaskPremultipliedAlpha = "FragShaderSrcMaskPremultipliedAlpha";

// Normal & Add & Mult 共通（クリッピングされて反転使用の描画用、PremultipliedAlphaの場合）
static const csmChar* FragShaderSrcMaskInvertedPremultipliedAlpha = "FragShaderSrcMaskInvertedPremultipliedAlpha";

CubismShader_Metal::CubismShader_Metal()
{
}

CubismShader_Metal::~CubismShader_Metal()
{
}

CubismShader_Metal* CubismShader_Metal::GetInstance()
{
    if (s_instance == NULL)
    {
        s_instance = CSM_NEW CubismShader_Metal();
    }
    return s_instance;
}

void CubismShader_Metal::DeleteInstance()
{
    if (s_instance)
    {
        CSM_DELETE_SELF(CubismShader_Metal, s_instance);
        s_instance = NULL;
    }
}


void CubismShader_Metal::GenerateShaders(CubismRenderer_Metal* renderer)
{
    for (csmInt32 i = 0; i < ShaderCount; i++)
    {
        _shaderSets.PushBack(CSM_NEW CubismShaderSet());
    }

    //シェーダライブラリのロード（.metal）
    CubismRenderingInstanceSingleton_Metal *single = [CubismRenderingInstanceSingleton_Metal sharedManager];
    id <MTLDevice> device = [single getMTLDevice];
    _shaderLib = [device newDefaultLibrary];

    if(!_shaderLib)
    {
        NSLog(@" ERROR: Couldnt create a default shader library");
        // assert here because if the shader libary isn't loading, nothing good will happen
        return;
    }

    _shaderSets[0]->ShaderProgram = LoadShaderProgram(VertShaderSrcSetupMask, FragShaderSrcSetupMask);

    _shaderSets[1]->ShaderProgram = LoadShaderProgram(VertShaderSrc, FragShaderSrc);
    _shaderSets[2]->ShaderProgram = LoadShaderProgram(VertShaderSrcMasked, FragShaderSrcMask);
    _shaderSets[3]->ShaderProgram = LoadShaderProgram(VertShaderSrcMasked, FragShaderSrcMaskInverted);
    _shaderSets[4]->ShaderProgram = LoadShaderProgram(VertShaderSrc, FragShaderSrcPremultipliedAlpha);
    _shaderSets[5]->ShaderProgram = LoadShaderProgram(VertShaderSrcMasked, FragShaderSrcMaskPremultipliedAlpha);
    _shaderSets[6]->ShaderProgram = LoadShaderProgram(VertShaderSrcMasked, FragShaderSrcMaskInvertedPremultipliedAlpha);

    _shaderSets[0]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[0]->ShaderProgram, -1);
    _shaderSets[0]->SamplerState = MakeSamplerState(device, renderer);

    _shaderSets[1]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[1]->ShaderProgram, CubismRenderer::CubismBlendMode_Normal);
    _shaderSets[2]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[2]->ShaderProgram, CubismRenderer::CubismBlendMode_Normal);
    _shaderSets[3]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[3]->ShaderProgram, CubismRenderer::CubismBlendMode_Normal);
    _shaderSets[4]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[4]->ShaderProgram, CubismRenderer::CubismBlendMode_Normal);
    _shaderSets[5]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[5]->ShaderProgram, CubismRenderer::CubismBlendMode_Normal);
    _shaderSets[6]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[6]->ShaderProgram, CubismRenderer::CubismBlendMode_Normal);

    _shaderSets[1]->DepthStencilState = MakeDepthStencilState(device);
    _shaderSets[2]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[3]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[4]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[5]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[6]->DepthStencilState = _shaderSets[1]->DepthStencilState;

    _shaderSets[1]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[2]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[3]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[4]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[5]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[6]->SamplerState = _shaderSets[0]->SamplerState;

    // 加算も通常と同じシェーダーを利用する
    _shaderSets[7]->ShaderProgram = _shaderSets[1]->ShaderProgram;
    _shaderSets[8]->ShaderProgram = _shaderSets[2]->ShaderProgram;
    _shaderSets[9]->ShaderProgram = _shaderSets[3]->ShaderProgram;
    _shaderSets[10]->ShaderProgram = _shaderSets[4]->ShaderProgram;
    _shaderSets[11]->ShaderProgram = _shaderSets[5]->ShaderProgram;
    _shaderSets[12]->ShaderProgram = _shaderSets[6]->ShaderProgram;

    _shaderSets[7]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[7]->ShaderProgram, CubismRenderer::CubismBlendMode_Additive);
    _shaderSets[8]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[8]->ShaderProgram, CubismRenderer::CubismBlendMode_Additive);
    _shaderSets[9]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[9]->ShaderProgram, CubismRenderer::CubismBlendMode_Additive);
    _shaderSets[10]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[10]->ShaderProgram, CubismRenderer::CubismBlendMode_Additive);
    _shaderSets[11]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[11]->ShaderProgram, CubismRenderer::CubismBlendMode_Additive);
    _shaderSets[12]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[12]->ShaderProgram, CubismRenderer::CubismBlendMode_Additive);

    _shaderSets[7]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[8]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[9]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[10]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[11]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[12]->DepthStencilState = _shaderSets[1]->DepthStencilState;

    _shaderSets[7]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[8]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[9]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[10]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[11]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[12]->SamplerState = _shaderSets[0]->SamplerState;

    // 乗算も通常と同じシェーダーを利用する
    _shaderSets[13]->ShaderProgram = _shaderSets[1]->ShaderProgram;
    _shaderSets[14]->ShaderProgram = _shaderSets[2]->ShaderProgram;
    _shaderSets[15]->ShaderProgram = _shaderSets[3]->ShaderProgram;
    _shaderSets[16]->ShaderProgram = _shaderSets[4]->ShaderProgram;
    _shaderSets[17]->ShaderProgram = _shaderSets[5]->ShaderProgram;
    _shaderSets[18]->ShaderProgram = _shaderSets[6]->ShaderProgram;

    _shaderSets[13]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[13]->ShaderProgram, CubismRenderer::CubismBlendMode_Multiplicative);
    _shaderSets[14]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[14]->ShaderProgram, CubismRenderer::CubismBlendMode_Multiplicative);
    _shaderSets[15]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[15]->ShaderProgram, CubismRenderer::CubismBlendMode_Multiplicative);
    _shaderSets[16]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[16]->ShaderProgram, CubismRenderer::CubismBlendMode_Multiplicative);
    _shaderSets[17]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[17]->ShaderProgram, CubismRenderer::CubismBlendMode_Multiplicative);
    _shaderSets[18]->RenderPipelineState = MakeRenderPipelineState(device, _shaderSets[18]->ShaderProgram, CubismRenderer::CubismBlendMode_Multiplicative);

    _shaderSets[13]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[14]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[15]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[16]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[17]->DepthStencilState = _shaderSets[1]->DepthStencilState;
    _shaderSets[18]->DepthStencilState = _shaderSets[1]->DepthStencilState;

    _shaderSets[13]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[14]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[15]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[16]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[17]->SamplerState = _shaderSets[0]->SamplerState;
    _shaderSets[18]->SamplerState = _shaderSets[0]->SamplerState;
}

void CubismShader_Metal::SetupShaderProgram(CubismCommandBuffer_Metal::DrawCommandBuffer* drawCommandBuffer, CubismRenderer_Metal* renderer, id <MTLTexture> texture
                                                , csmFloat32 opacity
                                                , CubismRenderer::CubismBlendMode colorBlendMode
                                                , CubismRenderer::CubismTextureColor baseColor
                                                , CubismRenderer::CubismTextureColor multiplyColor
                                                , CubismRenderer::CubismTextureColor screenColor
                                                , csmBool isPremultipliedAlpha, CubismMatrix44 matrix4x4
                                                , csmBool invertedMask
                                                , id <MTLRenderCommandEncoder> renderEncoder)
{
    if (_shaderSets.GetSize() == 0)
    {
        GenerateShaders(renderer);
    }

    CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand* drawCommand = drawCommandBuffer->GetCommandDraw();

    if (renderer->GetClippingContextBufferForMask() != NULL) // マスク生成時
    {
        CubismShaderSet* shaderSet = _shaderSets[ShaderNames_SetupMask];

        //テクスチャ設定
        [renderEncoder setFragmentTexture:texture atIndex:0];

        [renderEncoder setVertexBuffer:(drawCommandBuffer->GetVertexBuffer()) offset:0 atIndex:MetalVertexInputIndexVertices];
        [renderEncoder setVertexBuffer:(drawCommandBuffer->GetUvBuffer()) offset:0 atIndex:MetalVertexInputUVs];

        CubismSetupMaskedShaderUniforms maskedShaderUniforms;
        const csmInt32 channelNo = renderer->GetClippingContextBufferForMask()->_layoutChannelNo;
        CubismRenderer::CubismTextureColor* colorChannel = renderer->GetClippingContextBufferForMask()->GetClippingManager()->GetChannelFlagAsColor(channelNo);
        maskedShaderUniforms.channelFlag = (vector_float4){ colorChannel->R, colorChannel->G, colorChannel->B, colorChannel->A };

        csmFloat32* srcArray = renderer->GetClippingContextBufferForMask()->_matrixForMask.GetArray();

        maskedShaderUniforms.clipMatrix = simd::float4x4(
                            simd::float4 {srcArray[0], srcArray[1], srcArray[2], srcArray[3]},
                            simd::float4 {srcArray[4], srcArray[5], srcArray[6], srcArray[7]},
                            simd::float4 {srcArray[8], srcArray[9], srcArray[10], srcArray[11]},
                            simd::float4 {srcArray[12], srcArray[13], srcArray[14], srcArray[15]});

        csmRectF* rect = renderer->GetClippingContextBufferForMask()->_layoutBounds;

        maskedShaderUniforms.baseColor = (vector_float4){ rect->X * 2.0f - 1.0f,
                                    rect->Y * 2.0f - 1.0f,
                                    rect->GetRight() * 2.0f - 1.0f,
                                    rect->GetBottom() * 2.0f - 1.0f };

        // 転送
        [renderEncoder setVertexBytes:&maskedShaderUniforms length:sizeof(CubismSetupMaskedShaderUniforms) atIndex:MetalVertexInputIndexUniforms];
        [renderEncoder setFragmentBytes:&maskedShaderUniforms length:sizeof(CubismSetupMaskedShaderUniforms) atIndex:MetalVertexInputIndexUniforms];
        [renderEncoder setFragmentSamplerState:shaderSet->SamplerState atIndex:0];
        drawCommand->SetRenderPipelineState(shaderSet->RenderPipelineState);
    }
    else // マスク生成以外の場合
    {
        const csmBool masked = renderer->GetClippingContextBufferForDraw() != NULL;  // この描画オブジェクトはマスク対象か
        const csmInt32 offset = (masked ? ( invertedMask ? 2 : 1 ) : 0) + (isPremultipliedAlpha ? 3 : 0);

        CubismShaderSet* shaderSet;

        switch (colorBlendMode)
        {
        case CubismRenderer::CubismBlendMode_Normal:
        default:
            //_shaderSetsにshaderが入っていて、それをブレンドモードごとに切り替えている
            shaderSet = _shaderSets[ShaderNames_Normal + offset];
            break;

        case CubismRenderer::CubismBlendMode_Additive:
            shaderSet = _shaderSets[ShaderNames_Add + offset];
            break;

        case CubismRenderer::CubismBlendMode_Multiplicative:
            shaderSet = _shaderSets[ShaderNames_Mult + offset];
            break;
        }

        // 頂点配列の設定
        [renderEncoder setVertexBuffer:(drawCommandBuffer->GetVertexBuffer()) offset:0 atIndex:MetalVertexInputIndexVertices];

        // テクスチャ頂点の設定
        [renderEncoder setVertexBuffer:(drawCommandBuffer->GetUvBuffer()) offset:0 atIndex:MetalVertexInputUVs];

        if (masked)
        {
            CubismMaskedShaderUniforms maskedShaderUniforms;
            CubismFragMaskedShaderUniforms fragMaskedShaderUniforms;

            // frameBufferに書かれたテクスチャ
            id <MTLTexture> tex = renderer->_offscreenFrameBuffer.GetColorBuffer();

            //テクスチャ設定
            [renderEncoder setFragmentTexture:tex atIndex:1];

            // 使用するカラーチャンネルを設定
            const csmInt32 channelNo = renderer->GetClippingContextBufferForDraw()->_layoutChannelNo;
            CubismRenderer::CubismTextureColor* colorChannel = renderer->GetClippingContextBufferForDraw()->GetClippingManager()->GetChannelFlagAsColor(channelNo);

            fragMaskedShaderUniforms.channelFlag = (vector_float4){ colorChannel->R, colorChannel->G, colorChannel->B, colorChannel->A };
            {
                csmFloat32* srcArray = renderer->GetClippingContextBufferForDraw()->_matrixForDraw.GetArray();

                maskedShaderUniforms.clipMatrix = simd::float4x4(
                                    simd::float4 {srcArray[0], srcArray[1], srcArray[2], srcArray[3]},
                                    simd::float4 {srcArray[4], srcArray[5], srcArray[6], srcArray[7]},
                                    simd::float4 {srcArray[8], srcArray[9], srcArray[10], srcArray[11]},
                                    simd::float4 {srcArray[12], srcArray[13], srcArray[14], srcArray[15]});
            }
            {
                //座標変換
                csmFloat32* srcArray = matrix4x4.GetArray();
                maskedShaderUniforms.matrix = simd::float4x4(
                                                             simd::float4 {srcArray[0], srcArray[1], srcArray[2], srcArray[3]},
                                                             simd::float4 {srcArray[4], srcArray[5], srcArray[6], srcArray[7]},
                                                             simd::float4 {srcArray[8], srcArray[9], srcArray[10], srcArray[11]},
                                                             simd::float4 {srcArray[12], srcArray[13], srcArray[14], srcArray[15]});
            }

            //テクスチャ設定
            [renderEncoder setFragmentTexture:texture atIndex:0];

            maskedShaderUniforms.baseColor = (vector_float4){ baseColor.R, baseColor.G, baseColor.B, baseColor.A };
            fragMaskedShaderUniforms.baseColor = (vector_float4){ baseColor.R, baseColor.G, baseColor.B, baseColor.A };
            fragMaskedShaderUniforms.multiplyColor = (vector_float4){ multiplyColor.R, multiplyColor.G, multiplyColor.B, multiplyColor.A };
            fragMaskedShaderUniforms.screenColor = (vector_float4){ screenColor.R, screenColor.G, screenColor.B, screenColor.A };

            // 転送
            [renderEncoder setVertexBytes:&maskedShaderUniforms length:sizeof(CubismMaskedShaderUniforms) atIndex:MetalVertexInputIndexUniforms];
            [renderEncoder setFragmentBytes:&fragMaskedShaderUniforms length:sizeof(CubismFragMaskedShaderUniforms) atIndex:MetalVertexInputIndexUniforms];
            [renderEncoder setFragmentSamplerState:shaderSet->SamplerState atIndex:0];

        } else {
            CubismNormalShaderUniforms normalShaderUniforms;

            //テクスチャ設定
            [renderEncoder setFragmentTexture:texture atIndex:0];

            //座標変換
            csmFloat32* srcArray = matrix4x4.GetArray();
            normalShaderUniforms.matrix = simd::float4x4(
                                                         simd::float4 {srcArray[0], srcArray[1], srcArray[2], srcArray[3]},
                                                         simd::float4 {srcArray[4], srcArray[5], srcArray[6], srcArray[7]},
                                                         simd::float4 {srcArray[8], srcArray[9], srcArray[10], srcArray[11]},
                                                         simd::float4 {srcArray[12], srcArray[13], srcArray[14], srcArray[15]});
            normalShaderUniforms.baseColor = (vector_float4){ baseColor.R, baseColor.G, baseColor.B, baseColor.A };
            normalShaderUniforms.multiplyColor = (vector_float4){ multiplyColor.R, multiplyColor.G, multiplyColor.B, multiplyColor.A };
            normalShaderUniforms.screenColor = (vector_float4){ screenColor.R, screenColor.G, screenColor.B, screenColor.A };

            // 転送
            [renderEncoder setVertexBytes:&normalShaderUniforms length:sizeof(CubismNormalShaderUniforms) atIndex:MetalVertexInputIndexUniforms];
            [renderEncoder setFragmentBytes:&normalShaderUniforms length:sizeof(CubismNormalShaderUniforms) atIndex:MetalVertexInputIndexUniforms];
            [renderEncoder setFragmentSamplerState:shaderSet->SamplerState atIndex:0];
        }

        [renderEncoder setDepthStencilState:shaderSet->DepthStencilState];
        drawCommand->SetRenderPipelineState(shaderSet->RenderPipelineState);
    }
}

CubismShader_Metal::ShaderProgram* CubismShader_Metal::LoadShaderProgram(const csmChar* vertShaderSrc, const csmChar* fragShaderSrc)
{
    // Create shader program.
    ShaderProgram *shaderProgram = new ShaderProgram;

    //頂点シェーダの取得
    NSString *vertShaderStr = [NSString stringWithCString: vertShaderSrc encoding:NSUTF8StringEncoding];

    shaderProgram->vertexFunction = [_shaderLib newFunctionWithName:vertShaderStr];
    if(!shaderProgram->vertexFunction)
    {
        delete shaderProgram;
        NSLog(@">> ERROR: Couldn't load vertex function from default library");
        return nil;
    }

    //フラグメントシェーダの取得
    NSString *fragShaderStr = [NSString stringWithCString: fragShaderSrc encoding:NSUTF8StringEncoding];
    shaderProgram->fragmentFunction = [_shaderLib newFunctionWithName:fragShaderStr];
    if(!shaderProgram->fragmentFunction)
    {
        delete shaderProgram;
        NSLog(@" ERROR: Couldn't load fragment function from default library");
        return nil;
    }

    return shaderProgram;
}

id<MTLRenderPipelineState> CubismShader_Metal::MakeRenderPipelineState(id<MTLDevice> device, CubismShader_Metal::ShaderProgram* shaderProgram, int blendMode)
{
    MTLRenderPipelineDescriptor* renderPipelineDescriptor = [MTLRenderPipelineDescriptor new];
    NSError *error;

    renderPipelineDescriptor.vertexFunction = shaderProgram->vertexFunction;
    renderPipelineDescriptor.fragmentFunction = shaderProgram->fragmentFunction;
    renderPipelineDescriptor.colorAttachments[0].blendingEnabled = true;

    switch (blendMode)
    {
    default:
            // only Setup masking
            renderPipelineDescriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorZero;
            renderPipelineDescriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceColor;
            renderPipelineDescriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorZero;
            renderPipelineDescriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
            renderPipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatRGBA8Unorm;
            break;

    case CubismRenderer::CubismBlendMode_Normal:
            renderPipelineDescriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorOne;
            renderPipelineDescriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
            renderPipelineDescriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
            renderPipelineDescriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
            renderPipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
            renderPipelineDescriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;

        break;

    case CubismRenderer::CubismBlendMode_Additive:
            renderPipelineDescriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorOne;
            renderPipelineDescriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOne;
            renderPipelineDescriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorZero;
            renderPipelineDescriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOne;
            renderPipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
            renderPipelineDescriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;

        break;

    case CubismRenderer::CubismBlendMode_Multiplicative:
            renderPipelineDescriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorDestinationColor;
            renderPipelineDescriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
            renderPipelineDescriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorZero;
            renderPipelineDescriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOne;
            renderPipelineDescriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
            renderPipelineDescriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;

        break;
    }

    return [device newRenderPipelineStateWithDescriptor:renderPipelineDescriptor error:&error];
}

id<MTLDepthStencilState> CubismShader_Metal::MakeDepthStencilState(id<MTLDevice> device)
{
    MTLDepthStencilDescriptor* depthStencilDescriptor = [MTLDepthStencilDescriptor new];

    depthStencilDescriptor.depthCompareFunction = MTLCompareFunctionAlways;
    depthStencilDescriptor.depthWriteEnabled = YES;

    return [device newDepthStencilStateWithDescriptor:depthStencilDescriptor];
}

id<MTLSamplerState> CubismShader_Metal::MakeSamplerState(id<MTLDevice> device, CubismRenderer_Metal* renderer)
{
    MTLSamplerDescriptor* samplerDescriptor = [MTLSamplerDescriptor new];

    samplerDescriptor.rAddressMode = MTLSamplerAddressModeRepeat;
    samplerDescriptor.sAddressMode = MTLSamplerAddressModeRepeat;
    samplerDescriptor.tAddressMode = MTLSamplerAddressModeRepeat;
    samplerDescriptor.minFilter = MTLSamplerMinMagFilterLinear;
    samplerDescriptor.magFilter = MTLSamplerMinMagFilterLinear;
    samplerDescriptor.mipFilter = MTLSamplerMipFilterNotMipmapped;

    //異方性フィルタリング
    if (renderer->GetAnisotropy() > 0.0f)
    {
        samplerDescriptor.maxAnisotropy = renderer->GetAnisotropy();
    }

    return [device newSamplerStateWithDescriptor:samplerDescriptor];
}

/*********************************************************************************************************************
 *                                      CubismRenderer_Metal
 ********************************************************************************************************************/

id<MTLCommandBuffer> CubismRenderer_Metal::s_commandBuffer = nil;
id<MTLDevice> CubismRenderer_Metal::s_device = nil;
MTLRenderPassDescriptor* CubismRenderer_Metal::s_renderPassDescriptor = nil;

CubismRenderer* CubismRenderer::Create()
{
    return CSM_NEW CubismRenderer_Metal();
}

void CubismRenderer::StaticRelease()
{
    CubismRenderer_Metal::DoStaticRelease();
}

CubismRenderer_Metal::CubismRenderer_Metal() : _clippingManager(NULL)
                                                     , _clippingContextBufferForMask(NULL)
                                                     , _clippingContextBufferForDraw(NULL)
{
    // テクスチャ対応マップの容量を確保しておく.
    _textures.PrepareCapacity(32, true);
}

CubismRenderer_Metal::~CubismRenderer_Metal()
{
    CSM_DELETE_SELF(CubismClippingManager_Metal, _clippingManager);

    for (csmInt32 i = 0; i < _drawableDrawCommandBuffer.GetSize(); ++i)
    {
        CSM_DELETE(_drawableDrawCommandBuffer[i]);
    }
}

void CubismRenderer_Metal::DoStaticRelease()
{
    s_commandBuffer = nil;
    CubismShader_Metal::DeleteInstance();
}

void CubismRenderer_Metal::Initialize(CubismModel* model)
{

    CubismRenderingInstanceSingleton_Metal *single = [CubismRenderingInstanceSingleton_Metal sharedManager];
    id <MTLDevice> device = [single getMTLDevice];
    CAMetalLayer* metalLayer = [single getMetalLayer];

    if (model->IsUsingMasking())
    {
        _clippingManager = CSM_NEW CubismClippingManager_Metal();  //クリッピングマスク・バッファ前処理方式を初期化
        _clippingManager->Initialize(
            *model,
            model->GetDrawableCount(),
            model->GetDrawableMasks(),
            model->GetDrawableMaskCounts()
        );

        _offscreenFrameBuffer.CreateOffscreenFrame(_clippingManager->GetClippingMaskBufferSize().X, _clippingManager->GetClippingMaskBufferSize().Y);
    }

    _sortedDrawableIndexList.Resize(model->GetDrawableCount(), 0);

    _drawableDrawCommandBuffer.Resize(model->GetDrawableCount());

    for (csmInt32 i = 0; i < _drawableDrawCommandBuffer.GetSize(); ++i)
    {
        const csmInt32 drawableVertexCount = model->GetDrawableVertexCount(i);
        const csmInt32 drawableVertexIndexCount = model->GetDrawableVertexIndexCount(i);
        const csmSizeInt vertexSize = sizeof(csmFloat32) * 2;

        _drawableDrawCommandBuffer[i] = CSM_NEW CubismCommandBuffer_Metal::DrawCommandBuffer();

        // ここで頂点情報のメモリを確保する
        _drawableDrawCommandBuffer[i]->CreateVertexBuffer(device, vertexSize, drawableVertexCount);

        if (drawableVertexIndexCount > 0)
        {
            _drawableDrawCommandBuffer[i]->CreateIndexBuffer(device, drawableVertexIndexCount);
        }
    }

    CubismRenderer::Initialize(model);  //親クラスの処理を呼ぶ
}

void CubismRenderer_Metal::StartFrame(id<MTLDevice> device, id<MTLCommandBuffer> commandBuffer, MTLRenderPassDescriptor* renderPassDescriptor)
{
    s_commandBuffer = commandBuffer;
    s_device = device;
    s_renderPassDescriptor = renderPassDescriptor;
}

id <MTLRenderCommandEncoder> CubismRenderer_Metal::PreDraw(id <MTLCommandBuffer> commandBuffer, MTLRenderPassDescriptor* drawableRenderDescriptor)
{
    id <MTLRenderCommandEncoder> renderEncoder = [commandBuffer renderCommandEncoderWithDescriptor:drawableRenderDescriptor];
    return renderEncoder;
}

void CubismRenderer_Metal::PostDraw(id <MTLRenderCommandEncoder> renderEncoder)
{
    [renderEncoder endEncoding];
}

void CubismRenderer_Metal::DoDrawModel()
{
    //------------ クリッピングマスク・バッファ前処理方式の場合 ------------
    if (_clippingManager != NULL)
    {
        // サイズが違う場合はここで作成しなおし
        if (_offscreenFrameBuffer.GetBufferWidth() != _clippingManager->GetClippingMaskBufferSize().X ||
            _offscreenFrameBuffer.GetBufferHeight() != _clippingManager->GetClippingMaskBufferSize().Y)
        {
            _offscreenFrameBuffer.DestroyOffscreenFrame();
            _offscreenFrameBuffer.CreateOffscreenFrame(
                _clippingManager->GetClippingMaskBufferSize().X, _clippingManager->GetClippingMaskBufferSize().Y);
        }

        _clippingManager->SetupClippingContext(*GetModel(), this, _rendererProfile._lastColorBuffer, _rendererProfile._lastViewport);
    }

    id <MTLRenderCommandEncoder> renderEncoder = nil;

    if(!IsUsingHighPrecisionMask())
    {
        renderEncoder = PreDraw(s_commandBuffer, s_renderPassDescriptor);
    }

    const csmInt32 drawableCount = GetModel()->GetDrawableCount();
    const csmInt32* renderOrder = GetModel()->GetDrawableRenderOrders();

    // インデックスを描画順でソート
    for (csmInt32 i = 0; i < drawableCount; ++i)
    {
        const csmInt32 order = renderOrder[i];
        _sortedDrawableIndexList[order] = i;
    }

    // Update Vertex / Index buffer.
    for (csmInt32 i = 0; i < drawableCount; ++i)
    {
        csmFloat32* vertices = const_cast<csmFloat32*>(GetModel()->GetDrawableVertices(i));
        Core::csmVector2* uvs = const_cast<Core::csmVector2*>(GetModel()->GetDrawableVertexUvs(i));
        csmUint16* vertexIndices = const_cast<csmUint16*>(GetModel()->GetDrawableVertexIndices(i));
        const csmUint32 vertexCount = GetModel()->GetDrawableVertexCount(i);
        const csmUint32 vertexIndexCount = GetModel()->GetDrawableVertexIndexCount(i);

        _drawableDrawCommandBuffer[i]->UpdateVertexBuffer(vertices, uvs, vertexCount);
        if (vertexIndexCount > 0)
        {
            _drawableDrawCommandBuffer[i]->UpdateIndexBuffer(vertexIndices, vertexIndexCount);
        }
    }

    // 描画
    for (csmInt32 i = 0; i < drawableCount; ++i)
    {
        const csmInt32 drawableIndex = _sortedDrawableIndexList[i];

        // Drawableが表示状態でなければ処理をパスする
        if (!GetModel()->GetDrawableDynamicFlagIsVisible(drawableIndex))
        {
            continue;
        }

        // クリッピングマスク
        CubismClippingContext* clipContext = (_clippingManager != NULL)
            ? (*_clippingManager->GetClippingContextListForDraw())[drawableIndex]
            : NULL;

        if (clipContext != NULL && IsUsingHighPrecisionMask()) // マスクを書く必要がある
        {
            // 生成したFrameBufferと同じサイズでビューポートを設定
            MTLViewport clipVp = {0, 0, _clippingManager->GetClippingMaskBufferSize().X, _clippingManager->GetClippingMaskBufferSize().Y, 0.0, 1.0};
            if(clipContext->_isUsing) // 書くことになっていた
            {
                renderEncoder = PreDraw(s_commandBuffer, _offscreenFrameBuffer.GetRenderPassDescriptor());
                [renderEncoder setViewport:clipVp];
            }

            {
                const csmInt32 clipDrawCount = clipContext->_clippingIdCount;
                for (csmInt32 index = 0; index < clipDrawCount; index++)
                {
                    const csmInt32 clipDrawIndex = clipContext->_clippingIdList[index];
                    CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand* drawCommandMask = clipContext->_clippingCommandBufferList->At(index)->GetCommandDraw();

                    // 頂点情報が更新されておらず、信頼性がない場合は描画をパスする
                    if (!GetModel()->GetDrawableDynamicFlagVertexPositionsDidChange(clipDrawIndex))
                    {
                        continue;
                    }

                    IsCulling(GetModel()->GetDrawableCulling(clipDrawIndex) != 0);

                    // Update Vertex / Index buffer.
                    {
                        csmFloat32* vertices = const_cast<csmFloat32*>(GetModel()->GetDrawableVertices(clipDrawIndex));
                        Core::csmVector2* uvs = const_cast<Core::csmVector2*>(GetModel()->GetDrawableVertexUvs(clipDrawIndex));
                        csmUint16* vertexIndices = const_cast<csmUint16*>(GetModel()->GetDrawableVertexIndices(clipDrawIndex));
                        const csmUint32 vertexCount = GetModel()->GetDrawableVertexCount(clipDrawIndex);
                        const csmUint32 vertexIndexCount = GetModel()->GetDrawableVertexIndexCount(clipDrawIndex);

                        CubismCommandBuffer_Metal::DrawCommandBuffer* drawCommandBufferMask = clipContext->_clippingCommandBufferList->At(index);
                        drawCommandBufferMask->UpdateVertexBuffer(vertices, uvs, vertexCount);
                        if (vertexIndexCount > 0)
                        {
                            drawCommandBufferMask->UpdateIndexBuffer(vertexIndices, vertexIndexCount);
                        }

                        if (vertexCount <= 0)
                        {
                            continue;
                        }
                    }

                    // 今回専用の変換を適用して描く
                    // チャンネルも切り替える必要がある(A,R,G,B)
                    SetClippingContextBufferForMask(clipContext);
                    DrawMeshMetal(
                        clipContext->_clippingCommandBufferList->At(index),
                        GetModel()->GetDrawableTextureIndex(clipDrawIndex),
                        GetModel()->GetDrawableVertexIndexCount(clipDrawIndex),
                        GetModel()->GetDrawableVertexCount(clipDrawIndex),
                        const_cast<csmUint16*>(GetModel()->GetDrawableVertexIndices(clipDrawIndex)),
                        const_cast<csmFloat32*>(GetModel()->GetDrawableVertices(clipDrawIndex)),
                        reinterpret_cast<csmFloat32*>(const_cast<Core::csmVector2*>(GetModel()->GetDrawableVertexUvs(clipDrawIndex))),
                        GetModel()->GetMultiplyColor(clipDrawIndex),
                        GetModel()->GetScreenColor(clipDrawIndex),
                        GetModel()->GetDrawableOpacity(clipDrawIndex),
                        CubismRenderer::CubismBlendMode_Normal,   //クリッピングは通常描画を強制
                        false, // マスク生成時はクリッピングの反転使用は全く関係がない
                        clipDrawIndex,
                        renderEncoder
                    );
                }
            }

            {
                // --- 後処理 ---
                PostDraw(renderEncoder);
            }
        }

        CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand* drawCommandDraw = _drawableDrawCommandBuffer[drawableIndex]->GetCommandDraw();
        _drawableDrawCommandBuffer[drawableIndex]->SetCommandBuffer(s_commandBuffer);

        // クリッピングマスクをセットする
        SetClippingContextBufferForDraw(clipContext);

        IsCulling(GetModel()->GetDrawableCulling(drawableIndex) != 0);

        if (GetModel()->GetDrawableVertexIndexCount(drawableIndex) <= 0)
        {
            continue;
        }

        if(IsUsingHighPrecisionMask())
        {
            renderEncoder = PreDraw(s_commandBuffer, s_renderPassDescriptor);
        }

        DrawMeshMetal(
            _drawableDrawCommandBuffer[drawableIndex],
            GetModel()->GetDrawableTextureIndex(drawableIndex),
            GetModel()->GetDrawableVertexIndexCount(drawableIndex),
            GetModel()->GetDrawableVertexCount(drawableIndex),
            const_cast<csmUint16*>(GetModel()->GetDrawableVertexIndices(drawableIndex)),
            const_cast<csmFloat32*>(GetModel()->GetDrawableVertices(drawableIndex)),
            reinterpret_cast<csmFloat32*>(const_cast<Core::csmVector2*>(GetModel()->GetDrawableVertexUvs(drawableIndex))),
            GetModel()->GetMultiplyColor(drawableIndex),
            GetModel()->GetScreenColor(drawableIndex),
            GetModel()->GetDrawableOpacity(drawableIndex),
            GetModel()->GetDrawableBlendMode(drawableIndex),
            GetModel()->GetDrawableInvertedMask(drawableIndex), // マスクを反転使用するか
            drawableIndex,
            renderEncoder
        );
        if(IsUsingHighPrecisionMask())
        {
            PostDraw(renderEncoder);
        }
    }

    if(!IsUsingHighPrecisionMask())
    {
        PostDraw(renderEncoder);
    }
}

void CubismRenderer_Metal::DrawMesh(csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount,
    csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray, csmFloat32 opacity,
    CubismBlendMode colorBlendMode, csmBool invertedMask)
{
}

void CubismRenderer_Metal::DrawMeshMetal(CubismCommandBuffer_Metal::DrawCommandBuffer* drawCommandBuffer, csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
                                        , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
                                        , const CubismTextureColor& multiplyColor, const CubismTextureColor& screenColor
                                        , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask, csmInt32 drawableIndex
                                        , id <MTLRenderCommandEncoder> renderEncoder)
{
#ifndef CSM_DEBUG
    if (_textures[textureNo] == 0) return;    // モデルが参照するテクスチャがバインドされていない場合は描画をスキップする
#endif

    // 裏面描画の有効・無効
    if (IsCulling())
    {
        [renderEncoder setCullMode:MTLCullModeBack];
    }
    else
    {
        [renderEncoder setCullMode:MTLCullModeNone];
    }

    CubismTextureColor modelColorRGBA = GetModelColor();

    if (GetClippingContextBufferForMask() == NULL) // マスク生成時以外
    {
        modelColorRGBA.A *= opacity;
        if (IsPremultipliedAlpha())
        {
            modelColorRGBA.R *= modelColorRGBA.A;
            modelColorRGBA.G *= modelColorRGBA.A;
            modelColorRGBA.B *= modelColorRGBA.A;
        }
    }

    id <MTLTexture> drawTexture;   // シェーダに渡すテクスチャ

    // テクスチャマップからバインド済みテクスチャIDを取得
    // バインドされていなければダミーのテクスチャIDをセットする
    if(_textures[textureNo] != NULL)
    {
        drawTexture = _textures[textureNo];
    }
    else
    {
        drawTexture = NULL;
    }

    CubismShader_Metal::GetInstance()->SetupShaderProgram(
        drawCommandBuffer, this, drawTexture
        , opacity, colorBlendMode, modelColorRGBA, multiplyColor, screenColor, IsPremultipliedAlpha()
        , GetMvpMatrix(), invertedMask, renderEncoder
    );

    CubismCommandBuffer_Metal::DrawCommandBuffer::DrawCommand* drawCommand =  drawCommandBuffer->GetCommandDraw();

    id <MTLRenderPipelineState> pipelineState = drawCommand->GetRenderPipelineState();

    // パイプライン状態オブジェクトを設定する
    [renderEncoder setRenderPipelineState:pipelineState];

    id <MTLBuffer> indexBuffer = drawCommandBuffer->GetIndexBuffer();
    [renderEncoder drawIndexedPrimitives:MTLPrimitiveTypeTriangle indexCount:indexCount indexType:MTLIndexTypeUInt16
                             indexBuffer:indexBuffer indexBufferOffset:0];
    // 後処理
    SetClippingContextBufferForDraw(NULL);
    SetClippingContextBufferForMask(NULL);
}

CubismCommandBuffer_Metal::DrawCommandBuffer* CubismRenderer_Metal::GetDrawCommandBufferData(csmInt32 drawableIndex)
{
    return _drawableDrawCommandBuffer[drawableIndex];
}

void CubismRenderer_Metal::SaveProfile()
{
}

void CubismRenderer_Metal::RestoreProfile()
{
}

void CubismRenderer_Metal::BindTexture(csmUint32 modelTextureNo, id <MTLTexture> texture)
{
    _textures[modelTextureNo] = texture;
}

const csmMap<csmInt32, id <MTLTexture>>& CubismRenderer_Metal::GetBindedTextures() const
{
    return _textures;
}

void CubismRenderer_Metal::SetClippingMaskBufferSize(csmFloat32 width, csmFloat32 height)
{
    //FrameBufferのサイズを変更するためにインスタンスを破棄・再作成する
    CSM_DELETE_SELF(CubismClippingManager_Metal, _clippingManager);

    _clippingManager = CSM_NEW CubismClippingManager_Metal();

    _clippingManager->SetClippingMaskBufferSize(width, height);

    _clippingManager->Initialize(
        *GetModel(),
        GetModel()->GetDrawableCount(),
        GetModel()->GetDrawableMasks(),
        GetModel()->GetDrawableMaskCounts()
    );
}

CubismVector2 CubismRenderer_Metal::GetClippingMaskBufferSize() const
{
    return _clippingManager->GetClippingMaskBufferSize();
}

void CubismRenderer_Metal::SetClippingContextBufferForMask(CubismClippingContext* clip)
{
    _clippingContextBufferForMask = clip;
}

CubismClippingContext* CubismRenderer_Metal::GetClippingContextBufferForMask() const
{
    return _clippingContextBufferForMask;
}

void CubismRenderer_Metal::SetClippingContextBufferForDraw(CubismClippingContext* clip)
{
    _clippingContextBufferForDraw = clip;
}

CubismClippingContext* CubismRenderer_Metal::GetClippingContextBufferForDraw() const
{
    return _clippingContextBufferForDraw;
}

}}}}

//------------ LIVE2D NAMESPACE ------------
