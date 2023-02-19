/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <cmath>
#include "Type/CubismBasicType.hpp"
#include "Math/CubismVector2.hpp"

//--------- LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework {

/**
 *@brief    数値計算などに使用するユーティリティクラス
 *
 */
class CubismMath
{
public:
    static const csmFloat32 Pi;

    /**
     *@brief    第一引数の値を最小値と最大値の範囲に収めた値を返す
     *
     *@param    value   ->  収められる値
     *@param    min     ->  範囲の最小値
     *@param    max     ->  範囲の最大値
     *@return   最小値と最大値の範囲に収めた値
     */
    static csmFloat32 RangeF(csmFloat32 value, csmFloat32 min, csmFloat32 max)
    {
        if (value < min) value = min;
        else if (value > max) value = max;
        return value;
    };

    /**
     *@brief    サイン関数の値を求める
     *
     *@param    x   ->  角度値（ラジアン）
     *@return   サイン関数sin(x)の値
     */
    static csmFloat32 SinF(csmFloat32 x)
    {
        return sinf(x);
    };

    /**
     *@brief    コサイン関数の値を求める
     *
     *@param    x   ->  角度値（ラジアン）
     *@return   コサイン関数cos(x)の値
     */
    static csmFloat32 CosF(csmFloat32 x)
    {
        return cosf(x);
    };

    /**
     *@brief    絶対値の値を求める
     *
     *@param    x  ->  絶対値を求める値
     *@return   値の絶対値
     */
    static csmFloat32 AbsF(csmFloat32 x)
    {
        return std::fabs(x);
    };

    /**
     *@brief    平方根(ルート)を求める
     *
     *@param    x  ->  平方根を求める値
     *@return   値の平方根
     */
    static csmFloat32 SqrtF(csmFloat32 x)
    {
        return sqrtf(x);
    };

    /**
     *@brief    イージング処理されたサインを求める<br>
     *           フェードイン・アウト時のイージングに利用できる
     *
     *@param    value  ->  イージングを行う値
     *@return   イージング処理されたサイン値
     */
    static csmFloat32 GetEasingSine(csmFloat32 value)
    {
        if (value < 0.0f) return 0.0f;
        else if (value > 1.0f) return 1.0f;

        return static_cast<csmFloat32>(0.5f - 0.5f * CosF(value * Pi));
    }

    /**
     * @brief  大きい方の値を返す。
     *
     * @param  l  -> 左辺の値
     * @param  r  -> 右辺の値
     * @return 大きい方の値
     */
    static csmFloat32 Max(csmFloat32 l, csmFloat32 r)
    {
        return (l > r) ? l : r;
    }

    /**
     * @brief  小さい方の値を返す。
     *
     * @param  l  -> 左辺の値
     * @param  r  -> 右辺の値
     * @return 小さい方の値
     */
    static csmFloat32 Min(csmFloat32 l, csmFloat32 r)
    {
        return (l > r) ? r : l;
    }

    /**
     * @brief   角度値をラジアン値に変換します。
     *
     * @param   degrees  ->  角度値
     * @return  角度値から変換したラジアン値
     */
    static csmFloat32 DegreesToRadian(csmFloat32 degrees);

    /**
     * @brief   ラジアン値を角度値に変換します。
     *
     * @param   radian  ->  ラジアン値
     * @return  ラジアン値から変換した角度値
     */
    static csmFloat32 RadianToDegrees(csmFloat32 radian);

    /**
     * @brief   2つのベクトルからラジアン値を求めます。
     *
     * @param   from  ->  始点ベクトル
     * @param   to    ->  終点ベクトル
     * @return  ラジアン値から求めた方向ベクトル
     */
    static csmFloat32 DirectionToRadian(CubismVector2 from, CubismVector2 to);

    /**
     * @brief   2つのベクトルから角度値を求めます。
     *
     * @param   from  ->  始点ベクトル
     * @param   to    ->  終点ベクトル
     * @return  角度値から求めた方向ベクトル
     */
    static csmFloat32 DirectionToDegrees(CubismVector2 from, CubismVector2 to);

    /**
     * @brief   ラジアン値を方向ベクトルに変換します。
     *
     * @param   totalAngle  ->  ラジアン値
     * @return  ラジアン値から変換した方向ベクトル
     */
    static CubismVector2 RadianToDirection(csmFloat32 totalAngle);

private:
    /**
     *@brief    privateコンストラクタ
     *
     */
    CubismMath();
};

}}}

//--------- LIVE2D NAMESPACE ------------
