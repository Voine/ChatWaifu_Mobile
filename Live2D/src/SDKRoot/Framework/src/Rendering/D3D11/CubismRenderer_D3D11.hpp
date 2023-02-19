/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismNativeInclude_D3D11.hpp"

#include "../CubismRenderer.hpp"
#include "CubismFramework.hpp"
#include "Type/csmVector.hpp"
#include "Type/csmRectF.hpp"
#include "Type/csmMap.hpp"
#include "Rendering/D3D11/CubismOffscreenSurface_D3D11.hpp"
#include "CubismRenderState_D3D11.hpp"

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

//  前方宣言
class CubismRenderer_D3D11;
class CubismShader_D3D11;
class CubismClippingContext;

/**
 * @brief DirectX::XMMATRIXに変換
 */
DirectX::XMMATRIX ConvertToD3DX(CubismMatrix44& mtx);

/**
 * @brief  クリッピングマスクの処理を実行するクラス
 *
 */
class CubismClippingManager_D3D11
{
    friend class CubismShader_D3D11;
    friend class CubismRenderer_D3D11;

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
    CubismClippingManager_D3D11();

    /**
     * @brief    デストラクタ
     */
    virtual ~CubismClippingManager_D3D11();

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
    void SetupClippingContext(ID3D11DeviceContext* renderContext, CubismModel& model, CubismRenderer_D3D11* renderer, CubismOffscreenFrame_D3D11& useTarget);

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
    CubismOffscreenFrame_D3D11* GetColorBuffer() const;

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

    CubismOffscreenFrame_D3D11*  _colorBuffer;   ///< マスク用カラーバッファーのアドレス
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
    friend class CubismClippingManager_D3D11;
    friend class CubismShader_D3D11;
    friend class CubismRenderer_D3D11;

private:
    /**
     * @brief   引数付きコンストラクタ
     *
     */
    CubismClippingContext(CubismClippingManager_D3D11* manager, const csmInt32* clippingDrawableIndices, csmInt32 clipCount);

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
    CubismClippingManager_D3D11* GetClippingManager();

    csmBool _isUsing;                                ///< 現在の描画状態でマスクの準備が必要ならtrue
    const csmInt32* _clippingIdList;                 ///< クリッピングマスクのIDリスト
    csmInt32 _clippingIdCount;                       ///< クリッピングマスクの数
    csmInt32 _layoutChannelNo;                       ///< RGBAのいずれのチャンネルにこのクリップを配置するか(0:R , 1:G , 2:B , 3:A)
    csmRectF* _layoutBounds;                         ///< マスク用チャンネルのどの領域にマスクを入れるか(View座標-1..1, UVは0..1に直す)
    csmRectF* _allClippedDrawRect;                   ///< このクリッピングで、クリッピングされる全ての描画オブジェクトの囲み矩形（毎回更新）
    CubismMatrix44 _matrixForMask;                   ///< マスクの位置計算結果を保持する行列
    CubismMatrix44 _matrixForDraw;                   ///< 描画オブジェクトの位置計算結果を保持する行列
    csmVector<csmInt32>* _clippedDrawableIndexList;  ///< このマスクにクリップされる描画オブジェクトのリスト

    CubismClippingManager_D3D11* _owner;        ///< このマスクを管理しているマネージャのインスタンス
};


/**
 * @brief   DirectX11用の描画命令を実装したクラス
 *
 */
class CubismRenderer_D3D11 : public CubismRenderer
{
    friend class CubismRenderer;
    friend class CubismClippingManager_D3D11;
    friend class CubismShader_D3D11;

public:

    /**
     * @brief    レンダラを作成するための各種設定
     *           モデルロードの前に一度だけ呼び出す
     *
     * @param[in]   bufferSetNum -> 描画コマンドバッファ数
     * @param[in]   device       -> 使用デバイス
     */
    static void InitializeConstantSettings(csmUint32 bufferSetNum, ID3D11Device* device);

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
    static void StartFrame(ID3D11Device* device, ID3D11DeviceContext* renderContext, csmUint32 viewportWidth, csmUint32 viewportHeight);

    /**
     * @brief  Cubism描画関連の終了時行う処理。
     *          各フレームでのCubism処理前にこれを呼んでもらう
     *
     * @param[in]   device         -> 使用デバイス
     */
    static void EndFrame(ID3D11Device* device);

    /**
     * @brief  CubismRenderer_D3D11で使用するレンダーステート管理マネージャ取得
     */
    static CubismRenderState_D3D11* GetRenderStateManager();

    /**
     * @brief   レンダーステート管理マネージャ削除
     */
    static void DeleteRenderStateManager();

    /**
     * @brief   シェーダ管理機構の取得
     */
    static CubismShader_D3D11* GetShaderManager();

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
    static void GenerateShader(ID3D11Device* device);

    /**
     * @brief   使用中デバイス取得
     */
    static ID3D11Device* GetCurrentDevice();

