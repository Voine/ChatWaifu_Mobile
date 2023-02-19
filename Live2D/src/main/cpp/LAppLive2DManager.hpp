/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <CubismFramework.hpp>
#include <Math/CubismMatrix44.hpp>
#include <Type/csmVector.hpp>

class LAppModel;

/**
* @brief サンプルアプリケーションにおいてCubismModelを管理するクラス<br>
*         モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
*
*/
class LAppLive2DManager
{

public:
    /**
    * @brief   クラスのインスタンス（シングルトン）を返す。<br>
    *           インスタンスが生成されていない場合は内部でインスタンを生成する。
    *
    * @return  クラスのインスタンス
    */
    static LAppLive2DManager* GetInstance();

    /**
    * @brief   クラスのインスタンス（シングルトン）を解放する。
    *
    */
    static void ReleaseInstance();

    /**
    * @brief   現在のシーンで保持しているモデルを返す
    *
    * @param[in]   no  モデルリストのインデックス値
    * @return      モデルのインスタンスを返す。インデックス値が範囲外の場合はNULLを返す。
    */
    LAppModel* GetModel(Csm::csmUint32 no) const;

    /**
    * @brief   現在のシーンで保持しているすべてのモデルを解放する
    *
    */
    void ReleaseAllModel();

    /**
    * @brief   画面をドラッグしたときの処理
    *
    * @param[in]   x   画面のX座標
    * @param[in]   y   画面のY座標
    */
    void OnDrag(Csm::csmFloat32 x, Csm::csmFloat32 y) const;

    /**
    * @brief   画面をタップしたときの処理
    *
    * @param[in]   x   画面のX座標
    * @param[in]   y   画面のY座標
    */
    void OnTap(Csm::csmFloat32 x, Csm::csmFloat32 y);

    /**
    * @brief   画面を更新するときの処理
    *          モデルの更新処理および描画処理を行う
    */
    void OnUpdate() const;

    /**
    * @brief   次のシーンに切り替える<br>
    *           サンプルアプリケーションではモデルセットの切り替えを行う。
    */
    void NextScene();

    /**
    * @brief   シーンを切り替える<br>
    *           サンプルアプリケーションではモデルセットの切り替えを行う。
    */
    void ChangeScene(Csm::csmInt32 index);

    /**
    * @brief   シーンインデックスの取得
    * @return  シーンインデックスを返す
    */
    Csm::csmInt32 GetSceneIndex() { return _sceneIndex; }

    /**
     * @brief   モデル個数を得る
     * @return  所持モデル個数
     */
    Csm::csmUint32 GetModelNum() const;

    /**
     * @brief   viewMatrixをセットする
     */
    void SetViewMatrix(Live2D::Cubism::Framework::CubismMatrix44* m);

private:
    /**
    * @brief  コンストラクタ
    */
    LAppLive2DManager();

    /**
    * @brief  デストラクタ
    */
    virtual ~LAppLive2DManager();

    Csm::CubismMatrix44*        _viewMatrix; ///< モデル描画に用いるView行列
    Csm::csmVector<LAppModel*>  _models; ///< モデルインスタンスのコンテナ
    Csm::csmInt32               _sceneIndex; ///< 表示するシーンのインデックス値
};
