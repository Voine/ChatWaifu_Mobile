/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismRenderer_D3D9.hpp"

#include <cfloat> // FLT_MAX,MIN

#include "Math/CubismMatrix44.hpp"
#include "Type/csmVector.hpp"
#include "Model/CubismModel.hpp"
#include "CubismShader_D3D9.hpp"
#include "CubismRenderState_D3D9.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

D3DXMATRIX ConvertToD3DX(CubismMatrix44& mtx)
{
    D3DXMATRIX retMtx;
    retMtx._11 = mtx.GetArray()[ 0];
    retMtx._12 = mtx.GetArray()[ 1];
    retMtx._13 = mtx.GetArray()[ 2];
    retMtx._14 = mtx.GetArray()[ 3];

    retMtx._21 = mtx.GetArray()[ 4];
    retMtx._22 = mtx.GetArray()[ 5];
    retMtx._23 = mtx.GetArray()[ 6];
    retMtx._24 = mtx.GetArray()[ 7];

    retMtx._31 = mtx.GetArray()[ 8];
    retMtx._32 = mtx.GetArray()[ 9];
    retMtx._33 = mtx.GetArray()[10];
    retMtx._34 = mtx.GetArray()[11];

    retMtx._41 = mtx.GetArray()[12];
    retMtx._42 = mtx.GetArray()[13];
    retMtx._43 = mtx.GetArray()[14];
    retMtx._44 = mtx.GetArray()[15];

    return retMtx;
}

/*********************************************************************************************************************
*                                      CubismClippingManager_DX9
********************************************************************************************************************/
///< ファイルスコープの変数宣言
namespace {
const csmInt32 ColorChannelCount = 4;   ///< 実験時に1チャンネルの場合は1、RGBだけの場合は3、アルファも含める場合は4
}

CubismClippingManager_DX9::CubismClippingManager_DX9()
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

CubismClippingManager_DX9::~CubismClippingManager_DX9()
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

void CubismClippingManager_DX9::Initialize(CubismModel& model, csmInt32 drawableCount, const csmInt32** drawableMasks, const csmInt32* drawableMaskCounts)
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

CubismClippingContext* CubismClippingManager_DX9::FindSameClip(const csmInt32* drawableMasks, csmInt32 drawableMaskCounts) const
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

void CubismClippingManager_DX9::SetupClippingContext(LPDIRECT3DDEVICE9 device, CubismModel& model, CubismRenderer_D3D9* renderer, CubismOffscreenFrame_D3D9& useTarget)
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
            CubismRenderer_D3D9::GetRenderStateManager()->SetViewport(device,
                0,
                0,
                _clippingMaskBufferSize,
                _clippingMaskBufferSize,
                0.0f, 1.0f);

            useTarget.BeginDraw(device);
            // 1が無効（描かれない）領域、0が有効（描かれる）領域。（シェーダで Cd*Csで0に近い値をかけてマスクを作る。1をかけると何も起こらない）
            useTarget.Clear(device, 1.0f, 1.0f, 1.0f, 1.0f);
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
                    renderer->DrawMeshDX9(clipDrawIndex,
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
            useTarget.EndDraw(device);

            renderer->SetClippingContextBufferForMask(NULL);
        }
    }
}

void CubismClippingManager_DX9::CalcClippedDrawTotalBounds(CubismModel& model, CubismClippingContext* clippingContext)
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

void CubismClippingManager_DX9::SetupLayoutBounds(csmInt32 usingClipCount) const
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

CubismRenderer::CubismTextureColor* CubismClippingManager_DX9::GetChannelFlagAsColor(csmInt32 channelNo)
{
    return _channelColors[channelNo];
}

CubismOffscreenFrame_D3D9* CubismClippingManager_DX9::GetColorBuffer() const
{
    return _colorBuffer;
}

csmVector<CubismClippingContext*>* CubismClippingManager_DX9::GetClippingContextListForDraw()
{
    return &_clippingContextListForDraw;
}

