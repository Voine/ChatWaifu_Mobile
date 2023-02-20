package com.chatwaifu.translate.baidu

/**
 * Description: BaiduTranslateResponseData
 * Author: Voine
 * Date: 2023/2/20
 */
data class BaiduTranslateResponseData(
    var from: String?,
    var to: String?,
    var trans_result: List<ResultBean>?,
    var error_code: String?,
    var error_msg: String?,
)

data class ResultBean(
    var src: String?,
    var dst: String?
)
