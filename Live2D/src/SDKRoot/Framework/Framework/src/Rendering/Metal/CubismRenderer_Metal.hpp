/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <MetalKit/MetalKit.h>
#include "../CubismRenderer.hpp"
#include "CubismFramework.hpp"
#include "CubismOffscreenSurface_Metal.hpp"
#include "CubismCommandBuffer_Metal.hpp"
#include "Type/csmVector.hpp"
#include "Type/csmRectF.hpp"
#include "Type/csmMap.hpp"
#include "Math/CubismVector2.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

//  前方宣言
class CubismRenderer_Metal;
class CubismClippingContext;

/**
 * @brief  クリッピングマスクの処理を実行するクラス
 *
 */
class CubismClippingManager_Metal
{
    friend class CubismShader_Metal;
    friend class CubismRenderer_Metal;

private:

    /**
     * @brief カラーチャンネル(RGBA)のフラグを取得する
     *
     * @param[in]   channelNo   ->   カラーチャンネル(RGBA)の番号(0:R , 1:G , 2:B, 3:A)
     */
    CubismRenderer::CubismTextureColor* GetChannelFlagAsColor(csmInt32 channelNo);

    /**
     * @brief   マスクされる描画オブジェクト群全体を囲む矩形(モデル座標系)を計算する
     *
     * @param[in]   model            ->  モデルのインスタンス
     * @param[in]   clippingContext  ->  クリッピングマスクのコンテキスト
     */
    void CalcClippedDrawTotalBounds(CubismModel& model, CubismClippingContext* clippingContext);

    /**
     * @brief    コンストラクタ
     */
    CubismClippingManager_Metal();

    /**
     * @brief    デストラクタ
     */
    virtual ~CubismClippingManager_Metal();

    /**
     * @brief    マネージャの初期化処理<br>
     *           クリッピングマスクを使う描画オブジェクトの登録を行う
     *
     * @param[in]   model           ->  モデルのインスタンス
     * @param[in]   drawableCount   ->  描画オブジェクトの数
     * @param[in]   drawableMasks   ->  描画オブジェクトをマスクする描画オブジェクトのインデックスのリスト
     * @param[in]   drawableMaskCounts   ->  描画オブジェクトをマスクする描画オブジェクトの数
     */
    void Initialize(CubismModel& model, csmInt32 drawableCount, const csmInt32** drawableMasks, const csmInt32* drawableMaskCounts);

    /**
     * @brief   クリッピングコンテキストを作成する。モデル描画時に実行する。
     *
     * @param[in]   model        ->  モデルのインスタンス
     * @param[in]   renderer     ->  レンダラのインスタンス
     * @param[in]   lastFBO      ->  フレームバッファ
     * @param[in]   lastViewport ->  ビューポート
     */
    void SetupClippingContext(CubismModel& model, CubismRenderer_Metal* renderer, CubismOffscreenFrame_Metal* lastColorBuffer, csmRectF lastViewport);

    /**
     * @brief   既にマスクを作っているかを確認。<br>
     *          作っているようであれば該当するクリッピングマスクのインスタンスを返す。<br>
     *          作っていなければNULLを返す
     *
     * @param[in]   drawableMasks    ->  描画オブジェクトをマスクする描画オブジェクトのリスト
     * @param[in]   drawableMaskCounts ->  描画オブジェクトをマスクする描画オブジェクトの数
     * @return          該当するクリッピングマスクが存在すればインスタンスを返し、なければNULLを返す。
     */
    CubismClippingContext* FindSameClip(const csmInt32* drawableMasks, csmInt32 drawableMaskCounts) const;

    /**
     * @brief   クリッピングコンテキストを配置するレイアウト。<br>
     *           ひとつのレンダーテクスチャを極力いっぱいに使ってマスクをレイアウトする。<br>
     *           マスクグループの数が4以下ならRGBA各チャンネルに１つずつマスクを配置し、5以上6以下ならRGBAを2,2,1,1と配置する。
     *
     * @param[in]   usingClipCount  ->  配置するクリッピングコンテキストの数
     */
    void SetupLayoutBounds(csmInt32 usingClipCount) const;

