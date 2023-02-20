package com.chatwaifu.translate


/**
 * Description: translate interface
 * Author: Voine
 * Date: 2023/2/20
 */
interface ITranslate {
    fun getTranslateResult(input: String, callback: (result: String?) -> Unit)
}