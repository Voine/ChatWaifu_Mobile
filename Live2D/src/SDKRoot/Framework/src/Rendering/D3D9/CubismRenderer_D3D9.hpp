/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismNativeInclude_D3D9.hpp"

#include "../CubismRenderer.hpp"
#include "CubismFramework.hpp"
#include "Type/csmVector.hpp"
#include "Type/csmRectF.hpp"
#include "Type/csmMap.hpp"
#include "Rendering/D3D9/CubismOffscreenSurface_D3D9.hpp"
#include "CubismRenderState_D3D9.hpp"
#include "CubismType_D3D9.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

//  前方宣言
class CubismRenderer_D3D9;
class CubismShader_D3D9;
class CubismClippingContext;

/**
 * @brief D3DXMATRIXに変換
 */
D3DXMATRIX ConvertToD3DX(CubismMatrix44& mtx);

/**
 * @brief  クリッピングマスクの処理を実行するクラス
 *
 */
class CubismClippingManager_DX9
{
    friend class CubismShader_D3D9;
    friend class CubismRenderer_D3D9;

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
    CubismClippingManager_DX9();

    /**
     * @brief    デストラクタ
     */
    virtual ~CubismClippingManager_DX9();

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
     * @param[in]   model       ->  モデルのインスタンス
     * @param[in]   renderer    ->  レンダラのインスタンス
     */
    void SetupClippingContext(LPDIRECT3DDEVICE9 device, CubismModel& model, CubismRenderer_D3D9* renderer, CubismOffscreenFrame_D3D9& useTarget);

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
     * @breif   カラーバッファのアドレスを取得する
     *
     * @return  カラーバッファのアドレス
     */
    CubismOffscreenFrame_D3D9* GetColorBuffer() const;

    /**
     * @brief   画面描画に使用するクリッピングマスクのリストを取得する
     *
     * @return  画面描画に使用するクリッピングマスクのリスト
     */
    csmVector<CubismClippingContext*>* GetClippingContextListForDraw();

    /**
     *@brief  クリッピングマスクバッファのサイズを設定する
     *
     *@param  size -> クリッピングマスクバッファのサイズ
     *
     */
    void SetClippingMaskBufferSize(csmInt32 size);

    /**
     *@brief  クリッピングマスクバッファのサイズを取得する
     *
     *@return クリッピングマスクバッファのサイズ
     *
     */
    csmInt32 GetClippingMaskBufferSize() const;

    CubismOffscreenFrame_D3D9*  _colorBuffer;   ///< マスク用カラーバッファーのアドレス
    csmInt32    _currentFrameNo;         ///< マスクテクスチャに与えるフレーム番号

    csmVector<CubismRenderer::CubismTextureColor*>  _channelColors;
    csmVector<CubismClippingContext*>               _clippingContextListForMask;   ///< マスク用クリッピングコンテキストのリスト
    csmVector<CubismClippingContext*>               _clippingContextListForDraw;   ///< 描画用クリッピングコンテキストのリスト
    csmInt32                                        _clippingMaskBufferSize; ///< クリッピングマスクのバッファサイズ（初期値:256）

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
    friend class CubismClippingManager_DX9;
    friend class CubismShader_D3D9;
    friend class CubismRenderer_D3D9;

private:
    /**
     * @brief   引数付きコンストラクタ
     *
     */
    CubismClippingContext(CubismClippingManager_DX9* manager, const csmInt32* clippingDrawableIndices, csmInt32 clipCount);

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
    CubismClippingManager_DX9* GetClippingManager();

    csmBool _isUsing;                                ///< 現在の描画状態でマスクの準備が必要ならtrue
    const csmInt32* _clippingIdList;                 ///< クリッピングマスクのIDリスト
    csmInt32 _clippingIdCount;                       ///< クリッピングマスクの数
    csmInt32 _layoutChannelNo;                       ///< RGBAのいずれのチャンネルにこのクリップを配置するか(0:R , 1:G , 2:B , 3:A)
    csmRectF* _layoutBounds;                         ///< マスク用チャンネルのどの領域にマスクを入れるか(View座標-1..1, UVは0..1に直す)
    csmRectF* _allClippedDrawRect;                   ///< このクリッピングで、クリッピングされる全ての描画オブジェクトの囲み矩形（毎回更新）
    CubismMatrix44 _matrixForMask;                   ///< マスクの位置計算結果を保持する行列
    CubismMatrix44 _matrixForDraw;                   ///< 描画オブジェクトの位置計算結果を保持する行列
    csmVector<csmInt32>* _clippedDrawableIndexList;  ///< このマスクにクリップされる描画オブジェクトのリスト

