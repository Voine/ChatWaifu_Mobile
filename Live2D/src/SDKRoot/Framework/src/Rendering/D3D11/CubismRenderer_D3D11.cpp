/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismRenderer_D3D11.hpp"

#include <cfloat> // FLT_MAX,MIN

#include "Math/CubismMatrix44.hpp"
#include "Type/csmVector.hpp"
#include "Model/CubismModel.hpp"
#include "CubismShader_D3D11.hpp"
#include "CubismRenderState_D3D11.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

DirectX::XMMATRIX ConvertToD3DX(CubismMatrix44& mtx)
{
    DirectX::XMMATRIX retMtx;
    retMtx.r[0].m128_f32[0] = mtx.GetArray()[ 0];
    retMtx.r[0].m128_f32[1] = mtx.GetArray()[ 1];
    retMtx.r[0].m128_f32[2] = mtx.GetArray()[ 2];
    retMtx.r[0].m128_f32[3] = mtx.GetArray()[ 3];

    retMtx.r[1].m128_f32[0] = mtx.GetArray()[ 4];
    retMtx.r[1].m128_f32[1] = mtx.GetArray()[ 5];
    retMtx.r[1].m128_f32[2] = mtx.GetArray()[ 6];
    retMtx.r[1].m128_f32[3] = mtx.GetArray()[ 7];

    retMtx.r[2].m128_f32[0] = mtx.GetArray()[ 8];
    retMtx.r[2].m128_f32[1] = mtx.GetArray()[ 9];
    retMtx.r[2].m128_f32[2] = mtx.GetArray()[10];
    retMtx.r[2].m128_f32[3] = mtx.GetArray()[11];

    retMtx.r[3].m128_f32[0] = mtx.GetArray()[12];
    retMtx.r[3].m128_f32[1] = mtx.GetArray()[13];
    retMtx.r[3].m128_f32[2] = mtx.GetArray()[14];
    retMtx.r[3].m128_f32[3] = mtx.GetArray()[15];

    return retMtx;
}

/*********************************************************************************************************************
*                                      CubismClippingManager_D3D11
********************************************************************************************************************/
///< ファイルスコープの変数宣言
namespace {
const csmInt32 ColorChannelCount = 4;   ///< 実験時に1チャンネルの場合は1、RGBだけの場合は3、アルファも含める場合は4
}

CubismClippingManager_D3D11::CubismClippingManager_D3D11()
    : _colorBuffer(NULL)
    , _currentFrameNo(0)
    , _clippingMaskBufferSize(256)
{
    CubismRenderer::CubismTextureColor* tmp = NULL;
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

CubismClippingManager_D3D11::~CubismClippingManager_D3D11()
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

void CubismClippingManager_D3D11::Initialize(CubismModel& model, csmInt32 drawableCount, const csmInt32** drawableMasks, const csmInt32* drawableMaskCounts)
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
            cc = CSM_NEW CubismClippingContext(this, drawableMasks[i], drawableMaskCounts[i]);
            _clippingContextListForMask.PushBack(cc);
        }

        cc->AddClippedDrawable(i);

        _clippingContextListForDraw.PushBack(cc);
    }
}

CubismClippingContext* CubismClippingManager_D3D11::FindSameClip(const csmInt32* drawableMasks, csmInt32 drawableMaskCounts) const
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

void CubismClippingManager_D3D11::SetupClippingContext(ID3D11DeviceContext* renderContext, CubismModel& model, CubismRenderer_D3D11* renderer, CubismOffscreenFrame_D3D11& useTarget)
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
        if (!renderer->IsUsingHighPrecisionMask())
        {
            // ビューポートは退避済み
            // 生成したFrameBufferと同じサイズでビューポートを設定
            CubismRenderer_D3D11::GetRenderStateManager()->SetViewport(renderContext,
                0,
                0,
                static_cast<FLOAT>(_clippingMaskBufferSize),
                static_cast<FLOAT>(_clippingMaskBufferSize),
                0.0f, 1.0f);

            useTarget.BeginDraw(renderContext);
            // 1が無効（描かれない）領域、0が有効（描かれる）領域。（シェーダで Cd*Csで0に近い値をかけてマスクを作る。1をかけると何も起こらない）
            useTarget.Clear(renderContext, 1.0f, 1.0f, 1.0f, 1.0f);
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

            // モデル座標上の矩形を、適宜マージンを付けて使う
            const csmFloat32 MARGIN = 0.05f;
            _tmpBoundsOnModel.SetRect(allClippedDrawRect);
            _tmpBoundsOnModel.Expand(allClippedDrawRect->Width * MARGIN, allClippedDrawRect->Height * MARGIN);
            //########## 本来は割り当てられた領域の全体を使わず必要最低限のサイズがよい

            // シェーダ用の計算式を求める。回転を考慮しない場合は以下のとおり
            // movePeriod' = movePeriod * scaleX + offX [[ movePeriod' = (movePeriod - tmpBoundsOnModel.movePeriod)*scale + layoutBoundsOnTex01.movePeriod ]]
            const csmFloat32 scaleX = layoutBoundsOnTex01->Width / _tmpBoundsOnModel.Width;
            const csmFloat32 scaleY = layoutBoundsOnTex01->Height / _tmpBoundsOnModel.Height;

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
                    // 上下反転
                    _tmpMatrix.ScaleRelative(scaleX, scaleY * -1.0f ); //new = [translate][scale]
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

                    // 頂点情報が更新されておらず、信頼性がない場合は描画をパスする
                    if (!model.GetDrawableDynamicFlagVertexPositionsDidChange(clipDrawIndex))
                    {
                        continue;
                    }

                    renderer->IsCulling(model.GetDrawableCulling(clipDrawIndex) != 0);

                    // 今回専用の変換を適用して描く
                    // チャンネルも切り替える必要がある(A,R,G,B)
                    renderer->SetClippingContextBufferForMask(clipContext);
                    renderer->DrawMeshDX11(clipDrawIndex,
                        model.GetDrawableTextureIndices(clipDrawIndex),
                        model.GetDrawableVertexIndexCount(clipDrawIndex),
                        model.GetDrawableVertexCount(clipDrawIndex),
                        const_cast<csmUint16*>(model.GetDrawableVertexIndices(clipDrawIndex)),
                        const_cast<csmFloat32*>(model.GetDrawableVertices(clipDrawIndex)),
                        reinterpret_cast<csmFloat32*>(const_cast<Core::csmVector2*>(model.GetDrawableVertexUvs(clipDrawIndex))),
                        model.GetDrawableOpacity(clipDrawIndex),
                        CubismRenderer::CubismBlendMode::CubismBlendMode_Normal, //クリッピングは通常描画を強制
                        false   // マスク生成時はクリッピングの反転使用は全く関係がない
                    );
                }
            }
            else
            {
                // NOP このモードの際はチャンネルを分けず、マトリクスの計算だけをしておいて描画自体は本体描画直前で行う
            }
        }

        if (!renderer->IsUsingHighPrecisionMask())
        {
            useTarget.EndDraw(renderContext);

            renderer->SetClippingContextBufferForMask(NULL);
        }
    }
}