    /**
     * @brief   画面描画に使用するクリッピングマスクのリストを取得する
     *
     * @return  画面描画に使用するクリッピングマスクのリスト
     */
    csmVector<CubismClippingContext*>* GetClippingContextListForDraw();

    /**
     * @brief  クリッピングマスクバッファのサイズを設定する
     *
     * @param  size -> クリッピングマスクバッファのサイズ
     *
     */
    void SetClippingMaskBufferSize(csmFloat32 width, csmFloat32 height);

    /**
     * @brief  クリッピングマスクバッファのサイズを取得する
     *
     * @return クリッピングマスクバッファのサイズ
     *
     */
    CubismVector2 GetClippingMaskBufferSize() const;

    csmInt32    _currentFrameNo;         ///< マスクテクスチャに与えるフレーム番号

    csmVector<CubismRenderer::CubismTextureColor*>  _channelColors;
    csmVector<CubismClippingContext*>               _clippingContextListForMask;   ///< マスク用クリッピングコンテキストのリスト
    csmVector<CubismClippingContext*>               _clippingContextListForDraw;   ///< 描画用クリッピングコンテキストのリスト
    CubismVector2                                   _clippingMaskBufferSize; ///< クリッピングマスクのバッファサイズ（初期値:256）

    CubismMatrix44  _tmpMatrix;              ///< マスク計算用の行列
    CubismMatrix44  _tmpMatrixForMask;       ///< マスク計算用の行列
    CubismMatrix44  _tmpMatrixForDraw;       ///< マスク計算用の行列
    csmRectF        _tmpBoundsOnModel;       ///< マスク配置計算用の矩形

};

/**
 * @brief   クリッピングマスクのコンテキスト
 */
class CubismClippingContext
{
    friend class CubismClippingManager_Metal;
    friend class CubismShader_Metal;
    friend class CubismRenderer_Metal;

private:
    /**
     * @brief   引数付きコンストラクタ
     *
     */
    CubismClippingContext(CubismClippingManager_Metal* manager, CubismModel& model, const csmInt32* clippingDrawableIndices, csmInt32 clipCount);

    /**
     * @brief   デストラクタ
     */
    virtual ~CubismClippingContext();

    /**
     * @brief   このマスクにクリップされる描画オブジェクトを追加する
     *
     * @param[in]   drawableIndex   ->  クリッピング対象に追加する描画オブジェクトのインデックス
     */
    void AddClippedDrawable(csmInt32 drawableIndex);

    /**
     * @brief   このマスクを管理するマネージャのインスタンスを取得する。
     *
     * @return  クリッピングマネージャのインスタンス
     */
    CubismClippingManager_Metal* GetClippingManager();

    csmBool _isUsing;                                ///< 現在の描画状態でマスクの準備が必要ならtrue
    const csmInt32* _clippingIdList;                 ///< クリッピングマスクのIDリスト
    csmInt32 _clippingIdCount;                       ///< クリッピングマスクの数
    csmInt32 _layoutChannelNo;                       ///< RGBAのいずれのチャンネルにこのクリップを配置するか(0:R , 1:G , 2:B , 3:A)
    csmRectF* _layoutBounds;                         ///< マスク用チャンネルのどの領域にマスクを入れるか(View座標-1..1, UVは0..1に直す)
    csmRectF* _allClippedDrawRect;                   ///< このクリッピングで、クリッピングされる全ての描画オブジェクトの囲み矩形（毎回更新）
    CubismMatrix44 _matrixForMask;                   ///< マスクの位置計算結果を保持する行列
    CubismMatrix44 _matrixForDraw;                   ///< 描画オブジェクトの位置計算結果を保持する行列
    csmVector<csmInt32>* _clippedDrawableIndexList;  ///< このマスクにクリップされる描画オブジェクトのリスト
    csmVector<CubismCommandBuffer_Metal::DrawCommandBuffer*>* _clippingCommandBufferList;

    CubismClippingManager_Metal* _owner;        ///< このマスクを管理しているマネージャのインスタンス
};

/**
 * @brief   Metal用のシェーダプログラムを生成・破棄するクラス<br>
 *           シングルトンなクラスであり、CubismShader_Metal::GetInstance()からアクセスする。
 *
 */