    CubismClippingManager_DX9* _owner;        ///< このマスクを管理しているマネージャのインスタンス
};


/**
 * @brief   DirectX9用の描画命令を実装したクラス
 *
 */
class CubismRenderer_D3D9 : public CubismRenderer
{
    friend class CubismRenderer;
    friend class CubismClippingManager_DX9;
    friend class CubismShader_D3D9;

public:

    /**
     * @brief    レンダラを作成するための各種設定
     *           モデルロードの前に一度だけ呼び出す
     *
     * @param[in]   bufferSetNum -> 1パーツに付き作成するバッファ数
     * @param[in]   device       -> 使用デバイス
     */
    static void InitializeConstantSettings(csmUint32 bufferSetNum, LPDIRECT3DDEVICE9 device);

    /**
     *  @brief  CubismRenderStateにデフォルトの設定をセットする
     */
    static void SetDefaultRenderState();

    /**
     * @brief  Cubism描画関連の先頭で行う処理。
     *          各フレームでのCubism処理前にこれを呼んでもらう
     *
     * @param[in]   device         -> 使用デバイス
     */
    static void StartFrame(LPDIRECT3DDEVICE9 device, csmUint32 viewportWidth, csmUint32 viewportHeight);

    /**
     * @brief  Cubism描画関連の終了時行う処理。
     *          各フレームでのCubism処理前にこれを呼んでもらう
     *
     * @param[in]   device         -> 使用デバイス
     */
    static void EndFrame(LPDIRECT3DDEVICE9 device);

    /**
     * @brief  CubismRenderer_D3D9で使用するレンダーステート管理マネージャ取得
     */
    static CubismRenderState_D3D9* GetRenderStateManager();

    /**
     * @brief   レンダーステート管理マネージャ削除
     */
    static void DeleteRenderStateManager();

    /**
     * @brief   シェーダ管理機構の取得
     */
    static CubismShader_D3D9* GetShaderManager();

    /**
     * @brief   シェーダ管理機構の削除
     */
    static void DeleteShaderManager();

    /**
     * @brief   デバイスロスト・デバイス再作成時コールする
     */
    static void OnDeviceLost();

    /**
     * @brief   使用シェーダー作成
     */
    static void GenerateShader(LPDIRECT3DDEVICE9 device);


    /**
     * @brief   レンダラの初期化処理を実行する<br>
     *           引数に渡したモデルからレンダラの初期化処理に必要な情報を取り出すことができる
     *
     * @param[in]  model -> モデルのインスタンス
     */
    virtual void Initialize(Framework::CubismModel* model) override;

    /**
     * @brief   レンダリング前のフレーム先頭で呼ばれる
     */
    void FrameRenderingInit();

    /**
     * @brief   テクスチャのバインド処理<br>
     *           CubismRendererにテクスチャを設定し、CubismRenderer中でその画像を参照するためのIndex値を戻り値とする
     *
     * @param[in]   modelTextureAssign  ->  セットするモデルテクスチャのアサイン番号
     * @param[in]   texture            ->  描画に使用するテクスチャ
     *
     */
    void BindTexture(csmUint32 modelTextureAssign, const LPDIRECT3DTEXTURE9 texture);

    /**
     * @brief   バインドされたテクスチャのリストを取得する
     *
     * @return  テクスチャのアドレスのリスト
     */
    const csmMap<csmInt32, LPDIRECT3DTEXTURE9>& GetBindedTextures() const;

    /**
     * @brief  クリッピングマスクバッファのサイズを設定する<br>
     *         マスク用のFrameBufferを破棄・再作成するため処理コストは高い。
     *
     * @param[in]  size -> クリッピングマスクバッファのサイズ
     *
     */
    void SetClippingMaskBufferSize(csmInt32 size);

    /**
     * @brief  クリッピングマスクバッファのサイズを取得する
     *
     * @return クリッピングマスクバッファのサイズ
     *
     */
    csmInt32 GetClippingMaskBufferSize() const;

