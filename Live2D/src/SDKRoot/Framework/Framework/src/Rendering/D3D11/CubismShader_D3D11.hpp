/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include "CubismNativeInclude_D3D11.hpp"

#include "../CubismRenderer.hpp"
#include "CubismType_D3D11.hpp"
#include "CubismFramework.hpp"
#include "Type/csmVector.hpp"

namespace Live2D { namespace Cubism { namespace Framework {
    enum ShaderNames
    {
        // SetupMask
        ShaderNames_SetupMask,

        //Normal
        ShaderNames_Normal,
        ShaderNames_NormalMasked,
        ShaderNames_NormalMaskedInverted,
        ShaderNames_NormalPremultipliedAlpha,
        ShaderNames_NormalMaskedPremultipliedAlpha,
        ShaderNames_NormalMaskedInvertedPremultipliedAlpha,

        //Add
        ShaderNames_Add,
        ShaderNames_AddMasked,
        ShaderNames_AddMaskedInverted,
        ShaderNames_AddPremultipliedAlpha,
        ShaderNames_AddMaskedPremultipliedAlpha,
        ShaderNames_AddMaskedInvertedPremultipliedAlpha,

        //Mult
        ShaderNames_Mult,
        ShaderNames_MultMasked,
        ShaderNames_MultMaskedInverted,
        ShaderNames_MultPremultipliedAlpha,
        ShaderNames_MultMaskedPremultipliedAlpha,
        ShaderNames_MultMaskedInvertedPremultipliedAlpha,

        ShaderNames_Max,
    };
}}}

//------------ LIVE2D NAMESPACE ------------
namespace Live2D { namespace Cubism { namespace Framework { namespace Rendering {

//  前方宣言
class CubismRenderer_D3D11;
class CubismClippingContext;

/**
 * @bref    D3D11シェーダ管理
 */
class CubismShaderSet
{
public:
    CubismShaderSet()
        : _vertexShader(NULL)
        , _pixelShader(NULL)
    {
    }

    ID3D11VertexShader*     _vertexShader;
    ID3D11PixelShader*      _pixelShader;
};

/**
* @brief   Cubismで使用するシェーダ管理クラス
*           CubismRenderer_D3D11のstatic変数として一つだけ実体化される
*
*/
class CubismShader_D3D11
{
    friend class CubismRenderer_D3D11;

public:

    /**
    * @brief   privateなコンストラクタ
    */
    CubismShader_D3D11();

    /**
    * @brief   privateなデストラクタ
    */
    virtual ~CubismShader_D3D11();

    /**
     * @brief   シェーダプログラムを解放する
     */
    void ReleaseShaderProgram();

    /**
     * @brief   頂点シェーダの取得
     *
     */
    ID3D11VertexShader* GetVertexShader(csmUint32 assign);

    /**
     * @brief   ピクセルシェーダの取得
     *
     */
    ID3D11PixelShader* GetPixelShader(csmUint32 assign);

    /**
     * @brief   頂点宣言のデバイスへの設定、シェーダがまだ未設定ならロード
     */
    void SetupShader(ID3D11Device* device, ID3D11DeviceContext* renderContext);

private:

    /**
     * @brief   シェーダプログラムを初期化する
     */
    void GenerateShaders(ID3D11Device* device);

    /**
     * @brief   シェーダプログラムをロード
     *
     * @param[in]   device      使用デバイス
     *
     * @return  成功時はtrue、失敗時はfalse
     */
    Csm::csmBool LoadShaderProgram(ID3D11Device* device, bool isPs, csmInt32 assign, const csmChar* entryPoint);

    csmVector<CubismShaderSet*> _shaderSets;   ///< ロードしたシェーダプログラムを保持する変数

    csmVector<ID3D11VertexShader*> _shaderSetsVS;     ///< ロードしたシェーダプログラムを保持する変数(VS)
    csmVector<ID3D11PixelShader*> _shaderSetsPS;      ///< ロードしたシェーダプログラムを保持する変数(PS)

    ID3D11InputLayout*    _vertexFormat; ///< 描画で使用する型宣言
};

}}}}
//------------ LIVE2D NAMESPACE ------------