class CubismShader_Metal
{
    friend class CubismRenderer_Metal;

private:
    /**
     * @brief   インスタンスを取得する（シングルトン）。
     *
     * @return  インスタンスのポインタ
     */
    static CubismShader_Metal* GetInstance();

    /**
     * @brief   インスタンスを解放する（シングルトン）。
     */
    static void DeleteInstance();

    struct ShaderProgram
    {
        id <MTLFunction> vertexFunction;
        id <MTLFunction> fragmentFunction;
    };

    /**
    * @bref    シェーダープログラムとシェーダ変数のアドレスを保持する構造体
    *
    */
    struct CubismShaderSet
    {
        ShaderProgram *ShaderProgram; ///< シェーダプログラムのアドレス
        id<MTLRenderPipelineState> RenderPipelineState;
        id<MTLDepthStencilState> DepthStencilState;
        id<MTLSamplerState> SamplerState;
    };

    /**
     * @brief   privateなコンストラクタ
     */
    CubismShader_Metal();

    /**
     * @brief   privateなデストラクタ
     */
    virtual ~CubismShader_Metal();

    /**
     * @brief   シェーダプログラムの一連のセットアップを実行する
     *
     * @param[in]   renderer              ->  レンダラのインスタンス
     * @param[in]   textureId             ->  GPUのテクスチャID
     * @param[in]   vertexCount           ->  ポリゴンメッシュの頂点数
     * @param[in]   vertexArray           ->  ポリゴンメッシュの頂点配列
     * @param[in]   uvArray               ->  uv配列
     * @param[in]   opacity               ->  不透明度
     * @param[in]   colorBlendMode        ->  カラーブレンディングのタイプ
     * @param[in]   baseColor             ->  ベースカラー
     * @param[in]   isPremultipliedAlpha  ->  乗算済みアルファかどうか
     * @param[in]   matrix4x4             ->  Model-View-Projection行列
     * @param[in]   invertedMask           ->  マスクを反転して使用するフラグ
     * @param[in]   renderEncoder           ->  MTLRenderCommandEncoder
     */
    void SetupShaderProgram(CubismCommandBuffer_Metal::DrawCommandBuffer* drawCommandBuffer, CubismRenderer_Metal* renderer, id <MTLTexture> texture
                            , csmFloat32 opacity
                            , CubismRenderer::CubismBlendMode colorBlendMode
                            , CubismRenderer::CubismTextureColor baseColor
                            , CubismRenderer::CubismTextureColor multiplyColor
                            , CubismRenderer::CubismTextureColor screenColor
                            , csmBool isPremultipliedAlpha, CubismMatrix44 matrix4x4
                            , csmBool invertedMask
                            , id <MTLRenderCommandEncoder> renderEncoder);

    /**
     * @brief   シェーダプログラムを初期化する
     */
    void GenerateShaders(CubismRenderer_Metal* renderer);

    /**
     * @brief   シェーダプログラムをロードしてアドレス返す。
     *
     * @param[in]   vertShaderSrc   ->  頂点シェーダのソース
     * @param[in]   fragShaderSrc   ->  フラグメントシェーダのソース
     *
     * @return  シェーダプログラムのアドレス
     */
    ShaderProgram* LoadShaderProgram(const csmChar* vertShaderSrc, const csmChar* fragShaderSrc);
    id<MTLRenderPipelineState> MakeRenderPipelineState(id<MTLDevice> device, ShaderProgram* shaderProgram, int blendMode);
    id<MTLDepthStencilState> MakeDepthStencilState(id<MTLDevice> device);
    id<MTLSamplerState> MakeSamplerState(id<MTLDevice> device, CubismRenderer_Metal* renderer);

    id<MTLLibrary> _shaderLib;

    csmVector<CubismShaderSet*> _shaderSets;   ///< ロードしたシェーダプログラムを保持する変数

};

/**
 * @brief   Cubismモデルを描画する直前のMetalのステートを保持・復帰させるクラス
 *
 */
