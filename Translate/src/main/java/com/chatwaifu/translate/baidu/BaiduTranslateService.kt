package com.chatwaifu.translate.baidu

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.chatwaifu.translate.ITranslate
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Description: BaiduTranslateService
 * Author: Voine
 * Date: 2023/2/20
 */
class BaiduTranslateService(
    var context: Context,
    var fromLanguage: String = "auto",
    var toLanguage: String = "jp",
    var salt: String = "114514",
    var appid: String? = null,
    var privateKey: String? = null
) : ITranslate {
    companion object {
        private const val BASE_URL = "https://fanyi-api.baidu.com/"
        private const val TAG = "BaiduTranslateService"
    }

    private var sign: String? = null

    private val baiduNetService: BaiduTranslateAPI by lazy {
        val retrofit = Retrofit.Builder().run {
            addConverterFactory(GsonConverterFactory.create())
            baseUrl(BASE_URL)
            client(OkHttpClient())
            build()
        }
        retrofit.create(BaiduTranslateAPI::class.java)
    }


    override fun getTranslateResult(input: String, callback: (result: String?) -> Unit) {
        if (appid == null || privateKey == null) {
            return callback.invoke(null)
        }
        val startIndex  = input.indexOfFirst { it.isLetterOrDigit() }
        val endIndex = input.indexOfLast { it.isLetterOrDigit() }
        val realInput = input.substring(startIndex, endIndex + 1).replace("\n","").trimIndent()

        sign = DigestUtils.md5("$appid$realInput$salt$privateKey")
        val call = baiduNetService.getTranslateResult(
            realInput,
            fromLanguage,
            toLanguage,
            appid!!,
            salt,
            sign!!
        )
        call.enqueue(object : Callback<BaiduTranslateResponseData> {
            override fun onResponse(
                call: Call<BaiduTranslateResponseData>,
                response: Response<BaiduTranslateResponseData>
            ) {
                Log.d(TAG,"receive response $response")
                callback.invoke(response.body()?.trans_result?.firstOrNull()?.dst)
            }

            override fun onFailure(call: Call<BaiduTranslateResponseData>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(context, "translate error ...", Toast.LENGTH_SHORT)
                callback.invoke(null)
            }
        })
    }
}