    /**
     * @brief   レンダラの初期化処理を実行する<br>
     *           引数に渡したモデルからレンダラの初期化処理に必要な情報を取り出すことができる
     *
     * @param[in]  model -> モデルのインスタンス
     */
    virtual void Initialize(Framework::CubismModel* model) override;

    /**
     * @brief   OpenGLテクスチャのバインド処理<br>
     *           CubismRendererにテクスチャを設定し、CubismRenderer中でその画像を参照するためのIndex値を戻り値とする
     *
     * @param[in]   modelTextureAssign  ->  セットするモデルテクスチャのアサイン番号
     * @param[in]   textureView            ->  描画に使用するテクスチャ
     *
     */
    void BindTexture(csmUint32 modelTextureAssign, ID3D11ShaderResourceView* textureView);

    /**
     * @brief   OpenGLにバインドされたテクスチャのリストを取得する
     *
     * @return  テクスチャのアドレスのリスト
     */
    const csmMap<csmInt32, ID3D11ShaderResourceView*>& GetBindedTextures() const;

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
     * @param[in]  invertedMask      -> マスク使用時のマスクの反転使用
     *
     */
    void ExecuteDraw(ID3D11Device* device, ID3D11DeviceContext* renderContext,
        ID3D11Buffer* vertexBuffer, ID3D11Buffer* indexBuffer, ID3D11Buffer* constantBuffer,
        const csmInt32 indexCount,
        const csmInt32 textureNo, CubismTextureColor& modelColorRGBA, CubismBlendMode colorBlendMode, csmBool invertedMask);

protected:
    /**
     * @brief   コンストラクタ
     */
    CubismRenderer_D3D11();

    /**
     * @brief   デストラクタ
     */
    virtual ~CubismRenderer_D3D11();

    /**
     * @brief   モデルを描画する実際の処理
     *
     */
    virtual void DoDrawModel() override;


    void DrawMesh(csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
        , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
        , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask) override;

    /**
     * @brief   描画オブジェクト（アートメッシュ）を描画する。<br>
     *           ポリゴンメッシュとテクスチャ番号をセットで渡す。
     *
     * @param[in]   drawableIndex   ->  描画パーツ番号
     * @param[in]   textureNo       ->  テクスチャ番号
     * @param[in]   indexCount      ->  インデックス数
     * @param[in]   vertexCount     ->  頂点数
     * @param[in]   indexArray      ->  インデックス配列
     * @param[in]   vertexArray     ->  頂点配列
     * @param[in]   uvArray         ->  UV配列
     * @param[in]   opacity         ->  描画透明度
     * @param[in]   colorBlendMode  ->  カラー合成タイプ
     * @param[in]   invertedMask     ->  マスク使用時のマスクの反転使用
     *
     */
    void DrawMeshDX11(csmInt32 drawableIndex
        , csmInt32 textureNo, csmInt32 indexCount, csmInt32 vertexCount
        , csmUint16* indexArray, csmFloat32* vertexArray, csmFloat32* uvArray
        , csmFloat32 opacity, CubismBlendMode colorBlendMode, csmBool invertedMask);


private:

    /**
     * @brief   レンダラが保持する静的なリソースを解放する
     */
    static void DoStaticRelease();

    /**
     * @brief   使用シェーダーと頂点定義の削除
     */
    static void ReleaseShader();


    // Prevention of copy Constructor
    CubismRenderer_D3D11(const CubismRenderer_D3D11&);
    CubismRenderer_D3D11& operator=(const CubismRenderer_D3D11&);

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
     * @brief   GetDrawableVertices,GetDrawableVertexUvsの内容をバッファへコピー
     */
    void CopyToBuffer(ID3D11DeviceContext* renderContext, csmInt32 drawAssign, const csmInt32 vcount, const csmFloat32* varray, const csmFloat32* uvarray);

    ID3D11Buffer***                     _vertexBuffers;         ///< 描画バッファ カラー無し、位置+UV
    ID3D11Buffer***                     _indexBuffers;          ///< インデックスのバッファ
    ID3D11Buffer***                     _constantBuffers;       ///< 定数のバッファ
    csmUint32                           _drawableNum;           ///< _vertexBuffers, _indexBuffersの確保数

    csmInt32                            _commandBufferNum;
    csmInt32                            _commandBufferCurrent;

    csmVector<csmInt32>                 _sortedDrawableIndexList;       ///< 描画オブジェクトのインデックスを描画順に並べたリスト

    csmMap<csmInt32, ID3D11ShaderResourceView*> _textures;              ///< モデルが参照するテクスチャとレンダラでバインドしているテクスチャとのマップ

    csmVector<CubismOffscreenFrame_D3D11>   _offscreenFrameBuffer;      ///< マスク描画用のフレームバッファ

    CubismClippingManager_D3D11*        _clippingManager;               ///< クリッピングマスク管理オブジェクト
    CubismClippingContext*              _clippingContextBufferForMask;  ///< マスクテクスチャに描画するためのクリッピングコンテキスト
    CubismClippingContext*              _clippingContextBufferForDraw;  ///< 画面上描画するためのクリッピングコンテキスト
};

}}}}
//------------ LIVE2D NAMESPACE ------------