    /**
     * @brief  使用するシェーダの設定・コンスタントバッファの設定などを行い、描画を実行
     *
     * @param[in]  device          -> 使用デバイス
     * @param[in]  textureNo        -> 使用テクスチャ番号。基本的にCubismModel::GetDrawableTextureIndicesで返されたもの
     * @param[in]  modelColorRGBA   -> 描画カラー
     * @param[in]  colorBlendMode   -> ブレンドモード
     * @param[in]  invertedMask      -> マスク使用時のマスクの反転設定
     *
     */
    void ExecuteDraw(LPDIRECT3DDEVICE9 device, CubismVertexD3D9* vertexArray, csmUint16* indexArray,
        const csmInt32 vertexCount, const csmInt32 triangleCount,
        const csmInt32 textureNo, CubismTextureColor& modelColorRGBA, CubismBlendMode colorBlendMode, csmBool invertedMask);

protected:
    /**
     * @brief   コンストラクタ
     */
    CubismRenderer_D3D9();

    /**
     * @brief   デストラクタ
     */
    virtual ~CubismRenderer_D3D9();

    /**
     * @brief   モデルを描画する実際の処理
     *
     */
    virtual void DoDrawModel() override;


    void DrawMesh(csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
        , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
        , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask) override;

    /**
     * @brief   [オーバーライド]<br>
     *           描画オブジェクト（アートメッシュ）を描画する。<br>
     *           ポリゴンメッシュとテクスチャ番号をセットで渡す。
     *
     * @param[in]   gfxc            ->  レンダリングコンテキスト
     * @param[in]   drawableIndex   ->  描画パーツ番号
     * @param[in]   colorBlendMode  ->  カラー合成タイプ
     * @param[in]   invertedMask     ->  マスク使用時のマスクの反転使用
     *
     */
    void DrawMeshDX9(csmInt32 drawableIndex
        , csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
        , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
        , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask);


private:

    /**
     * @brief   レンダラが保持する静的なリソースを解放する<br>
     */
    static void DoStaticRelease();

    /**
     * @brief   使用シェーダーと頂点定義の削除
     */
    static void ReleaseShader();

    // Prevention of copy Constructor
    CubismRenderer_D3D9(const CubismRenderer_D3D9&);
    CubismRenderer_D3D9& operator=(const CubismRenderer_D3D9&);

    /**
     * @brief   描画開始時の追加処理。<br>
     *           モデルを描画する前にクリッピングマスクに必要な処理を実装している。
     */
    void PreDraw();

    /**
     * @brief   描画完了後の追加処理。
     *
     */
    void PostDraw();

    /**
     * @brief   モデル描画直前のステートを保持する
     */
    virtual void SaveProfile() override;

    /**
     * @brief   モデル描画直前のステートを保持する
     */
    virtual void RestoreProfile() override;

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

    /**
     * @brief   GetDrawableVertices,GetDrawableVertexUvsの内容を内部バッファへコピー
     */
    void CopyToBuffer(csmInt32 drawAssign, const csmInt32 vcount, const csmFloat32* varray, const csmFloat32* uvarray);

    csmUint32                           _drawableNum;           ///< _vertexBuffers, _indexBuffersの確保数

    CubismVertexD3D9**                  _vertexStore;           ///< 頂点をストアしておく領域
    csmUint16**                         _indexStore;            ///< インデックスをストアしておく領域

    csmInt32                            _commandBufferNum;      ///< 描画バッファを複数作成する場合の数
    csmInt32                            _commandBufferCurrent;  ///< 現在使用中のバッファ番号

    csmVector<csmInt32>                 _sortedDrawableIndexList;       ///< 描画オブジェクトのインデックスを描画順に並べたリスト

    csmMap<csmInt32, LPDIRECT3DTEXTURE9>_textures;                      ///< モデルが参照するテクスチャとレンダラでバインドしているテクスチャとのマップ

    csmVector<CubismOffscreenFrame_D3D9>_offscreenFrameBuffer;          ///< マスク描画用のフレームバッファ

    CubismClippingManager_DX9*          _clippingManager;               ///< クリッピングマスク管理オブジェクト
    CubismClippingContext*              _clippingContextBufferForMask;  ///< マスクテクスチャに描画するためのクリッピングコンテキスト
    CubismClippingContext*              _clippingContextBufferForDraw;  ///< 画面上描画するためのクリッピングコンテキスト
};

}}}}
//------------ LIVE2D NAMESPACE ------------