void CubismClippingManager_DX9::SetClippingMaskBufferSize(csmInt32 size)
{
    _clippingMaskBufferSize = size;
}

csmInt32 CubismClippingManager_DX9::GetClippingMaskBufferSize() const
{
    return _clippingMaskBufferSize;
}

/*********************************************************************************************************************
*                                      CubismClippingContext
********************************************************************************************************************/
CubismClippingContext::CubismClippingContext(CubismClippingManager_DX9* manager, const csmInt32* clippingDrawableIndices, csmInt32 clipCount)
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

CubismClippingManager_DX9* CubismClippingContext::GetClippingManager()
{
    return _owner;
}


/*********************************************************************************************************************
 *                                      CubismRenderer_D3D9
 ********************************************************************************************************************/

namespace {
    CubismRenderState_D3D9*         s_renderStateManagerInstance;   ///< レンダーステートの管理
    CubismShader_D3D9*              s_shaderManagerInstance;        ///< シェーダー管理

    csmUint32                       s_bufferSetNum;         ///< 作成コンテキストの数。モデルロード前に設定されている必要あり。
    LPDIRECT3DDEVICE9               s_useDevice;            ///< 使用デバイス。モデルロード前に設定されている必要あり。

    csmUint32                       s_viewportWidth;        ///< 描画ターゲット幅 CubismRenderer_D3D9::startframeで渡されます
    csmUint32                       s_viewportHeight;       ///< 描画ターゲット高さ CubismRenderer_D3D9::startframeで渡されます
}

CubismRenderer* CubismRenderer::Create()
{
    return CSM_NEW CubismRenderer_D3D9();
}

void CubismRenderer::StaticRelease()
{
    CubismRenderer_D3D9::DoStaticRelease();
}

CubismRenderState_D3D9* CubismRenderer_D3D9::GetRenderStateManager()
{
    if (s_renderStateManagerInstance == NULL)
    {
        s_renderStateManagerInstance = CSM_NEW CubismRenderState_D3D9();
    }
    return s_renderStateManagerInstance;
}

void CubismRenderer_D3D9::DeleteRenderStateManager()
{
    if (s_renderStateManagerInstance)
    {
        CSM_DELETE_SELF(CubismRenderState_D3D9, s_renderStateManagerInstance);
        s_renderStateManagerInstance = NULL;
    }
}

CubismShader_D3D9* CubismRenderer_D3D9::GetShaderManager()
{
    if (s_shaderManagerInstance == NULL)
    {
        s_shaderManagerInstance = CSM_NEW CubismShader_D3D9();
    }
    return s_shaderManagerInstance;
}

void CubismRenderer_D3D9::DeleteShaderManager()
{
    if (s_shaderManagerInstance)
    {
        CSM_DELETE_SELF(CubismShader_D3D9, s_shaderManagerInstance);
        s_shaderManagerInstance = NULL;
    }
}

void CubismRenderer_D3D9::GenerateShader(LPDIRECT3DDEVICE9 device)
{
    CubismShader_D3D9* shaderManager = GetShaderManager();
    if(shaderManager)
    {
        shaderManager->GenerateShaders(device);
    }
}

void CubismRenderer_D3D9::OnDeviceLost()
{
    // シェーダー・頂点宣言開放
    ReleaseShader();
}

void CubismRenderer_D3D9::ReleaseShader()
{
    CubismShader_D3D9* shaderManager = GetShaderManager();
    if (shaderManager)
    {
        shaderManager->ReleaseShaderProgram();
    }
}

CubismRenderer_D3D9::CubismRenderer_D3D9()
    : _drawableNum(0)
    , _vertexStore(NULL)
    , _indexStore(NULL)
    , _clippingManager(NULL)
    , _clippingContextBufferForMask(NULL)
    , _clippingContextBufferForDraw(NULL)
{
    _commandBufferNum = 0;
    _commandBufferCurrent = 0;

    // テクスチャ対応マップの容量を確保しておく.
    _textures.PrepareCapacity(32, true);
}

