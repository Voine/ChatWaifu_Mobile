/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismMath.hpp"

namespace Live2D {namespace Cubism {namespace Framework {

const csmFloat32 CubismMath::Pi = 3.1415926535897932384626433832795f;

csmFloat32 CubismMath::DegreesToRadian(csmFloat32 degrees)
{
    return (degrees / 180.0f) * Pi;
}

csmFloat32 CubismMath::RadianToDegrees(csmFloat32 radian)
{
    return (radian * 180.0f) / Pi;
}

csmFloat32 CubismMath::DirectionToRadian(CubismVector2 from, CubismVector2 to)
{
    csmFloat32 q1;
    csmFloat32 q2;
    csmFloat32 ret;

    q1 = atan2f(to.Y, to.X);
    q2 = atan2f(from.Y, from.X);

    ret = q1 - q2;

    while (ret < -Pi)
    {
        ret += Pi * 2.0f;
    }

    while (ret > Pi)
    {
        ret -= Pi * 2.0f;
    }

    return ret;
}

csmFloat32 CubismMath::DirectionToDegrees(CubismVector2 from, CubismVector2 to)
{
    csmFloat32 radian;
    csmFloat32 degree;

    radian = DirectionToRadian(from, to);
    degree = RadianToDegrees(radian);

    if ((to.X - from.X) > 0.0f)
    {
        degree = -degree;
    }

    return degree;
}

CubismVector2 CubismMath::RadianToDirection(csmFloat32 totalAngle)
{
    CubismVector2 ret;

    ret.X = CubismMath::SinF(totalAngle);
    ret.Y = CubismMath::CosF(totalAngle);

    return ret;
}

}}}
