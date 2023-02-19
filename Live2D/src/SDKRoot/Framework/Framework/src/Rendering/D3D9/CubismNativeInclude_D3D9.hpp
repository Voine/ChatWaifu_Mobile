/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismFrameworkConfig.hpp"

#ifdef CSM_DEBUG
// d3dヘッダの前にこれを定義するとデバッグ情報が増える 必要に応じてOn
#define D3D_DEBUG_INFO
#endif

#include <d3d9.h>
#include <d3dx9.h>