CubismRenderer_D3D9::~CubismRenderer_D3D9()
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

    for (csmInt32 drawAssign = 0; drawAssign < drawableCount; drawAssign++)
    {
        // インデックス
        if (_indexStore[drawAssign])
        {
            CSM_FREE(_indexStore[drawAssign]);
        }
        // 頂点
        if (_vertexStore[drawAssign])
        {
            CSM_FREE(_vertexStore[drawAssign]);
        }
    }

    CSM_FREE(_indexStore);
    CSM_FREE(_vertexStore);
    _indexStore = NULL;
    _vertexStore = NULL;

    CSM_DELETE_SELF(CubismClippingManager_DX9, _clippingManager);
}

void CubismRenderer_D3D9::DoStaticRelease()
{
    // レンダーステートマネージャ削除
    DeleteRenderStateManager();
    // シェーダマネージャ削除
    DeleteShaderManager();
}


void CubismRenderer_D3D9::Initialize(CubismModel* model)
{
    // 0は許されず ここに来るまでに設定しなければならない
    if (s_bufferSetNum == 0)
    {
        CubismLogError("ContextNum has not been set.");
        CSM_ASSERT(0);
        return;
    }
    if (s_useDevice == 0)
    {
        CubismLogError("Device has not been set.");
        CSM_ASSERT(0);
        return;
    }

    if (model->IsUsingMasking())
    {
        _clippingManager = CSM_NEW CubismClippingManager_DX9();  //クリッピングマスク・バッファ前処理方式を初期化
        _clippingManager->Initialize(
            *model,
            model->GetDrawableCount(),
            model->GetDrawableMasks(),
            model->GetDrawableMaskCounts()
        );
    }

    _sortedDrawableIndexList.Resize(model->GetDrawableCount(), 0);

    CubismRenderer::Initialize(model);  //親クラスの処理を呼ぶ

    // モデルパーツごとに確保
    const csmInt32 drawableCount = GetModel()->GetDrawableCount();
    _drawableNum = drawableCount;

    _vertexStore = static_cast<CubismVertexD3D9**>(CSM_MALLOC(sizeof(CubismVertexD3D9*) * drawableCount));
    _indexStore = static_cast<csmUint16**>(CSM_MALLOC(sizeof(csmUint16*) * drawableCount));

    for (csmInt32 drawAssign = 0; drawAssign < drawableCount; drawAssign++)
    {
        // 頂点
        const csmInt32 vcount = GetModel()->GetDrawableVertexCount(drawAssign);
        if (vcount != 0)
        {
            _vertexStore[drawAssign] = static_cast<CubismVertexD3D9*>(CSM_MALLOC(sizeof(CubismVertexD3D9) * vcount));
        }
        else
        {
            _vertexStore[drawAssign] = NULL;
        }

        // インデックスはここで要素コピーを済ませる
        const csmInt32 icount = GetModel()->GetDrawableVertexIndexCount(drawAssign);
        if (icount != 0)
        {
            _indexStore[drawAssign] = static_cast<csmUint16*>(CSM_MALLOC(sizeof(csmUint16) * icount));
            const csmUint16* idxarray = GetModel()->GetDrawableVertexIndices(drawAssign);
            for (csmInt32 ct = 0; ct < icount; ct++)
            {// モデルデータからのコピー
                _indexStore[drawAssign][ct] = idxarray[ct];
            }
        }
        else
        {
            _indexStore[drawAssign] = NULL;
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
            CubismOffscreenFrame_D3D9 push;
            _offscreenFrameBuffer.PushBack(push);
        }
        // オフスクリーン
        for (csmUint32 i = 0; i < s_bufferSetNum; i++)
        {
            _offscreenFrameBuffer[i].CreateOffscreenFrame(
                s_useDevice,
                bufferHeight, bufferHeight);
        }
    }
}

void CubismRenderer_D3D9::FrameRenderingInit()
{
}

void CubismRenderer_D3D9::PreDraw()
{
    SetDefaultRenderState();
}

