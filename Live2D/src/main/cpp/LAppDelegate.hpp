/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include "LAppAllocator.hpp"

class LAppView;
class LAppTextureManager;

/**
* @brief   アプリケーションクラス。
*   Cubism SDK の管理を行う。
*/
class LAppDelegate
{
public:
    /**
    * @brief   クラスのインスタンス（シングルトン）を返す。<br>
    *           インスタンスが生成されていない場合は内部でインスタンを生成する。
    *
    * @return  クラスのインスタンス
    */
    static LAppDelegate* GetInstance();

    /**
    * @brief   クラスのインスタンス（シングルトン）を解放する。
    *
    */
    static void ReleaseInstance();

    /**
    * @brief JavaのActivityのOnStart()のコールバック関数。
    */
    void OnStart();

    /**
    * @brief JavaのActivityのOnPause()のコールバック関数。
    */
    void OnPause();

    /**
    * @brief JavaのActivityのOnStop()のコールバック関数。
    */
    void OnStop();

    /**
    * @brief JavaのActivityのOnDestroy()のコールバック関数。
    */
    void OnDestroy();

    /**
    * @brief   JavaのGLSurfaceviewのOnSurfaceCreate()のコールバック関数。
    */
    void OnSurfaceCreate();

    /**
     * @brief JavaのGLSurfaceviewのOnSurfaceChanged()のコールバック関数。
     * @param width
     * @param height
     */
    void OnSurfaceChanged(float width, float height);

    /**
    * @brief   実行処理。
    */
    void Run();

    /**
    * @brief Touch開始。
    *
    * @param[in] x x座標
    * @param[in] y x座標
    */
    void OnTouchBegan(double x, double y);

    /**
    * @brief Touch終了。
    *
    * @param[in] x x座標
    * @param[in] y x座標
    */
    void OnTouchEnded(double x, double y);

    /**
    * @brief Touch移動。
    *
    * @param[in] x x座標
    * @param[in] y x座標
    */
    void OnTouchMoved(double x, double y);

    /**
    * @brief シェーダーを登録する。
    */
    GLuint CreateShader();

    /**
    * @brief テクスチャマネージャーの取得
    */
    LAppTextureManager* GetTextureManager() { return _textureManager; }

    /**
    * @brief ウインドウ幅の設定
    */
    int GetWindowWidth() { return _width; }

    /**
    * @brief ウインドウ高さの取得
    */
    int GetWindowHeight() { return _height; }

    /**
    * @brief   アプリケーションを非アクティブにする。
    */
    void DeActivateApp() { _isActive = false; }

    /**
    * @brief   View情報を取得する。
    */
    LAppView* GetView() { return _view; }

private:
    /**
    * @brief   コンストラクタ
    */
    LAppDelegate();

    /**
    * @brief   デストラクタ
    */
    ~LAppDelegate();

    /**
    * @brief   Cubism SDK の初期化
    */
    void InitializeCubism();

    LAppAllocator _cubismAllocator;              ///< Cubism SDK Allocator
    Csm::CubismFramework::Option _cubismOption;  ///< Cubism SDK Option
    LAppTextureManager* _textureManager;         ///< テクスチャマネージャー
    LAppView* _view;                             ///< View情報
    int _width;                                  ///< Windowの幅
    int _height;                                 ///< windowの高さ
    int _SceneIndex;                             ///< モデルシーンインデックス
    bool _captured;                              ///< クリックしているか
    bool _isActive;                              ///< アプリがアクティブ状態なのか
    float _mouseY;                               ///< マウスY座標
    float _mouseX;                               ///< マウスX座標
};
