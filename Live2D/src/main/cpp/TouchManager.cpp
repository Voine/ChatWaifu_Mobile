/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "TouchManager.hpp"
#include <math.h>

TouchManager::TouchManager()
    : _startY(0.0f)
    , _startX(0.0f)
    , _lastX(0.0f)
    , _lastY(0.0f)
    , _lastX1(0.0f)
    , _lastY1(0.0f)
    , _lastX2(0.0f)
    , _lastY2(0.0f)
    , _lastTouchDistance(0.0f)
    , _deltaX(0.0f)
    , _deltaY(0.0f)
    , _scale(1.0f)
    , _touchSingle(false)
    , _flipAvailable(false)
{ }

void TouchManager::TouchesBegan(float deviceX, float deviceY)
{
    _lastX = deviceX;
    _lastY = deviceY;
    _startX = deviceX;
    _startY = deviceY;
    _lastTouchDistance = -1.0f;
    _flipAvailable = true;
    _touchSingle = true;
}

void TouchManager::TouchesMoved(float deviceX, float deviceY)
{
    _lastX = deviceX;
    _lastY = deviceY;
    _lastTouchDistance = -1.0f;
    _touchSingle = true;
}

void TouchManager::TouchesMoved(float deviceX1, float deviceY1, float deviceX2, float deviceY2)
{
    float distance = CalculateDistance(deviceX1, deviceY1, deviceX2, deviceY2);
    float centerX = (deviceX1 + deviceX2) * 0.5f;
    float centerY = (deviceY1 + deviceY2) * 0.5f;

    if (_lastTouchDistance > 0.0f)
    {
        _scale = powf(distance / _lastTouchDistance, 0.75f);
        _deltaX = CalculateMovingAmount(deviceX1 - _lastX1, deviceX2 - _lastX2);
        _deltaY = CalculateMovingAmount(deviceY1 - _lastY1, deviceY2 - _lastY2);
    }
    else
    {
        _scale = 1.0f;
        _deltaX = 0.0f;
        _deltaY = 0.0f;
    }

    _lastX = centerX;
    _lastY = centerY;
    _lastX1 = deviceX1;
    _lastY1 = deviceY1;
    _lastX2 = deviceX2;
    _lastY2 = deviceY2;
    _lastTouchDistance = distance;
    _touchSingle = false;
}

float TouchManager::GetFlickDistance() const
{
    return CalculateDistance(_startX, _startY, _lastX, _lastY);
}

float TouchManager::CalculateDistance(float x1, float y1, float x2, float y2) const
{
    return sqrtf((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
}

float TouchManager::CalculateMovingAmount(float v1, float v2)
{
    if ((v1 > 0.0f) != (v2 > 0.0f))
    {
        return 0.0f;
    }

    float sign = v1 > 0.0f ? 1.0f : -1.0f;
    float absoluteValue1 = fabsf(v1);
    float absoluteValue2 = fabsf(v2);
    return sign * ((absoluteValue1 < absoluteValue2) ? absoluteValue1 : absoluteValue2);
}
