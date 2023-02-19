/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "CubismModel.hpp"
#include "Rendering/CubismRenderer.hpp"
#include "Id/CubismId.hpp"
#include "Id/CubismIdManager.hpp"

namespace Live2D { namespace Cubism { namespace Framework {

static csmInt32 IsBitSet(const csmUint8 byte, const csmUint8 mask)
{
    return ((byte & mask) == mask);
}

CubismModel::CubismModel(Core::csmModel* model)
    : _model(model)
    , _parameterValues(NULL)
    , _parameterMaximumValues(NULL)
    , _parameterMinimumValues(NULL)
    , _partOpacities(NULL)
    , _isOverwrittenModelMultiplyColors(false)
    , _isOverwrittenModelScreenColors(false)
{ }

CubismModel::~CubismModel()
{
    CSM_FREE_ALLIGNED(_model);
}

csmFloat32 CubismModel::GetParameterValue(CubismIdHandle parameterId)
{
    // 高速化のためにParameterIndexを取得できる機構になっているが、外部からの設定の時は呼び出し頻度が低いため不要
    const csmInt32 parameterIndex = GetParameterIndex(parameterId);
    return GetParameterValue(parameterIndex);
}

void CubismModel::SetParameterValue(CubismIdHandle parameteId, csmFloat32 value, csmFloat32 weight)
{
    const csmInt32 index = GetParameterIndex(parameteId);
    SetParameterValue(index, value, weight);
}

void CubismModel::AddParameterValue(CubismIdHandle parameterId, csmFloat32 value, csmFloat32 weight)
{
    const csmInt32 index = GetParameterIndex(parameterId);
    AddParameterValue(index, value, weight);
}

void CubismModel::AddParameterValue(csmInt32 parameterIndex, csmFloat32 value, csmFloat32 weight)
{
    SetParameterValue(parameterIndex, (GetParameterValue(parameterIndex) + (value * weight)));
}

void CubismModel::MultiplyParameterValue(CubismIdHandle parameterId, csmFloat32 value, csmFloat32 weight)
{
    const csmInt32 index = GetParameterIndex(parameterId);
    MultiplyParameterValue(index, value, weight);
}

void CubismModel::MultiplyParameterValue(csmInt32 parameterIndex, csmFloat32 value, csmFloat32 weight)
{
    SetParameterValue(parameterIndex, (GetParameterValue(parameterIndex) * (1.0f + (value - 1.0f) * weight)));
}

void CubismModel::Update() const
{
    // Update model.
    Core::csmUpdateModel(_model);

    // Reset dynamic drawable flags.
    Core::csmResetDrawableDynamicFlags(_model);
}

void CubismModel::SetPartOpacity(CubismIdHandle partId, csmFloat32 opacity)
{
    // 高速化のためにPartIndexを取得できる機構になっているが、外部からの設定の時は呼び出し頻度が低いため不要
    const csmInt32 index = GetPartIndex(partId);

    if (index < 0)
    {
        return; // パーツが無いのでスキップ
    }

    SetPartOpacity(index, opacity);
}

void CubismModel::SetPartOpacity(csmInt32 partIndex, csmFloat32 opacity)
{
    if (_notExistPartOpacities.IsExist(partIndex))
    {
        _notExistPartOpacities[partIndex] = opacity;
        return;
    }

    //インデックスの範囲内検知
    CSM_ASSERT(0 <= partIndex && partIndex < GetPartCount());

    _partOpacities[partIndex] = opacity;
}

csmFloat32 CubismModel::GetPartOpacity(CubismIdHandle partId)
{
    // 高速化のためにPartIndexを取得できる機構になっているが、外部からの設定の時は呼び出し頻度が低いため不要
    const csmInt32 index = GetPartIndex(partId);

    if (index < 0)
    {
        return 0; //パーツが無いのでスキップ
    }

    return GetPartOpacity(index);
}

csmFloat32 CubismModel::GetPartOpacity(csmInt32 partIndex)
{
    if (_notExistPartOpacities.IsExist(partIndex))
    {
        // モデルに存在しないパーツIDの場合、非存在パーツリストから不透明度を返す
        return _notExistPartOpacities[partIndex];
    }

    //インデックスの範囲内検知
    CSM_ASSERT(0 <= partIndex && partIndex < GetPartCount());

    return _partOpacities[partIndex];
}

csmInt32 CubismModel::GetParameterCount() const
{
    return Core::csmGetParameterCount(_model);
}

csmFloat32 CubismModel::GetParameterDefaultValue(csmUint32 parameterIndex) const
{
    return Core::csmGetParameterDefaultValues(_model)[parameterIndex];
}

csmFloat32 CubismModel::GetParameterMaximumValue(csmUint32 parameterIndex) const
{
    return Core::csmGetParameterMaximumValues(_model)[parameterIndex];
}

csmFloat32 CubismModel::GetParameterMinimumValue(csmUint32 parameterIndex) const
{
    return Core::csmGetParameterMinimumValues(_model)[parameterIndex];
}

csmInt32 CubismModel::GetParameterIndex(CubismIdHandle parameterId)
{
    csmInt32            parameterIndex;
    const csmInt32      idCount = Core::csmGetParameterCount(_model);


    for (parameterIndex = 0; parameterIndex < idCount; ++parameterIndex)
    {
        if (parameterId != _parameterIds[parameterIndex])
        {
            continue;
        }

        return parameterIndex;
    }

    // モデルに存在していない場合、非存在パラメータIDリスト内を検索し、そのインデックスを返す
    if (_notExistParameterId.IsExist(parameterId))
    {
        return _notExistParameterId[parameterId];
    }

    // 非存在パラメータIDリストにない場合、新しく要素を追加する
    parameterIndex = Core::csmGetParameterCount(_model) + _notExistParameterId.GetSize();

    _notExistParameterId[parameterId] = parameterIndex;
    _notExistParameterValues.AppendKey(parameterIndex);

    return parameterIndex;
}

csmFloat32 CubismModel::GetParameterValue(csmInt32 parameterIndex)
{
    if (_notExistParameterValues.IsExist(parameterIndex))
    {
        return _notExistParameterValues[parameterIndex];
    }

    //インデックスの範囲内検知
    CSM_ASSERT(0 <= parameterIndex && parameterIndex < GetParameterCount());

    return _parameterValues[parameterIndex];
}

void CubismModel::SetParameterValue(csmInt32 parameterIndex, csmFloat32 value, csmFloat32 weight)
{
    if (_notExistParameterValues.IsExist(parameterIndex))
    {
        _notExistParameterValues[parameterIndex] = (weight == 1)
                                                         ? value
                                                         : (_notExistParameterValues[parameterIndex] * (1 - weight)) +
                                                         (value * weight);
        return;
    }

    //インデックスの範囲内検知
    CSM_ASSERT(0 <= parameterIndex && parameterIndex < GetParameterCount());

    if (Core::csmGetParameterMaximumValues(_model)[parameterIndex] < value)
    {
        value = Core::csmGetParameterMaximumValues(_model)[parameterIndex];
    }
    if (Core::csmGetParameterMinimumValues(_model)[parameterIndex] > value)
    {
        value = Core::csmGetParameterMinimumValues(_model)[parameterIndex];
    }

    _parameterValues[parameterIndex] = (weight == 1)
                                      ? value
                                      : _parameterValues[parameterIndex] = (_parameterValues[parameterIndex] * (1 - weight)) + (value * weight);
}

csmFloat32 CubismModel::GetCanvasWidthPixel() const
{
    if (_model == NULL)
    {
        return 0.0f;
    }

    Core::csmVector2 tmpSizeInPixels;
    Core::csmVector2 tmpOriginInPixels;
    csmFloat32 tmpPixelsPerUnit;

    Core::csmReadCanvasInfo(_model, &tmpSizeInPixels, &tmpOriginInPixels, &tmpPixelsPerUnit);

    return tmpSizeInPixels.X;
}

csmFloat32 CubismModel::GetCanvasHeightPixel() const
{
    if (_model == NULL)
    {
        return 0.0f;
    }

    Core::csmVector2 tmpSizeInPixels;
    Core::csmVector2 tmpOriginInPixels;
    csmFloat32 tmpPixelsPerUnit;

    Core::csmReadCanvasInfo(_model, &tmpSizeInPixels, &tmpOriginInPixels, &tmpPixelsPerUnit);

    return tmpSizeInPixels.Y;
}

csmFloat32 CubismModel::GetPixelsPerUnit() const
{
    if (_model == NULL)
    {
        return 0.0f;
    }

    Core::csmVector2 tmpSizeInPixels;
    Core::csmVector2 tmpOriginInPixels;
    csmFloat32 tmpPixelsPerUnit;

    Core::csmReadCanvasInfo(_model, &tmpSizeInPixels, &tmpOriginInPixels, &tmpPixelsPerUnit);

    return tmpPixelsPerUnit;
}

csmFloat32 CubismModel::GetCanvasWidth() const
{
    if (_model == NULL)
    {
        return 0.0f;
    }

    Core::csmVector2 tmpSizeInPixels;
    Core::csmVector2 tmpOriginInPixels;
    csmFloat32 tmpPixelsPerUnit;

    Core::csmReadCanvasInfo(_model, &tmpSizeInPixels, &tmpOriginInPixels, &tmpPixelsPerUnit);

    return tmpSizeInPixels.X / tmpPixelsPerUnit;
}

csmFloat32 CubismModel::GetCanvasHeight() const
{
    if (_model == NULL)
    {
        return 0.0f;
    }

    Core::csmVector2 tmpSizeInPixels;
    Core::csmVector2 tmpOriginInPixels;
    csmFloat32 tmpPixelsPerUnit;

    Core::csmReadCanvasInfo(_model, &tmpSizeInPixels, &tmpOriginInPixels, &tmpPixelsPerUnit);

    return tmpSizeInPixels.Y / tmpPixelsPerUnit;
}

csmInt32 CubismModel::GetDrawableIndex(CubismIdHandle drawableId) const
{
    const csmInt32 drawableCount = Core::csmGetDrawableCount(_model);

    for (csmInt32 drawableIndex = 0; drawableIndex < drawableCount; ++drawableIndex)
    {
        if (_drawableIds[drawableIndex] == drawableId)
        {
            return drawableIndex;
        }
    }

    return -1;
}

const csmFloat32* CubismModel::GetDrawableVertices(csmInt32 drawableIndex) const
{
    return reinterpret_cast<const csmFloat32*>(GetDrawableVertexPositions(drawableIndex));
}

csmInt32 CubismModel::GetPartIndex(CubismIdHandle partId)
{
    csmInt32            partIndex;
    const csmInt32      idCount = Core::csmGetPartCount(_model);

    for (partIndex = 0; partIndex < idCount; ++partIndex)
    {
        if (partId == _partIds[partIndex])
        {
            return partIndex;
        }
    }

    const csmInt32 partCount = Core::csmGetPartCount(_model);

    // モデルに存在していない場合、非存在パーツIDリスト内にあるかを検索し、そのインデックスを返す
    if (_notExistPartId.IsExist(partId))
    {
        return _notExistPartId[partId];
    }

    // 非存在パーツIDリストにない場合、新しく要素を追加する
    partIndex = partCount + _notExistPartId.GetSize();

    _notExistPartId[partId] = partIndex;
    _notExistPartOpacities.AppendKey(partIndex);

    return partIndex;
}

void CubismModel::Initialize()
{
    CSM_ASSERT(_model);

    _parameterValues = Core::csmGetParameterValues(_model);
    _partOpacities = Core::csmGetPartOpacities(_model);
    _parameterMaximumValues = Core::csmGetParameterMaximumValues(_model);
    _parameterMinimumValues = Core::csmGetParameterMinimumValues(_model);

    {
        const csmChar** parameterIds = Core::csmGetParameterIds(_model);
        const csmInt32  parameterCount = Core::csmGetParameterCount(_model);

        _parameterIds.PrepareCapacity(parameterCount);
        for (csmInt32 i = 0; i < parameterCount; ++i)
        {
            _parameterIds.PushBack(CubismFramework::GetIdManager()->GetId(parameterIds[i]));
        }
    }

    {
        const csmChar** partIds = Core::csmGetPartIds(_model);
        const csmInt32  partCount = Core::csmGetPartCount(_model);

        _partIds.PrepareCapacity(partCount);
        for (csmInt32 i = 0; i < partCount; ++i)
        {
            _partIds.PushBack(CubismFramework::GetIdManager()->GetId(partIds[i]));
        }
    }

    {
        const csmChar** drawableIds = Core::csmGetDrawableIds(_model);
        const csmInt32  drawableCount = Core::csmGetDrawableCount(_model);

        _drawableIds.PrepareCapacity(drawableCount);
        _userMultiplyColors.PrepareCapacity(drawableCount);
        _userScreenColors.PrepareCapacity(drawableCount);

        // 乗算色
        Rendering::CubismRenderer::CubismTextureColor multiplyColor;
        multiplyColor.R = 1.0f;
        multiplyColor.G = 1.0f;
        multiplyColor.B = 1.0f;
        multiplyColor.A = 1.0f;
        DrawableColorData userMultiplyColor;
        userMultiplyColor.IsOverwritten = false;
        userMultiplyColor.Color = multiplyColor;

        // スクリーン色
        Rendering::CubismRenderer::CubismTextureColor screenColor;
        screenColor.R = 0.0f;
        screenColor.G = 0.0f;
        screenColor.B = 0.0f;
        screenColor.A = 1.0f;
        DrawableColorData userScreenColor;
        userScreenColor.IsOverwritten = false;
        userScreenColor.Color = screenColor;

        for (csmInt32 i = 0; i < drawableCount; ++i)
        {
            _drawableIds.PushBack(CubismFramework::GetIdManager()->GetId(drawableIds[i]));
            _userMultiplyColors.PushBack(userMultiplyColor);
            _userScreenColors.PushBack(userScreenColor);
        }
    }
}

CubismIdHandle CubismModel::GetDrawableId(csmInt32 drawableIndex) const
{
    const csmChar** parameterIds = Core::csmGetDrawableIds(_model);
    return CubismFramework::GetIdManager()->GetId(parameterIds[drawableIndex]);
}

csmInt32 CubismModel::GetPartCount() const
{
    const csmInt32 partCount = Core::csmGetPartCount(_model);
    return partCount;
}

const csmInt32* CubismModel::GetDrawableRenderOrders() const
{
    const csmInt32* renderOrders = Core::csmGetDrawableRenderOrders(_model);
    return renderOrders;
}

csmInt32 CubismModel::GetDrawableCount() const
{
    const csmInt32 drawableCount = Core::csmGetDrawableCount(_model);
    return drawableCount;
}

csmInt32 CubismModel::GetDrawableTextureIndices(csmInt32 drawableIndex) const
{
    return GetDrawableTextureIndex(drawableIndex);
}

csmInt32 CubismModel::GetDrawableTextureIndex(csmInt32 drawableIndex) const
{
    const csmInt32* textureIndices = Core::csmGetDrawableTextureIndices(_model);
    return textureIndices[drawableIndex];
}

csmInt32 CubismModel::GetDrawableVertexIndexCount(csmInt32 drawableIndex) const
{
    const csmInt32* indexCounts = Core::csmGetDrawableIndexCounts(_model);
    return indexCounts[drawableIndex];
}

csmInt32 CubismModel::GetDrawableVertexCount(csmInt32 drawableIndex) const
{
    const csmInt32* vertexCounts = Core::csmGetDrawableVertexCounts(_model);
    return vertexCounts[drawableIndex];
}

const csmUint16* CubismModel::GetDrawableVertexIndices(csmInt32 drawableIndex) const
{
    const csmUint16** indicesArray = Core::csmGetDrawableIndices(_model);
    return indicesArray[drawableIndex];
}

const Core::csmVector2* CubismModel::GetDrawableVertexPositions(csmInt32 drawableIndex) const
{
    const Core::csmVector2** verticesArray = Core::csmGetDrawableVertexPositions(_model);
    return verticesArray[drawableIndex];
}

const Core::csmVector2* CubismModel::GetDrawableVertexUvs(csmInt32 drawableIndex) const
{
    const Core::csmVector2** uvsArray = Core::csmGetDrawableVertexUvs(_model);
    return uvsArray[drawableIndex];
}

csmFloat32 CubismModel::GetDrawableOpacity(csmInt32 drawableIndex) const
{
    const csmFloat32* opacities = Core::csmGetDrawableOpacities(_model);
    return opacities[drawableIndex];
}

Core::csmVector4 CubismModel::GetDrawableMultiplyColor(csmInt32 drawableIndex) const
{
    const Core::csmVector4* multiplyColors = Core::csmGetDrawableMultiplyColors(_model);
    return multiplyColors[drawableIndex];
}

Core::csmVector4 CubismModel::GetDrawableScreenColor(csmInt32 drawableIndex) const
{
    const Core::csmVector4* screenColors = Core::csmGetDrawableScreenColors(_model);
    return screenColors[drawableIndex];
}

csmInt32 CubismModel::GetDrawableCulling(csmInt32 drawableIndex) const
{
    const Core::csmFlags* constantFlags = Core::csmGetDrawableConstantFlags(_model);
    return !IsBitSet(constantFlags[drawableIndex], Core::csmIsDoubleSided);
}

csmBool CubismModel::GetDrawableDynamicFlagIsVisible(csmInt32 drawableIndex) const
{
    const Core::csmFlags* dynamicFlags = Core::csmGetDrawableDynamicFlags(_model);
    return IsBitSet(dynamicFlags[drawableIndex], Core::csmIsVisible)!=0 ? true : false;
}

csmBool CubismModel::GetDrawableDynamicFlagVisibilityDidChange(csmInt32 drawableIndex) const
{
    const Core::csmFlags* dynamicFlags = Core::csmGetDrawableDynamicFlags(_model);
    return IsBitSet(dynamicFlags[drawableIndex], Core::csmVisibilityDidChange)!=0 ? true : false;
}

csmBool CubismModel::GetDrawableDynamicFlagOpacityDidChange(csmInt32 drawableIndex) const
{
    const Core::csmFlags* dynamicFlags = Core::csmGetDrawableDynamicFlags(_model);
    return IsBitSet(dynamicFlags[drawableIndex], Core::csmOpacityDidChange) != 0 ? true : false;
}

csmBool CubismModel::GetDrawableDynamicFlagDrawOrderDidChange(csmInt32 drawableIndex) const
{
    const Core::csmFlags* dynamicFlags = Core::csmGetDrawableDynamicFlags(_model);
    return IsBitSet(dynamicFlags[drawableIndex], Core::csmDrawOrderDidChange) != 0 ? true : false;
}

csmBool CubismModel::GetDrawableDynamicFlagRenderOrderDidChange(csmInt32 drawableIndex) const
{
    const Core::csmFlags* dynamicFlags = Core::csmGetDrawableDynamicFlags(_model);
    return IsBitSet(dynamicFlags[drawableIndex], Core::csmRenderOrderDidChange) != 0 ? true : false;
}

csmBool CubismModel::GetDrawableDynamicFlagVertexPositionsDidChange(csmInt32 drawableIndex) const
{
    const Core::csmFlags* dynamicFlags = Core::csmGetDrawableDynamicFlags(_model);
    return IsBitSet(dynamicFlags[drawableIndex], Core::csmVertexPositionsDidChange) != 0 ? true : false;
}

csmBool CubismModel::GetDrawableDynamicFlagBlendColorDidChange(csmInt32 drawableIndex) const
{
    const Core::csmFlags* dynamicFlags = Core::csmGetDrawableDynamicFlags(_model);
    return IsBitSet(dynamicFlags[drawableIndex], Core::csmBlendColorDidChange) != 0 ? true : false;
}

Rendering::CubismRenderer::CubismBlendMode CubismModel::GetDrawableBlendMode(csmInt32 drawableIndex) const
{
    const csmUint8* constantFlags = Core::csmGetDrawableConstantFlags(_model);
    return (IsBitSet(constantFlags[drawableIndex], Core::csmBlendAdditive))
               ? Rendering::CubismRenderer::CubismBlendMode_Additive
               : (IsBitSet(constantFlags[drawableIndex], Core::csmBlendMultiplicative))
               ? Rendering::CubismRenderer::CubismBlendMode_Multiplicative
               : Rendering::CubismRenderer::CubismBlendMode_Normal;
}

csmBool CubismModel::GetDrawableInvertedMask(csmInt32 drawableIndex) const
{
    const csmUint8* constantFlags = Core::csmGetDrawableConstantFlags(_model);
    return IsBitSet(constantFlags[drawableIndex], Core::csmIsInvertedMask) != 0 ? true : false;
}

const csmInt32** CubismModel::GetDrawableMasks() const
{
    const csmInt32** masks = Core::csmGetDrawableMasks(_model);
    return masks;
}

const csmInt32* CubismModel::GetDrawableMaskCounts() const
{
    const csmInt32* maskCounts = Core::csmGetDrawableMaskCounts(_model);
    return maskCounts;
}

void CubismModel::LoadParameters()
{
    csmInt32       parameterCount = Core::csmGetParameterCount(_model);
    const csmInt32 savedParameterCount = static_cast<csmInt32>(_savedParameters.GetSize());

    if (parameterCount > savedParameterCount)
    {
        parameterCount = savedParameterCount;
    }

    for (csmInt32 i = 0; i < parameterCount; ++i)
    {
        _parameterValues[i] = _savedParameters[i];
    }
}

void CubismModel::SaveParameters()
{
    const csmInt32 parameterCount = Core::csmGetParameterCount(_model);
    const csmInt32 savedParameterCount = static_cast<csmInt32>(_savedParameters.GetSize());

    for (csmInt32 i = 0; i < parameterCount; ++i)
    {
        if (i < savedParameterCount)
        {
            _savedParameters[i] = _parameterValues[i];
        }
        else
        {
            _savedParameters.PushBack(_parameterValues[i], false);
        }
    }
}

Rendering::CubismRenderer::CubismTextureColor CubismModel::GetMultiplyColor(csmInt32 drawableIndex) const
{
    if (GetOverwriteFlagForModelMultiplyColors() || GetOverwriteFlagForDrawableMultiplyColors(drawableIndex))
    {
        return _userMultiplyColors[drawableIndex].Color;
    }

    const Core::csmVector4 color = GetDrawableMultiplyColor(drawableIndex);

    return Rendering::CubismRenderer::CubismTextureColor(color.X, color.Y, color.Z, color.W);
}

Rendering::CubismRenderer::CubismTextureColor CubismModel::GetScreenColor(csmInt32 drawableIndex) const
{
    if (GetOverwriteFlagForModelScreenColors() || GetOverwriteFlagForDrawableScreenColors(drawableIndex))
    {
        return _userScreenColors[drawableIndex].Color;
    }

    const Core::csmVector4 color = GetDrawableScreenColor(drawableIndex);
    return Rendering::CubismRenderer::CubismTextureColor(color.X, color.Y, color.Z, color.W);
}

void CubismModel::SetMultiplyColor(csmInt32 drawableIndex, const Rendering::CubismRenderer::CubismTextureColor& color)
{
    SetMultiplyColor(drawableIndex, color.R, color.G, color.B, color.A);
}

void CubismModel::SetMultiplyColor(csmInt32 drawableIndex, csmFloat32 r, csmFloat32 g, csmFloat32 b, csmFloat32 a)
{
    _userMultiplyColors[drawableIndex].Color.R = r;
    _userMultiplyColors[drawableIndex].Color.G = g;
    _userMultiplyColors[drawableIndex].Color.B = b;
    _userMultiplyColors[drawableIndex].Color.A = a;
}

void CubismModel::SetScreenColor(csmInt32 drawableIndex, const Rendering::CubismRenderer::CubismTextureColor& color)
{
    SetScreenColor(drawableIndex, color.R, color.G, color.B, color.A);
}

void CubismModel::SetScreenColor(csmInt32 drawableIndex, csmFloat32 r, csmFloat32 g, csmFloat32 b, csmFloat32 a)
{
    _userScreenColors[drawableIndex].Color.R = r;
    _userScreenColors[drawableIndex].Color.G = g;
    _userScreenColors[drawableIndex].Color.B = b;
    _userScreenColors[drawableIndex].Color.A = a;
}

csmBool CubismModel::GetOverwriteFlagForModelMultiplyColors() const
{
    return _isOverwrittenModelMultiplyColors;
}

csmBool CubismModel::GetOverwriteFlagForModelScreenColors() const
{
    return _isOverwrittenModelScreenColors;
}

void CubismModel::SetOverwriteFlagForModelMultiplyColors(csmBool value)
{
    _isOverwrittenModelMultiplyColors = value;
}

void CubismModel::SetOverwriteFlagForModelScreenColors(csmBool value)
{
    _isOverwrittenModelScreenColors = value;
}

csmBool CubismModel::GetOverwriteFlagForDrawableMultiplyColors(csmInt32 drawableIndex) const
{
    return _userMultiplyColors[drawableIndex].IsOverwritten;
}

csmBool CubismModel::GetOverwriteFlagForDrawableScreenColors(csmInt32 drawableIndex) const
{
    return _userScreenColors[drawableIndex].IsOverwritten;
}

void CubismModel::SetOverwriteFlagForDrawableMultiplyColors(csmUint32 drawableIndex, csmBool value)
{
    _userMultiplyColors[drawableIndex].IsOverwritten = value;
}

void CubismModel::SetOverwriteFlagForDrawableScreenColors(csmUint32 drawableIndex, csmBool value)
{
    _userScreenColors[drawableIndex].IsOverwritten = value;
}

Core::csmModel* CubismModel::GetModel() const
{
    return _model;
}

csmBool CubismModel::IsUsingMasking() const
{
    for (csmInt32 d = 0; d < Core::csmGetDrawableCount(_model); ++d)
    {
        if (Core::csmGetDrawableMaskCounts(_model)[d] <= 0)
        {
            continue;
        }
        return true;
    }

    return false;
}

}}}