void CubismRenderer_D3D9::PostDraw()
{
    _commandBufferCurrent++;
    if (_commandBufferNum <= _commandBufferCurrent)
    {
        _commandBufferCurrent = 0;
    }
}

void CubismRenderer_D3D9::DoDrawModel()
{
    // NULLは許されず
    CSM_ASSERT(s_useDevice != NULL);

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
            _clippingManager->_colorBuffer->CreateOffscreenFrame(s_useDevice,
                static_cast<csmUint32>(_clippingManager->GetClippingMaskBufferSize()), static_cast<csmUint32>(_clippingManager->GetClippingMaskBufferSize()));
        }

        _clippingManager->SetupClippingContext(s_useDevice, *GetModel(), this, *_clippingManager->_colorBuffer);

        if (!IsUsingHighPrecisionMask())
        {
            // ビューポート
            GetRenderStateManager()->SetViewport(s_useDevice,
                0,
                0,
                s_viewportWidth,
                s_viewportHeight,
                0.0f, 1.0);
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
                GetRenderStateManager()->SetViewport(s_useDevice,
                    0,
                    0,
                    _clippingManager->GetClippingMaskBufferSize(),
                    _clippingManager->GetClippingMaskBufferSize(),
                    0.0f, 1.0f);

                _clippingManager->_colorBuffer->BeginDraw(s_useDevice);
                // 1が無効（描かれない）領域、0が有効（描かれる）領域。（シェーダで Cd*Csで0に近い値をかけてマスクを作る。1をかけると何も起こらない）
                _clippingManager->_colorBuffer->Clear(s_useDevice, 1.0f, 1.0f, 1.0f, 1.0f);

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
                    DrawMeshDX9(clipDrawIndex,
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

                _clippingManager->_colorBuffer->EndDraw(s_useDevice);
                SetClippingContextBufferForMask(NULL);

                // ビューポートを元に戻す
                GetRenderStateManager()->SetViewport(s_useDevice,
                    0,
                    0,
                    s_viewportWidth,
                    s_viewportHeight,
                    0.0f, 1.0f);
            }
        }

        // クリッピングマスクをセットする
        SetClippingContextBufferForDraw(clipContext);

        IsCulling(GetModel()->GetDrawableCulling(drawableIndex) != 0);

        DrawMeshDX9(drawableIndex,
            GetModel()->GetDrawableTextureIndices(drawableIndex),
            GetModel()->GetDrawableVertexIndexCount(drawableIndex),
            GetModel()->GetDrawableVertexCount(drawableIndex),
            const_cast<csmUint16*>(GetModel()->GetDrawableVertexIndices(drawableIndex)),
            const_cast<csmFloat32*>(GetModel()->GetDrawableVertices(drawableIndex)),
            reinterpret_cast<csmFloat32*>(const_cast<Core::csmVector2*>(GetModel()->GetDrawableVertexUvs(drawableIndex))),
            GetModel()->GetDrawableOpacity(drawableIndex),
            GetModel()->GetDrawableBlendMode(drawableIndex),
            GetModel()->GetDrawableInvertedMask(drawableIndex)    // マスクを反転使用するか
        );
    }

    //
    PostDraw();

}