class CubismRendererProfile_Metal
{
    friend class CubismRenderer_Metal;

private:
    /**
     * @brief   privateなコンストラクタ
     */
    CubismRendererProfile_Metal() {};

    /**
     * @brief   privateなデストラクタ
     */
    virtual ~CubismRendererProfile_Metal() {};

    csmBool _lastScissorTest;             ///< モデル描画直前のGL_VERTEX_ATTRIB_ARRAY_ENABLEDパラメータ
    csmBool _lastBlend;                   ///< モデル描画直前のGL_SCISSOR_TESTパラメータ
    csmBool _lastStencilTest;             ///< モデル描画直前のGL_STENCIL_TESTパラメータ
    csmBool _lastDepthTest;               ///< モデル描画直前のGL_DEPTH_TESTパラメータ
    CubismOffscreenFrame_Metal* _lastColorBuffer;                         ///< モデル描画直前のフレームバッファ
    id <MTLTexture> _lastDepthBuffer;
    id <MTLTexture> _lastStencilBuffer;
    csmRectF _lastViewport;                 ///< モデル描画直前のビューポート
};

/**
 * @brief   Metal用の描画命令を実装したクラス
 *
 */
class CubismRenderer_Metal : public CubismRenderer
{
    friend class CubismRenderer;
    friend class CubismClippingManager_Metal;
    friend class CubismShader_Metal;

public:

    static void StartFrame(id<MTLDevice> device, id<MTLCommandBuffer> commandBuffer, MTLRenderPassDescriptor* renderPassDescriptor);

    /**
     * @brief    レンダラの初期化処理を実行する<br>
     *           引数に渡したモデルからレンダラの初期化処理に必要な情報を取り出すことができる
     *
     * @param[in]  model -> モデルのインスタンス
     */
    void Initialize(Framework::CubismModel* model);

    /**
     * @brief   テクスチャのバインド処理<br>
     *           CubismRendererにテクスチャを設定し、CubismRenderer中でその画像を参照するためのIndex値を戻り値とする
     *
     * @param[in]   modelTextureNo  ->  セットするモデルテクスチャの番号
     * @param[in]   texture     ->  バックエンドテクスチャ
     *
     */
    void BindTexture(csmUint32 modelTextureNo, id <MTLTexture> texture);

    /**
     * @brief   バインドされたテクスチャのリストを取得する
     *
     * @return  テクスチャのアドレスのリスト
     */
    const csmMap<csmInt32, id <MTLTexture>>& GetBindedTextures() const;

    /**
     * @brief  クリッピングマスクバッファのサイズを設定する<br>
     *         マスク用のFrameBufferを破棄・再作成するため処理コストは高い。
     *
     * @param[in]  size -> クリッピングマスクバッファのサイズ
     *
     */
    void SetClippingMaskBufferSize(csmFloat32 width, csmFloat32 height);

    /**
     * @brief  クリッピングマスクバッファのサイズを取得する
     *
     * @return クリッピングマスクバッファのサイズ
     *
     */
    CubismVector2 GetClippingMaskBufferSize() const;

    CubismCommandBuffer_Metal* GetCommandBuffer()
    {
        return &_commandBuffer;
    }

protected:
    /**
     * @brief   コンストラクタ
     */
    CubismRenderer_Metal();

    /**
     * @brief   デストラクタ
     */
    virtual ~CubismRenderer_Metal();

    /**
     * @brief   モデルを描画する実際の処理
     *
     */
    void DoDrawModel();

    /**
     * @brief   [オーバーライド]<br>
     *           描画オブジェクト（アートメッシュ）を描画する。<br>
     *           ポリゴンメッシュとテクスチャ番号をセットで渡す。
     *
     * @param[in]   textureNo       ->  描画するテクスチャ番号
     * @param[in]   indexCount      ->  描画オブジェクトのインデックス値
     * @param[in]   vertexCount     ->  ポリゴンメッシュの頂点数
     * @param[in]   indexArray      ->  ポリゴンメッシュのインデックス配列
     * @param[in]   vertexArray     ->  ポリゴンメッシュの頂点配列
     * @param[in]   uvArray         ->  uv配列
     * @param[in]   opacity         ->  不透明度
     * @param[in]   colorBlendMode  ->  カラー合成タイプ
     * @param[in]   invertedMask     ->  マスク使用時のマスクの反転使用
     * @param[in]   drawableIndex     ->  DrawCommandBuffer番号
     * @param[in]   renderEncoder     ->  MTLRenderCommandEncoder
     *
     */