void CubismClippingManager_D3D11::CalcClippedDrawTotalBounds(CubismModel& model, CubismClippingContext* clippingContext)
{
    // 被クリッピングマスク（マスクされる描画オブジェクト）の全体の矩形
    csmFloat32 clippedDrawTotalMinX = FLT_MAX, clippedDrawTotalMinY = FLT_MAX;
    csmFloat32 clippedDrawTotalMaxX = FLT_MIN, clippedDrawTotalMaxY = FLT_MIN;

    // このマスクが実際に必要か判定する
    // このクリッピングを利用する「描画オブジェクト」がひとつでも使用可能であればマスクを生成する必要がある

    const csmInt32 clippedDrawCount = clippingContext->_clippedDrawableIndexList->GetSize();
    for (csmInt32 clippedDrawableIndex = 0; clippedDrawableIndex < clippedDrawCount; clippedDrawableIndex++)
    {
        // マスクを使用する描画オブジェクトの描画される矩形を求める
        const csmInt32 drawableIndex = (*clippingContext->_clippedDrawableIndexList)[clippedDrawableIndex];

        const csmInt32 drawableVertexCount = model.GetDrawableVertexCount(drawableIndex);
        const csmFloat32* drawableVertexes = const_cast<csmFloat32*>(model.GetDrawableVertices(drawableIndex));

        csmFloat32 minX = FLT_MAX, minY = FLT_MAX;
        csmFloat32 maxX = FLT_MIN, maxY = FLT_MIN;

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

void CubismClippingManager_D3D11::SetupLayoutBounds(csmInt32 usingClipCount) const
{
    if(usingClipCount<=0)
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
    csmInt32 curClipIndex = 0; //順番に設定していくk

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

CubismRenderer::CubismTextureColor* CubismClippingManager_D3D11::GetChannelFlagAsColor(csmInt32 channelNo)
{
    return _channelColors[channelNo];
}

CubismOffscreenFrame_D3D11* CubismClippingManager_D3D11::GetColorBuffer() const
{
    return _colorBuffer;
}

csmVector<CubismClippingContext*>* CubismClippingManager_D3D11::GetClippingContextListForDraw()
{
    return &_clippingContextListForDraw;
}

void CubismClippingManager_D3D11::SetClippingMaskBufferSize(csmInt32 size)
{
    _clippingMaskBufferSize = size;
}

csmInt32 CubismClippingManager_D3D11::GetClippingMaskBufferSize() const
{
    return _clippingMaskBufferSize;
}

/*********************************************************************************************************************
*                                      CubismClippingContext
********************************************************************************************************************/
CubismClippingContext::CubismClippingContext(CubismClippingManager_D3D11* manager, const csmInt32* clippingDrawableIndices, csmInt32 clipCount)
{
    _isUsing = false;

    _owner = manager;

    // クリップしている（＝マスク用の）Drawableのインデックスリスト
    _clippingIdList = clippingDrawableIndices;

    // マスクの数
    _clippingIdCount = clipCount;

    _layoutChannelNo = 0;

    _allClippedDrawRect = CSM_NEW csmRectF();
    _layoutBounds = CSM_NEW csmRectF();

    _clippedDrawableIndexList = CSM_NEW csmVector<csmInt32>();
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
}

void CubismClippingContext::AddClippedDrawable(csmInt32 drawableIndex)
{
    _clippedDrawableIndexList->PushBack(drawableIndex);
}

CubismClippingManager_D3D11* CubismClippingContext::GetClippingManager()
{
    return _owner;
}


/*********************************************************************************************************************
 *                                      CubismRenderer_D3D11
 ********************************************************************************************************************/

// 各種静的変数
namespace
{
    CubismRenderState_D3D11* s_renderStateManager = NULL;   ///< レンダーステートの管理
    CubismShader_D3D11* s_shaderManagerInstance = NULL;     ///< シェーダー管理

    csmUint32 s_bufferSetNum = 0;           ///< 作成コンテキストの数。モデルロード前に設定されている必要あり。
    ID3D11Device* s_device = NULL;          ///< 使用デバイス。モデルロード前に設定されている必要あり。
    ID3D11DeviceContext* s_context = NULL;  ///< 使用描画コンテキスト

    csmUint32 s_viewportWidth = 0;          ///< 描画ターゲット幅 CubismRenderer_D3D11::startframeで渡される
    csmUint32 s_viewportHeight = 0;         ///< 描画ターゲット高さ CubismRenderer_D3D11::startframeで渡される
}

CubismRenderer* CubismRenderer::Create()
{
    return CSM_NEW CubismRenderer_D3D11();
}

void CubismRenderer::StaticRelease()
{
    CubismRenderer_D3D11::DoStaticRelease();
}

CubismRenderState_D3D11* CubismRenderer_D3D11::GetRenderStateManager()
{
    if (s_renderStateManager == NULL)
    {
        s_renderStateManager = CSM_NEW CubismRenderState_D3D11();
    }
    return s_renderStateManager;
}

void CubismRenderer_D3D11::DeleteRenderStateManager()
{
    if (s_renderStateManager)
    {
        CSM_DELETE_SELF(CubismRenderState_D3D11, s_renderStateManager);
        s_renderStateManager = NULL;
    }
}

CubismShader_D3D11* CubismRenderer_D3D11::GetShaderManager()
{
    if (s_shaderManagerInstance == NULL)
    {
        s_shaderManagerInstance = CSM_NEW CubismShader_D3D11();
    }
    return s_shaderManagerInstance;
}

void CubismRenderer_D3D11::DeleteShaderManager()
{
    if (s_shaderManagerInstance)
    {
        CSM_DELETE_SELF(CubismShader_D3D11, s_shaderManagerInstance);
        s_shaderManagerInstance = NULL;
    }
}

void CubismRenderer_D3D11::GenerateShader(ID3D11Device* device)
{
    CubismShader_D3D11* shaderManager = GetShaderManager();
    if(shaderManager)
    {
        shaderManager->GenerateShaders(device);
    }
}

ID3D11Device* CubismRenderer_D3D11::GetCurrentDevice()
{
    return s_device;
}

void CubismRenderer_D3D11::OnDeviceLost()
{
    // シェーダー・頂点宣言開放
    ReleaseShader();
}

void CubismRenderer_D3D11::ReleaseShader()
{
    CubismShader_D3D11* shaderManager = GetShaderManager();
    if (shaderManager)
    {
        shaderManager->ReleaseShaderProgram();
    }
}

CubismRenderer_D3D11::CubismRenderer_D3D11()
    : _vertexBuffers(NULL)
    , _indexBuffers(NULL)
    , _constantBuffers(NULL)
    , _drawableNum(0)
    , _clippingManager(NULL)
    , _clippingContextBufferForMask(NULL)
    , _clippingContextBufferForDraw(NULL)
{
    _commandBufferNum = 0;
    _commandBufferCurrent = 0;

    // テクスチャ対応マップの容量を確保しておく.
    _textures.PrepareCapacity(32, true);
}

CubismRenderer_D3D11::~CubismRenderer_D3D11()
{
    {
        // オフスクリーンを作成していたのなら開放
        for (csmUint32 i = 0; i < _offscreenFrameBuffer.GetSize(); i++)
        {
            _offscreenFrameBuffer[i].DestroyOffscreenFrame();
        }
        _offscreenFrameBuffer.Clear();
    }

    const csmInt32 drawableCount = _drawableNum; //GetModel()->GetDrawableCount();

    for (csmInt32 buffer = 0; buffer < _commandBufferNum; buffer++)
    {
        for (csmInt32 drawAssign = 0; drawAssign < drawableCount; drawAssign++)
        {
            if(_constantBuffers[buffer][drawAssign])
            {
                _constantBuffers[buffer][drawAssign]->Release();
                _constantBuffers[buffer][drawAssign] = NULL;
            }
            // インデックス
            if(_indexBuffers[buffer][drawAssign])
            {
                _indexBuffers[buffer][drawAssign]->Release();
                _indexBuffers[buffer][drawAssign] = NULL;
            }
            // 頂点
            if(_vertexBuffers[buffer][drawAssign])
            {
                _vertexBuffers[buffer][drawAssign]->Release();
                _vertexBuffers[buffer][drawAssign] = NULL;
            }
        }

        CSM_FREE(_constantBuffers[buffer]);
        CSM_FREE(_indexBuffers[buffer]);
        CSM_FREE(_vertexBuffers[buffer]);
    }

    //
    CSM_FREE(_constantBuffers);
    CSM_FREE(_indexBuffers);
    CSM_FREE(_vertexBuffers);

    CSM_DELETE_SELF(CubismClippingManager_D3D11, _clippingManager);
}

void CubismRenderer_D3D11::DoStaticRelease()
{
    // レンダーステートマネージャ削除
    DeleteRenderStateManager();
    // シェーダマネージャ削除
    DeleteShaderManager();
}


void CubismRenderer_D3D11::Initialize(CubismModel* model)
{
    // 0は許されず ここに来るまでに設定しなければならない
    if (s_bufferSetNum == 0)
    {
        CubismLogError("ContextNum has not been set.");
        CSM_ASSERT(0);
        return;
    }
    if (s_device == 0)
    {
        CubismLogError("Device has not been set.");
        CSM_ASSERT(0);
        return;
    }

    if (model->IsUsingMasking())
    {
        _clippingManager = CSM_NEW CubismClippingManager_D3D11();  //クリッピングマスク・バッファ前処理方式を初期化
        _clippingManager->Initialize(
            *model,
            model->GetDrawableCount(),
            model->GetDrawableMasks(),
            model->GetDrawableMaskCounts()
        );
    }

    _sortedDrawableIndexList.Resize(model->GetDrawableCount(), 0);

    CubismRenderer::Initialize(model);  //親クラスの処理を呼ぶ

// コマンドバッファごとに確保
  // 頂点バッファをコンテキスト分
    _vertexBuffers = static_cast<ID3D11Buffer***>(CSM_MALLOC(sizeof(ID3D11Buffer**) * s_bufferSetNum));
    _indexBuffers = static_cast<ID3D11Buffer***>(CSM_MALLOC(sizeof(ID3D11Buffer**) * s_bufferSetNum));
    _constantBuffers = static_cast<ID3D11Buffer***>(CSM_MALLOC(sizeof(ID3D11Buffer**) * s_bufferSetNum));

    // モデルパーツごとに確保
    const csmInt32 drawableCount = GetModel()->GetDrawableCount();
    _drawableNum = drawableCount;

    for (csmUint32 buffer = 0; buffer < s_bufferSetNum; buffer++)
    {
        // 頂点バッファ
        _vertexBuffers[buffer] = static_cast<ID3D11Buffer**>(CSM_MALLOC(sizeof(ID3D11Buffer*) * drawableCount));
        _indexBuffers[buffer] = static_cast<ID3D11Buffer**>(CSM_MALLOC(sizeof(ID3D11Buffer*) * drawableCount));
        _constantBuffers[buffer] = static_cast<ID3D11Buffer**>(CSM_MALLOC(sizeof(ID3D11Buffer*) * drawableCount));

        for (csmInt32 drawAssign = 0; drawAssign < drawableCount; drawAssign++)
        {
            // 頂点
            const csmInt32 vcount = GetModel()->GetDrawableVertexCount(drawAssign);
            if (vcount != 0)
            {
                D3D11_BUFFER_DESC bufferDesc;
                memset(&bufferDesc, 0, sizeof(bufferDesc));
                bufferDesc.ByteWidth = sizeof(CubismVertexD3D11) * vcount;    // 総長 構造体サイズ*個数
                bufferDesc.Usage = D3D11_USAGE_DYNAMIC;
                bufferDesc.BindFlags = D3D11_BIND_VERTEX_BUFFER;
                bufferDesc.CPUAccessFlags = D3D11_CPU_ACCESS_WRITE;
                bufferDesc.MiscFlags = 0;
                bufferDesc.StructureByteStride = 0;

                // 後で頂点を入れるので領域だけ
                if (FAILED(s_device->CreateBuffer(&bufferDesc, NULL, &_vertexBuffers[buffer][drawAssign])))
                {
                    CubismLogError("Vertexbuffer create failed : %d", vcount);
                }
            }
            else
            {
                _vertexBuffers[buffer][drawAssign] = NULL;
            }

            // インデックスはここで要素コピーを済ませる
            _indexBuffers[buffer][drawAssign] = NULL;
            const csmInt32 icount = GetModel()->GetDrawableVertexIndexCount(drawAssign);
            if (icount != 0)
            {
                D3D11_BUFFER_DESC bufferDesc;
                memset(&bufferDesc, 0, sizeof(bufferDesc));
                bufferDesc.ByteWidth = sizeof(WORD) * icount;    // 総長 構造体サイズ*個数
                bufferDesc.Usage = D3D11_USAGE_DEFAULT;
                bufferDesc.BindFlags = D3D11_BIND_INDEX_BUFFER;
                bufferDesc.CPUAccessFlags = 0;
                bufferDesc.MiscFlags = 0;
                bufferDesc.StructureByteStride = 0;

                D3D11_SUBRESOURCE_DATA subResourceData;
                memset(&subResourceData, 0, sizeof(subResourceData));
                subResourceData.pSysMem = GetModel()->GetDrawableVertexIndices(drawAssign);
                subResourceData.SysMemPitch = 0;
                subResourceData.SysMemSlicePitch = 0;

                if (FAILED(s_device->CreateBuffer(&bufferDesc, &subResourceData, &_indexBuffers[buffer][drawAssign])))
                {
                    CubismLogError("Indexbuffer create failed : %d", icount);
                }
            }

            _constantBuffers[buffer][drawAssign] = NULL;
            {
                D3D11_BUFFER_DESC bufferDesc;
                memset(&bufferDesc, 0, sizeof(bufferDesc));
                bufferDesc.ByteWidth = sizeof(CubismConstantBufferD3D11);    // 総長 構造体サイズ*個数
                bufferDesc.Usage = D3D11_USAGE_DEFAULT; // 定数バッファに関しては「Map用にDynamic」にしなくともよい
                bufferDesc.BindFlags = D3D11_BIND_CONSTANT_BUFFER;
                bufferDesc.CPUAccessFlags = 0;
                bufferDesc.MiscFlags = 0;
                bufferDesc.StructureByteStride = 0;

                if (FAILED(s_device->CreateBuffer(&bufferDesc, NULL, &_constantBuffers[buffer][drawAssign])))
                {
                    CubismLogError("ConstantBuffers create failed");
                }
            }
        }
    }

    _commandBufferNum = s_bufferSetNum;
    _commandBufferCurrent = 0;

    if (model->IsUsingMasking())
    {
        const csmInt32 bufferHeight = _clippingManager->GetClippingMaskBufferSize();

        // バックバッファ分確保
        for (csmUint32 i = 0; i < s_bufferSetNum; i++)
        {
            CubismOffscreenFrame_D3D11 push;
            _offscreenFrameBuffer.PushBack(push);
        }
        // オフスクリーン
        for (csmUint32 i = 0; i < s_bufferSetNum; i++)
        {
            _offscreenFrameBuffer[i].CreateOffscreenFrame(
                s_device,
                bufferHeight, bufferHeight);
        }
    }
}

void CubismRenderer_D3D11::PreDraw()
{
    SetDefaultRenderState();
}

void CubismRenderer_D3D11::PostDraw()
{
    _commandBufferCurrent++;
    if (_commandBufferNum <= _commandBufferCurrent)
    {
        _commandBufferCurrent = 0;
    }
}

void CubismRenderer_D3D11::DoDrawModel()
{
    // NULLは許されず
    CSM_ASSERT(s_device != NULL);
    CSM_ASSERT(s_context != NULL);

    PreDraw();

    //------------ クリッピングマスク・バッファ前処理方式の場合 ------------
    if (_clippingManager != NULL)
    {
        _clippingManager->_colorBuffer = &_offscreenFrameBuffer[_commandBufferCurrent];

        // サイズが違う場合はここで作成しなおし
        if (_clippingManager->_colorBuffer->GetBufferWidth() != static_cast<csmUint32>(_clippingManager->GetClippingMaskBufferSize()) ||
            _clippingManager->_colorBuffer->GetBufferHeight() != static_cast<csmUint32>(_clippingManager->GetClippingMaskBufferSize()))
        {
            _clippingManager->_colorBuffer->DestroyOffscreenFrame();
            _clippingManager->_colorBuffer->CreateOffscreenFrame(s_device,
                static_cast<csmUint32>(_clippingManager->GetClippingMaskBufferSize()), static_cast<csmUint32>(_clippingManager->GetClippingMaskBufferSize()));
        }

        _clippingManager->SetupClippingContext(s_context, *GetModel(), this, *_clippingManager->_colorBuffer);

        if (!IsUsingHighPrecisionMask())
        {
            // ビューポートを元に戻す
            GetRenderStateManager()->SetViewport(s_context,
                0.0f,
                0.0f,
                static_cast<float>(s_viewportWidth),
                static_cast<float>(s_viewportHeight),
                0.0f, 1.0f);
        }
    }

    const csmInt32 drawableCount = GetModel()->GetDrawableCount();
    const csmInt32* renderOrder = GetModel()->GetDrawableRenderOrders();

    // インデックスを描画順でソート
    for (csmInt32 i = 0; i < drawableCount; ++i)
    {
        const csmInt32 order = renderOrder[i];
        _sortedDrawableIndexList[order] = i;
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

        // クリッピングマスクをセットする
        CubismClippingContext* clipContext = (_clippingManager != NULL)
            ? (*_clippingManager->GetClippingContextListForDraw())[drawableIndex]
            : NULL;

        if (clipContext != NULL && IsUsingHighPrecisionMask()) // マスクを書く必要がある
        {
            if (clipContext->_isUsing) // 書くことになっていた
            {
                CubismRenderer_D3D11::GetRenderStateManager()->SetViewport(s_context,
                    0,
                    0,
                    static_cast<FLOAT>(_clippingManager->GetClippingMaskBufferSize()),
                    static_cast<FLOAT>(_clippingManager->GetClippingMaskBufferSize()),
                    0.0f, 1.0f);

                _clippingManager->_colorBuffer->BeginDraw(s_context);
                // 1が無効（描かれない）領域、0が有効（描かれる）領域。（シェーダで Cd*Csで0に近い値をかけてマスクを作る。1をかけると何も起こらない）
                _clippingManager->_colorBuffer->Clear(s_context, 1.0f, 1.0f, 1.0f, 1.0f);

                const csmInt32 clipDrawCount = clipContext->_clippingIdCount;
                for (csmInt32 ctx = 0; ctx < clipDrawCount; ctx++)
                {
                    const csmInt32 clipDrawIndex = clipContext->_clippingIdList[ctx];

                    // 頂点情報が更新されておらず、信頼性がない場合は描画をパスする
                    if (!GetModel()->GetDrawableDynamicFlagVertexPositionsDidChange(clipDrawIndex))
                    {
                        continue;
                    }

                    IsCulling(GetModel()->GetDrawableCulling(clipDrawIndex) != 0);

                    // 今回専用の変換を適用して描く
                    // チャンネルも切り替える必要がある(A,R,G,B)
                    SetClippingContextBufferForMask(clipContext);
                    DrawMeshDX11(clipDrawIndex,
                        GetModel()->GetDrawableTextureIndices(clipDrawIndex),
                        GetModel()->GetDrawableVertexIndexCount(clipDrawIndex),
                        GetModel()->GetDrawableVertexCount(clipDrawIndex),
                        const_cast<csmUint16*>(GetModel()->GetDrawableVertexIndices(clipDrawIndex)),
                        const_cast<csmFloat32*>(GetModel()->GetDrawableVertices(clipDrawIndex)),
                        reinterpret_cast<csmFloat32*>(const_cast<Core::csmVector2*>(GetModel()->GetDrawableVertexUvs(clipDrawIndex))),
                        GetModel()->GetDrawableOpacity(clipDrawIndex),
                        CubismRenderer::CubismBlendMode::CubismBlendMode_Normal, //クリッピングは通常描画を強制
                        false   // マスク生成時はクリッピングの反転使用は全く関係がない
                    );
                }

                _clippingManager->_colorBuffer->EndDraw(s_context);
                SetClippingContextBufferForMask(NULL);

                // ビューポートを元に戻す
                GetRenderStateManager()->SetViewport(s_context,
                    0.0f,
                    0.0f,
                    static_cast<float>(s_viewportWidth),
                    static_cast<float>(s_viewportHeight),
                    0.0f, 1.0f);
            }
        }

        // クリッピングマスクをセットする
        SetClippingContextBufferForDraw(clipContext);

        IsCulling(GetModel()->GetDrawableCulling(drawableIndex) != 0);

        DrawMeshDX11(drawableIndex,
            GetModel()->GetDrawableTextureIndices(drawableIndex),
            GetModel()->GetDrawableVertexIndexCount(drawableIndex),
            GetModel()->GetDrawableVertexCount(drawableIndex),
            const_cast<csmUint16*>(GetModel()->GetDrawableVertexIndices(drawableIndex)),
            const_cast<csmFloat32*>(GetModel()->GetDrawableVertices(drawableIndex)),
            reinterpret_cast<csmFloat32*>(const_cast<Core::csmVector2*>(GetModel()->GetDrawableVertexUvs(drawableIndex))),
            GetModel()->GetDrawableOpacity(drawableIndex),
            GetModel()->GetDrawableBlendMode(drawableIndex),
            GetModel()->GetDrawableInvertedMask(drawableIndex)   // マスクを反転使用するか
        );
    }

    //
    PostDraw();
}

void CubismRenderer_D3D11::ExecuteDraw(ID3D11Device* device, ID3D11DeviceContext* renderContext,
    ID3D11Buffer* vertexBuffer, ID3D11Buffer* indexBuffer, ID3D11Buffer* constantBuffer,
    const csmInt32 indexCount,
    const csmInt32 textureNo, CubismTextureColor& modelColorRGBA, CubismBlendMode colorBlendMode, csmBool invertedMask)
{
    // 使用シェーダエフェクト取得
    CubismShader_D3D11* shaderManager = Live2D::Cubism::Framework::Rendering::CubismRenderer_D3D11::GetShaderManager();
    if(!shaderManager)
    {
        return;
    }

    // テクスチャセット
    ID3D11ShaderResourceView* textureView = NULL;
    if (textureNo >= 0)
    {
        textureView = _textures[textureNo];
    }

    if (textureView == NULL)
    {
        return;    // モデルが参照するテクスチャがバインドされていない場合は描画をスキップする
    }

    CubismConstantBufferD3D11 cb;
    memset(&cb, 0, sizeof(cb));

    if (GetClippingContextBufferForMask() != NULL) // マスク生成時
    {
        renderContext->VSSetShader(shaderManager->GetVertexShader(ShaderNames_SetupMask), NULL, 0);
        renderContext->PSSetShader(shaderManager->GetPixelShader(ShaderNames_SetupMask), NULL, 0);

        // チャンネル
        const csmInt32 channelNo = GetClippingContextBufferForMask()->_layoutChannelNo;
        // チャンネルをRGBAに変換
        CubismTextureColor* colorChannel = GetClippingContextBufferForMask()->GetClippingManager()->GetChannelFlagAsColor(channelNo);

        // マスク用ブレンドステート
        GetRenderStateManager()->SetBlend(renderContext,
            CubismRenderState_D3D11::Blend_Mask,
            DirectX::XMFLOAT4(0, 0, 0, 0),
            0xffffffff);

        // 定数バッファ
        {
            csmRectF* rect = GetClippingContextBufferForMask()->_layoutBounds;

            DirectX::XMMATRIX proj = ConvertToD3DX(GetClippingContextBufferForMask()->_matrixForMask);
            XMStoreFloat4x4(&cb.projectMatrix, DirectX::XMMatrixTranspose(proj));
            XMStoreFloat4(&cb.baseColor, DirectX::XMVectorSet(rect->X * 2.0f - 1.0f, rect->Y * 2.0f - 1.0f, rect->GetRight() * 2.0f - 1.0f, rect->GetBottom() * 2.0f - 1.0f));
            XMStoreFloat4(&cb.channelFlag, DirectX::XMVectorSet(colorChannel->R, colorChannel->G, colorChannel->B, colorChannel->A));

            // Update
            renderContext->UpdateSubresource(constantBuffer, 0, NULL, &cb, 0, 0);

            // セットする
            renderContext->VSSetConstantBuffers(0, 1, &constantBuffer);
            renderContext->PSSetConstantBuffers(0, 1, &constantBuffer);
        }

        // テクスチャ
        {
            ID3D11ShaderResourceView* const viewArray[2] = { textureView, NULL };

            renderContext->PSSetShaderResources(0, 2, viewArray);
            if (GetAnisotropy() > 0.0)
            {
                GetRenderStateManager()->SetSampler(renderContext, CubismRenderState_D3D11::Sampler_Anisotropy, GetAnisotropy());
            }
            else
            {
                GetRenderStateManager()->SetSampler(renderContext, CubismRenderState_D3D11::Sampler_Normal);
            }
        }

        // トライアングルリスト
        renderContext->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);

        // 描画
        {
            UINT strides = sizeof(Csm::CubismVertexD3D11);
            UINT offsets = 0;
            renderContext->IASetVertexBuffers(0, 1, &vertexBuffer, &strides, &offsets);
            renderContext->IASetIndexBuffer(indexBuffer, DXGI_FORMAT_R16_UINT, 0);
            renderContext->DrawIndexed(indexCount, 0, 0);

        }
    }
    else // マスク生成以外の場合
    {
        const csmBool masked = GetClippingContextBufferForDraw() != NULL;  // この描画オブジェクトはマスク対象か
        const csmBool premult = IsPremultipliedAlpha();
        const csmInt32 offset = (masked ? (invertedMask ? 2 : 1) : 0) + (IsPremultipliedAlpha() ? 3 : 0);

        // ブレンドステート
        switch (colorBlendMode)
        {
        case CubismRenderer::CubismBlendMode::CubismBlendMode_Normal:
        default:
            GetRenderStateManager()->SetBlend(renderContext,
                CubismRenderState_D3D11::Blend_Normal,
                DirectX::XMFLOAT4(0,0,0,0),
                0xffffffff);
            break;

        case CubismRenderer::CubismBlendMode::CubismBlendMode_Additive:
            GetRenderStateManager()->SetBlend(renderContext,
                CubismRenderState_D3D11::Blend_Add,
                DirectX::XMFLOAT4(0, 0, 0, 0),
                0xffffffff);
            break;

        case CubismRenderer::CubismBlendMode::CubismBlendMode_Multiplicative:
            GetRenderStateManager()->SetBlend(renderContext,
                CubismRenderState_D3D11::Blend_Mult,
                DirectX::XMFLOAT4(0, 0, 0, 0),
                0xffffffff);
            break;
        }

        {
            // シェーダセット
            if (masked)
            {
                if(premult)
                {
                    if (invertedMask)
                    {
                        renderContext->VSSetShader(shaderManager->GetVertexShader(ShaderNames_NormalMasked), NULL, 0);
                        renderContext->PSSetShader(shaderManager->GetPixelShader(ShaderNames_NormalMaskedInvertedPremultipliedAlpha), NULL, 0);
                    }
                    else
                    {
                        renderContext->VSSetShader(shaderManager->GetVertexShader(ShaderNames_NormalMasked), NULL, 0);
                        renderContext->PSSetShader(shaderManager->GetPixelShader(ShaderNames_NormalMaskedPremultipliedAlpha), NULL, 0);
                    }
                }
                else
                {
                    if (invertedMask)
                    {
                        renderContext->VSSetShader(shaderManager->GetVertexShader(ShaderNames_NormalMasked), NULL, 0);
                        renderContext->PSSetShader(shaderManager->GetPixelShader(ShaderNames_NormalMaskedInverted), NULL, 0);
                    }
                    else
                    {
                        renderContext->VSSetShader(shaderManager->GetVertexShader(ShaderNames_NormalMasked), NULL, 0);
                        renderContext->PSSetShader(shaderManager->GetPixelShader(ShaderNames_NormalMasked), NULL, 0);
                    }
                }
            }
            else
            {
                if(premult)
                {
                    renderContext->VSSetShader(shaderManager->GetVertexShader(ShaderNames_Normal), NULL, 0);
                    renderContext->PSSetShader(shaderManager->GetPixelShader(ShaderNames_NormalPremultipliedAlpha), NULL, 0);
                }
                else
                {
                    renderContext->VSSetShader(shaderManager->GetVertexShader(ShaderNames_Normal), NULL, 0);
                    renderContext->PSSetShader(shaderManager->GetPixelShader(ShaderNames_Normal), NULL, 0);
                }
            }

            // テクスチャ+サンプラーセット
            if (!masked)
            {
                ID3D11ShaderResourceView* const viewArray[2] = { textureView, NULL };
                renderContext->PSSetShaderResources(0, 2, viewArray);
            }
            else
            {
                ID3D11ShaderResourceView* const viewArray[2] ={textureView, _clippingManager->_colorBuffer->GetTextureView() };
                renderContext->PSSetShaderResources(0, 2, viewArray);
            }

            if (GetAnisotropy() > 0.0)
            {
                GetRenderStateManager()->SetSampler(renderContext, CubismRenderState_D3D11::Sampler_Anisotropy, GetAnisotropy());
            }
            else
            {
                GetRenderStateManager()->SetSampler(renderContext, CubismRenderState_D3D11::Sampler_Normal);
            }

            // 定数バッファ
            {
                if (masked)
                {
                    // View座標をClippingContextの座標に変換するための行列を設定

                    DirectX::XMMATRIX clip = ConvertToD3DX(GetClippingContextBufferForDraw()->_matrixForDraw);
                    XMStoreFloat4x4(&cb.clipMatrix, DirectX::XMMatrixTranspose(clip));

                    // 使用するカラーチャンネルを設定
                    const csmInt32 channelNo = GetClippingContextBufferForDraw()->_layoutChannelNo;
                    CubismRenderer::CubismTextureColor* colorChannel = GetClippingContextBufferForDraw()->GetClippingManager()->GetChannelFlagAsColor(channelNo);
                    XMStoreFloat4(&cb.channelFlag, DirectX::XMVectorSet(colorChannel->R, colorChannel->G, colorChannel->B, colorChannel->A));
                }

                // プロジェクションMtx
                CubismMatrix44 mvp = GetMvpMatrix();
                DirectX::XMMATRIX proj = ConvertToD3DX(mvp);
                XMStoreFloat4x4(&cb.projectMatrix, DirectX::XMMatrixTranspose(proj));
                // 色
                XMStoreFloat4(&cb.baseColor, DirectX::XMVectorSet(modelColorRGBA.R, modelColorRGBA.G, modelColorRGBA.B, modelColorRGBA.A));

                // Update
                renderContext->UpdateSubresource(constantBuffer, 0, NULL, &cb, 0, 0);

                renderContext->VSSetConstantBuffers(0, 1, &constantBuffer);
                renderContext->PSSetConstantBuffers(0, 1, &constantBuffer);
            }

            // トライアングルリスト
            renderContext->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);

            // 描画
            {
                UINT strides = sizeof(Csm::CubismVertexD3D11);
                UINT offsets = 0;
                renderContext->IASetVertexBuffers(0, 1, &vertexBuffer, &strides, &offsets);
                renderContext->IASetIndexBuffer(indexBuffer, DXGI_FORMAT_R16_UINT, 0);
                renderContext->DrawIndexed(indexCount, 0, 0);
            }
        }
    }
}

void CubismRenderer_D3D11::DrawMesh(csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
    , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
    , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask)
{
    CubismLogWarning("Use 'DrawMeshDX11' function");
    CSM_ASSERT(0);
}

void CubismRenderer_D3D11::DrawMeshDX11( csmInt32 drawableIndex
    , csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
    , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
    , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask)
{
    if (s_device == NULL)
    {// デバイス未設定
        return;
    }
    if(indexCount==0)
    {// 描画物無し
        return;
    }
    // 描画不要なら描画処理をスキップする
    if (opacity <= 0.0f && GetClippingContextBufferForMask() == NULL)
    {
        return;
    }

    // テクスチャセット
    ID3D11ShaderResourceView* textureView = NULL;
    if (textureNo >= 0)
    {
        textureView = _textures[textureNo];
    }

    if (textureView == NULL) return;    // モデルが参照するテクスチャがバインドされていない場合は描画をスキップする


    // 裏面描画の有効・無効
    if (IsCulling())
    {
        GetRenderStateManager()->SetCullMode(s_context, CubismRenderState_D3D11::Cull_Ccw); // CWを消す
    }
    else
    {
        GetRenderStateManager()->SetCullMode(s_context, CubismRenderState_D3D11::Cull_None); // カリング無し
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

    // 頂点バッファにコピー
    CopyToBuffer(s_context, drawableIndex, vertexCount, vertexArray, uvArray);

    // シェーダーセット
    ExecuteDraw(s_device, s_context,
        _vertexBuffers[_commandBufferCurrent][drawableIndex], _indexBuffers[_commandBufferCurrent][drawableIndex], _constantBuffers[_commandBufferCurrent][drawableIndex],
        indexCount,
        textureNo, modelColorRGBA, colorBlendMode, invertedMask);

    SetClippingContextBufferForDraw(NULL);
    SetClippingContextBufferForMask(NULL);
}

void CubismRenderer_D3D11::SaveProfile()
{
    // NULLは許されず
    CSM_ASSERT(s_device != NULL);
    CSM_ASSERT(s_context != NULL);

    // 現在のレンダリングステートをPush
    GetRenderStateManager()->SaveCurrentNativeState(s_device, s_context);
}

void CubismRenderer_D3D11::RestoreProfile()
{
    // NULLは許されず
    CSM_ASSERT(s_device != NULL);
    CSM_ASSERT(s_context != NULL);

    // SaveCurrentNativeStateと対
    GetRenderStateManager()->RestoreNativeState(s_device, s_context);
}

void CubismRenderer_D3D11::BindTexture(csmUint32 modelTextureAssign, ID3D11ShaderResourceView* textureView)
{
    _textures[modelTextureAssign] = textureView;
}

const csmMap<csmInt32, ID3D11ShaderResourceView*>& CubismRenderer_D3D11::GetBindedTextures() const
{
    return _textures;
}

void CubismRenderer_D3D11::SetClippingMaskBufferSize(csmInt32 size)
{
    //FrameBufferのサイズを変更するためにインスタンスを破棄・再作成する
    CSM_DELETE_SELF(CubismClippingManager_D3D11, _clippingManager);

    _clippingManager = CSM_NEW CubismClippingManager_D3D11();

    _clippingManager->SetClippingMaskBufferSize(size);

    _clippingManager->Initialize(
        *GetModel(),
        GetModel()->GetDrawableCount(),
        GetModel()->GetDrawableMasks(),
        GetModel()->GetDrawableMaskCounts()
    );
}

csmInt32 CubismRenderer_D3D11::GetClippingMaskBufferSize() const
{
    return _clippingManager->GetClippingMaskBufferSize();
}

void CubismRenderer_D3D11::InitializeConstantSettings(csmUint32 bufferSetNum, ID3D11Device* device)
{
    s_bufferSetNum = bufferSetNum;
    s_device = device;

    // 実体を作成しておく
    CubismRenderer_D3D11::GetRenderStateManager();
}

void CubismRenderer_D3D11::SetDefaultRenderState()
{
    // Zは無効 描画順で制御
    GetRenderStateManager()->SetZEnable(s_context,
        CubismRenderState_D3D11::Depth_Disable,
        0);

    // ビューポート
    GetRenderStateManager()->SetViewport(s_context,
        0.0f,
        0.0f,
        static_cast<float>(s_viewportWidth),
        static_cast<float>(s_viewportHeight),
        0.0f, 1.0f);
}

void CubismRenderer_D3D11::StartFrame(ID3D11Device* device, ID3D11DeviceContext* renderContext, csmUint32 viewportWidth, csmUint32 viewportHeight)
{
    // フレームで使用するデバイス設定
    s_device = device;
    s_context = renderContext;
    s_viewportWidth = viewportWidth;
    s_viewportHeight = viewportHeight;

    // レンダーステートフレーム先頭処理
    GetRenderStateManager()->StartFrame();

    // シェーダ・頂点宣言
    GetShaderManager()->SetupShader(s_device, s_context);
}

void CubismRenderer_D3D11::EndFrame(ID3D11Device* device)
{
}

void CubismRenderer_D3D11::SetClippingContextBufferForDraw(CubismClippingContext* clip)
{
    _clippingContextBufferForDraw = clip;
}

CubismClippingContext* CubismRenderer_D3D11::GetClippingContextBufferForDraw() const
{
    return _clippingContextBufferForDraw;
}

void CubismRenderer_D3D11::SetClippingContextBufferForMask(CubismClippingContext* clip)
{
    _clippingContextBufferForMask = clip;
}

CubismClippingContext* CubismRenderer_D3D11::GetClippingContextBufferForMask() const
{
    return _clippingContextBufferForMask;
}

void CubismRenderer_D3D11::CopyToBuffer(ID3D11DeviceContext* renderContext, csmInt32 drawAssign, const csmInt32 vcount, const csmFloat32* varray, const csmFloat32* uvarray)
{
    // CubismVertexD3D11の書き込み
    if (_vertexBuffers[_commandBufferCurrent][drawAssign])
    {
        D3D11_MAPPED_SUBRESOURCE subRes;
        if (SUCCEEDED(renderContext->Map(_vertexBuffers[_commandBufferCurrent][drawAssign], 0, D3D11_MAP_WRITE_DISCARD, 0, &subRes)))
        {
            CubismVertexD3D11* lockPointer = reinterpret_cast<CubismVertexD3D11*>(subRes.pData);
            for (csmInt32 ct = 0; ct < vcount * 2; ct += 2)
            {// モデルデータからのコピー
                lockPointer[ct / 2].x = varray[ct + 0];
                lockPointer[ct / 2].y = varray[ct + 1];

                lockPointer[ct / 2].u = uvarray[ct + 0];
                lockPointer[ct / 2].v = uvarray[ct + 1];
            }
            renderContext->Unmap(_vertexBuffers[_commandBufferCurrent][drawAssign], 0);
        }
    }
}

}}}}

//------------ LIVE2D NAMESPACE ------------
