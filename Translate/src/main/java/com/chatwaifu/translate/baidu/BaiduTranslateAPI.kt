package com.chatwaifu.translate.baidu

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Description: BaiduTranslateAPI
 * doc: https://api.fanyi.baidu.com/doc/21
 * Author: Voine
 * Date: 2023/2/20
 */
interface BaiduTranslateAPI {

    @GET("api/trans/vip/translate")
    fun getTranslateResult(
        @Query("q") input: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("appid") appid: String,
        @Query("salt") salt: String,
        @Query("sign") sign: String,
    ): Call<BaiduTranslateResponseData>
}