    void DrawMesh(csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount, csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray, csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask);

    void DrawMeshMetal(CubismCommandBuffer_Metal::DrawCommandBuffer* drawCommandBuffer, csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
                  , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
                  , const CubismTextureColor& multiplyColor, const CubismTextureColor& screenColor
                  , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask
                  , csmInt32 drawableIndex
                  , id <MTLRenderCommandEncoder> renderEncoder);

    CubismCommandBuffer_Metal::DrawCommandBuffer* GetDrawCommandBufferData(csmInt32 drawableIndex);

private:

    // Prevention of copy Constructor
    CubismRenderer_Metal(const CubismRenderer_Metal&);
    CubismRenderer_Metal& operator=(const CubismRenderer_Metal&);

    static id<MTLCommandBuffer> s_commandBuffer;
    static id<MTLDevice> s_device;
    static MTLRenderPassDescriptor* s_renderPassDescriptor;

    /**
     * @brief   レンダラが保持する静的なリソースを解放する<br>
     *           Metalの静的なシェーダプログラムを解放する
     */
    static void DoStaticRelease();

    /**
     * @brief   描画開始時の追加処理。<br>
     *           モデルを描画する前にクリッピングマスクに必要な処理を実装している。
     * @return  描画に使うMTLRenderCommandEncoder
     */
    id <MTLRenderCommandEncoder> PreDraw(id <MTLCommandBuffer> commandBuffer, MTLRenderPassDescriptor* drawableRenderDescriptor);

    /**
     * @brief   描画完了後の追加処理。
     *
     */
    void PostDraw(id <MTLRenderCommandEncoder> renderEncoder);

    /**
     * @brief   SuperClass対応
     */
    virtual void SaveProfile();

    /**
     * @brief   SuperClass対応
     */
    virtual void RestoreProfile();

    /**
     * @brief   マスクテクスチャに描画するクリッピングコンテキストをセットする。
     */
    void SetClippingContextBufferForMask(CubismClippingContext* clip);

    /**
     * @brief   マスクテクスチャに描画するクリッピングコンテキストを取得する。
     *
     * @return  マスクテクスチャに描画するクリッピングコンテキスト
     */
    CubismClippingContext* GetClippingContextBufferForMask() const;

    /**
     * @brief   画面上に描画するクリッピングコンテキストをセットする。
     */
    void SetClippingContextBufferForDraw(CubismClippingContext* clip);

    /**
     * @brief   画面上に描画するクリッピングコンテキストを取得する。
     *
     * @return  画面上に描画するクリッピングコンテキスト
     */
    CubismClippingContext* GetClippingContextBufferForDraw() const;

    csmMap<csmInt32, id <MTLTexture>>            _textures;                      ///< モデルが参照するテクスチャとレンダラでバインドしているテクスチャとのマップ
    csmVector<csmInt32>                 _sortedDrawableIndexList;       ///< 描画オブジェクトのインデックスを描画順に並べたリスト
    CubismRendererProfile_Metal     _rendererProfile;               ///< Metalのステートを保持するオブジェクト
    CubismClippingManager_Metal*    _clippingManager;               ///< クリッピングマスク管理オブジェクト
    CubismClippingContext*              _clippingContextBufferForMask;  ///< マスクテクスチャに描画するためのクリッピングコンテキスト
    CubismClippingContext*              _clippingContextBufferForDraw;  ///< 画面上描画するためのクリッピングコンテキスト

    CubismOffscreenFrame_Metal      _offscreenFrameBuffer;          ///< マスク描画用のフレームバッファ
    CubismCommandBuffer_Metal       _commandBuffer;
    csmVector<CubismCommandBuffer_Metal::DrawCommandBuffer*>  _drawableDrawCommandBuffer;
};

}}}}
//------------ LIVE2D NAMESPACE ------------