void CubismRenderer_D3D9::ExecuteDraw(LPDIRECT3DDEVICE9 device, CubismVertexD3D9* vertexArray, csmUint16* indexArray,
    const csmInt32 vertexCount, const csmInt32 triangleCount,
    const csmInt32 textureNo, CubismTextureColor& modelColorRGBA, CubismBlendMode colorBlendMode, csmBool invertedMask)
{
    // 使用シェーダエフェクト取得
    CubismShader_D3D9* shaderManager = Live2D::Cubism::Framework::Rendering::CubismRenderer_D3D9::GetShaderManager();
    if(!shaderManager)
    {
        return;
    }

    // テクスチャセット
    LPDIRECT3DTEXTURE9 texture = NULL;
    if (textureNo >= 0)
    {
        texture = _textures[textureNo];
    }

    if (texture == NULL)
    {
        return;    // モデルが参照するテクスチャがバインドされていない場合は描画をスキップする
    }

    if (GetClippingContextBufferForMask() != NULL) // マスク生成時
    {
        ID3DXEffect* shaderEffect = shaderManager->GetShaderEffect();
        if (shaderEffect)
        {
            UINT numPass = 0;
            shaderEffect->SetTechnique("ShaderNames_SetupMask");

            // numPassには指定のtechnique内に含まれるpassの数が返る
            shaderEffect->Begin(&numPass, 0);
            shaderEffect->BeginPass(0);


            // チャンネル
            const csmInt32 channelNo = GetClippingContextBufferForMask()->_layoutChannelNo;
            // チャンネルをRGBAに変換
            CubismTextureColor* colorChannel = GetClippingContextBufferForMask()->GetClippingManager()->GetChannelFlagAsColor(channelNo);

            GetRenderStateManager()->SetBlend(device,
                true,
                true,
                D3DBLEND_ZERO,
                D3DBLENDOP_ADD,
                D3DBLEND_INVSRCCOLOR,
                D3DBLEND_ZERO,
                D3DBLENDOP_ADD,
                D3DBLEND_INVSRCALPHA);

            if (GetAnisotropy() > 0.0)
            {
                GetRenderStateManager()->SetTextureFilter(device, D3DTEXF_ANISOTROPIC, D3DTEXF_ANISOTROPIC, D3DTEXF_LINEAR, D3DTADDRESS_WRAP, D3DTADDRESS_WRAP, GetAnisotropy());
            }
            else
            {
                GetRenderStateManager()->SetTextureFilter(device, D3DTEXF_LINEAR, D3DTEXF_LINEAR, D3DTEXF_LINEAR, D3DTADDRESS_WRAP, D3DTADDRESS_WRAP);
            }

            // 定数バッファ
            {
                csmRectF* rect = GetClippingContextBufferForMask()->_layoutBounds;

                CubismMatrix44 modelToWorldF = GetClippingContextBufferForMask()->_matrixForMask;
                D3DXMATRIX proj = ConvertToD3DX(modelToWorldF);
                shaderEffect->SetMatrix("projectMatrix", &proj);

                D3DXVECTOR4 color(rect->X * 2.0f - 1.0f, rect->Y * 2.0f - 1.0f, rect->GetRight() * 2.0f - 1.0f, rect->GetBottom() * 2.0f - 1.0f);
                shaderEffect->SetVector("baseColor", &color);

                D3DXVECTOR4 channel(colorChannel->R, colorChannel->G, colorChannel->B, colorChannel->A);
                shaderEffect->SetVector("channelFlag", &channel);

                // テクスチャセット
                shaderEffect->SetTexture("mainTexture", texture);
                // 使わない場合はNULLを必ず代入
                shaderEffect->SetTexture("maskTexture", NULL);

                // パラメータ反映
                shaderEffect->CommitChanges();
            }


            // 描画
            device->DrawIndexedPrimitiveUP(D3DPT_TRIANGLELIST, 0, vertexCount, triangleCount, indexArray, D3DFMT_INDEX16, vertexArray, sizeof(CubismVertexD3D9));

            shaderEffect->EndPass();
            shaderEffect->End();

        }
    }
    else // マスク生成以外の場合
    {
        const csmBool masked = GetClippingContextBufferForDraw() != NULL;  // この描画オブジェクトはマスク対象か
        const csmBool premult = IsPremultipliedAlpha();
        const csmInt32 offset = (masked ? (invertedMask ? 2 : 1) : 0) + (IsPremultipliedAlpha() ? 3 : 0);

        switch (colorBlendMode)
        {
        case CubismRenderer::CubismBlendMode::CubismBlendMode_Normal:
        default:
            GetRenderStateManager()->SetBlend(device,
                true,
                true,
                D3DBLEND_ONE,
                D3DBLENDOP_ADD,
                D3DBLEND_INVSRCALPHA,
                D3DBLEND_ONE,
                D3DBLENDOP_ADD,
                D3DBLEND_INVSRCALPHA);
            break;

        case CubismRenderer::CubismBlendMode::CubismBlendMode_Additive:
            GetRenderStateManager()->SetBlend(device,
                true,
                true,
                D3DBLEND_ONE,
                D3DBLENDOP_ADD,
                D3DBLEND_ONE,
                D3DBLEND_ZERO,
                D3DBLENDOP_ADD,
                D3DBLEND_ONE);
            break;

        case CubismRenderer::CubismBlendMode::CubismBlendMode_Multiplicative:
            GetRenderStateManager()->SetBlend(device,
                true,
                true,
                D3DBLEND_DESTCOLOR,
                D3DBLENDOP_ADD,
                D3DBLEND_INVSRCALPHA,
                D3DBLEND_ZERO,
                D3DBLENDOP_ADD,
                D3DBLEND_ONE);
            break;
        }

        if (GetAnisotropy() > 0.0)
        {
            GetRenderStateManager()->SetTextureFilter(device, D3DTEXF_ANISOTROPIC, D3DTEXF_ANISOTROPIC, D3DTEXF_LINEAR, D3DTADDRESS_WRAP, D3DTADDRESS_WRAP, GetAnisotropy());
        }
        else
        {
            GetRenderStateManager()->SetTextureFilter(device, D3DTEXF_LINEAR, D3DTEXF_LINEAR, D3DTEXF_LINEAR, D3DTADDRESS_WRAP, D3DTADDRESS_WRAP);
        }

        ID3DXEffect* shaderEffect = shaderManager->GetShaderEffect();
        if (shaderEffect)
        {
            UINT numPass = 0;
            if (masked)
            {
                if(premult)
                {
                    if (invertedMask)
                    {
                        shaderEffect->SetTechnique("ShaderNames_NormalMaskedInvertedPremultipliedAlpha");
                    }
                    else
                    {
                        shaderEffect->SetTechnique("ShaderNames_NormalMaskedPremultipliedAlpha");
                    }
                }
                else
                {
                    if (invertedMask)
                    {
                        shaderEffect->SetTechnique("ShaderNames_NormalMaskedInverted");
                    }
                    else
                    {
                        shaderEffect->SetTechnique("ShaderNames_NormalMasked");
                    }
                }
            }
            else
            {
                if(premult)
                {
                    shaderEffect->SetTechnique("ShaderNames_NormalPremultipliedAlpha");
                }
                else
                {
                    shaderEffect->SetTechnique("ShaderNames_Normal");
                }
            }

            // numPassには指定のtechnique内に含まれるpassの数が返る
            shaderEffect->Begin(&numPass, 0);
            shaderEffect->BeginPass(0);

            if (!masked)
            {
                // テクスチャセット
                shaderEffect->SetTexture("mainTexture", texture);
                // 使わない場合はNULLを必ず代入
                shaderEffect->SetTexture("maskTexture", NULL);
            }
            else
            {
                // テクスチャセット メイン
                shaderEffect->SetTexture("mainTexture", texture);
                // テクスチャセット マスク
                shaderEffect->SetTexture("maskTexture", _clippingManager->_colorBuffer->GetTexture());
            }

            // 定数バッファ
            {
                if (masked)
                {
                    // View座標をClippingContextの座標に変換するための行列を設定

                    CubismMatrix44 ClipF = GetClippingContextBufferForDraw()->_matrixForDraw;
                    D3DXMATRIX clipM = ConvertToD3DX(ClipF);
                    shaderEffect->SetMatrix("clipMatrix", &clipM);

                    // 使用するカラーチャンネルを設定
                    const csmInt32 channelNo = GetClippingContextBufferForDraw()->_layoutChannelNo;
                    CubismRenderer::CubismTextureColor* colorChannel = GetClippingContextBufferForDraw()->GetClippingManager()->GetChannelFlagAsColor(channelNo);

                    D3DXVECTOR4 channel(colorChannel->R, colorChannel->G, colorChannel->B, colorChannel->A);
                    shaderEffect->SetVector("channelFlag", &channel);
                }

                CubismMatrix44 mvp = GetMvpMatrix();
                D3DXMATRIX proj = ConvertToD3DX(mvp);
                shaderEffect->SetMatrix("projectMatrix", &proj);
                D3DXVECTOR4 color(modelColorRGBA.R, modelColorRGBA.G, modelColorRGBA.B, modelColorRGBA.A);
                shaderEffect->SetVector("baseColor", &color);
            }

            // パラメータ反映
            shaderEffect->CommitChanges();

            // 描画
            device->DrawIndexedPrimitiveUP(D3DPT_TRIANGLELIST, 0, vertexCount, triangleCount, indexArray, D3DFMT_INDEX16, vertexArray, sizeof(CubismVertexD3D9));

            shaderEffect->EndPass();
            shaderEffect->End();
        }
    }
}

void CubismRenderer_D3D9::DrawMesh(csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
    , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
    , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask)
{
    CubismLogWarning("Use 'DrawMeshDX9' function");
    CSM_ASSERT(0);
}

void CubismRenderer_D3D9::DrawMeshDX9( csmInt32 drawableIndex
    , csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
    , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
    , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask)
{
    if (s_useDevice == NULL)
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
    LPDIRECT3DTEXTURE9 texture = NULL;
    if (textureNo >= 0)
    {
        texture = _textures[textureNo];
    }

    if (texture == NULL) return;    // モデルが参照するテクスチャがバインドされていない場合は描画をスキップする


    // 裏面描画の有効・無効
    if (IsCulling())
    {
        GetRenderStateManager()->SetCullMode(s_useDevice,
            D3DCULL_CW); // CWを消す
    }
    else
    {
        GetRenderStateManager()->SetCullMode(s_useDevice,
            D3DCULL_NONE); // カリング無し
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
    CopyToBuffer(drawableIndex, vertexCount, vertexArray, uvArray);

    // シェーダーセット
    ExecuteDraw(s_useDevice, _vertexStore[drawableIndex], _indexStore[drawableIndex],
        vertexCount, indexCount/3,
        textureNo, modelColorRGBA, colorBlendMode, invertedMask);

    SetClippingContextBufferForDraw(NULL);
    SetClippingContextBufferForMask(NULL);
}

void CubismRenderer_D3D9::SaveProfile()
{
    // NULLは許されず
    CSM_ASSERT(s_useDevice != NULL);

    // 現在のレンダリングステートをPush
    GetRenderStateManager()->SaveCurrentNativeState(s_useDevice);
}

void CubismRenderer_D3D9::RestoreProfile()
{
    // NULLは許されず
    CSM_ASSERT(s_useDevice != NULL);

    // SaveCurrentNativeStateと対
    GetRenderStateManager()->RestoreNativeState(s_useDevice);
}

void CubismRenderer_D3D9::BindTexture(csmUint32 modelTextureAssign, LPDIRECT3DTEXTURE9 texture)
{
    _textures[modelTextureAssign] = texture;
}

const csmMap<csmInt32, LPDIRECT3DTEXTURE9>& CubismRenderer_D3D9::GetBindedTextures() const
{
    return _textures;
}

void CubismRenderer_D3D9::SetClippingMaskBufferSize(csmInt32 size)
{
    //FrameBufferのサイズを変更するためにインスタンスを破棄・再作成する
    CSM_DELETE_SELF(CubismClippingManager_DX9, _clippingManager);

    _clippingManager = CSM_NEW CubismClippingManager_DX9();

    _clippingManager->SetClippingMaskBufferSize(size);

    _clippingManager->Initialize(
        *GetModel(),
        GetModel()->GetDrawableCount(),
        GetModel()->GetDrawableMasks(),
        GetModel()->GetDrawableMaskCounts()
    );
}

csmInt32 CubismRenderer_D3D9::GetClippingMaskBufferSize() const
{
    return _clippingManager->GetClippingMaskBufferSize();
}

void CubismRenderer_D3D9::InitializeConstantSettings(csmUint32 bufferSetNum, LPDIRECT3DDEVICE9 device)
{
    s_bufferSetNum = bufferSetNum;
    s_useDevice = device;
}

void CubismRenderer_D3D9::SetDefaultRenderState()
{
    // Zは無効 描画順で制御
    GetRenderStateManager()->SetZEnable(s_useDevice,
        D3DZB_FALSE,
        D3DCMP_LESS);

    // ビューポート
    GetRenderStateManager()->SetViewport(s_useDevice,
        0,
        0,
        s_viewportWidth,
        s_viewportHeight,
        0.0f, 1.0);

    // カラーマスク
    GetRenderStateManager()->SetColorMask(s_useDevice,
        D3DCOLORWRITEENABLE_ALPHA | D3DCOLORWRITEENABLE_BLUE | D3DCOLORWRITEENABLE_GREEN | D3DCOLORWRITEENABLE_RED);

    // カリング
    GetRenderStateManager()->SetCullMode(s_useDevice,
        D3DCULL_CW); // CWを消す

    // フィルタ
    GetRenderStateManager()->SetTextureFilter(s_useDevice,
        D3DTEXF_LINEAR, D3DTEXF_LINEAR, D3DTEXF_LINEAR, D3DTADDRESS_WRAP, D3DTADDRESS_WRAP
    );
}

void CubismRenderer_D3D9::StartFrame(LPDIRECT3DDEVICE9 device, csmUint32 viewportWidth, csmUint32 viewportHeight)
{
    // フレームで使用するデバイス設定
    s_useDevice = device;
    s_viewportWidth = viewportWidth;
    s_viewportHeight = viewportHeight;

    // レンダーステートフレーム先頭処理
    GetRenderStateManager()->StartFrame();

    // シェーダ・頂点宣言
    GetShaderManager()->SetupShader(s_useDevice);
}

void CubismRenderer_D3D9::EndFrame(LPDIRECT3DDEVICE9 device)
{
    // 使用シェーダエフェクト取得
    Live2D::Cubism::Framework::Rendering::CubismShader_D3D9* shaderManager = Live2D::Cubism::Framework::Rendering::CubismRenderer_D3D9::GetShaderManager();
    {
        ID3DXEffect* shaderEffect = shaderManager->GetShaderEffect();
        // テクスチャの参照を外しておく
        if (shaderEffect)
        {
            shaderEffect->SetTexture("mainTexture", NULL);
            shaderEffect->SetTexture("maskTexture", NULL);
            shaderEffect->CommitChanges();
        }
    }
}

void CubismRenderer_D3D9::SetClippingContextBufferForDraw(CubismClippingContext* clip)
{
    _clippingContextBufferForDraw = clip;
}

CubismClippingContext* CubismRenderer_D3D9::GetClippingContextBufferForDraw() const
{
    return _clippingContextBufferForDraw;
}

void CubismRenderer_D3D9::SetClippingContextBufferForMask(CubismClippingContext* clip)
{
    _clippingContextBufferForMask = clip;
}

CubismClippingContext* CubismRenderer_D3D9::GetClippingContextBufferForMask() const
{
    return _clippingContextBufferForMask;
}

void CubismRenderer_D3D9::CopyToBuffer(csmInt32 drawAssign, const csmInt32 vcount, const csmFloat32* varray, const csmFloat32* uvarray)
{
    for (csmInt32 ct = 0; ct < vcount * 2; ct += 2)
    {// モデルデータからのコピー
        _vertexStore[drawAssign][ct / 2].x = varray[ct + 0];
        _vertexStore[drawAssign][ct / 2].y = varray[ct + 1];

        _vertexStore[drawAssign][ct / 2].u = uvarray[ct + 0];
        _vertexStore[drawAssign][ct / 2].v = uvarray[ct + 1];
    }
}

}}}}

//------------ LIVE2D NAMESPACE ------------
