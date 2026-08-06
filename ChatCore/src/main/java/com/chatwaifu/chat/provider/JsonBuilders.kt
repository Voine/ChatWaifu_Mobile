package com.chatwaifu.chat.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Description: 手写请求 JSON / 读响应 JSON 的小工具。
 *
 * 为什么不用 Gson 的反射映射：请求侧的内容块是 sealed interface，各家的 `type` 字段
 * 名和取值都不同，硬套 DTO 会变成一堆只用一次的 data class + `@SerializedName`；
 * 响应侧则是「同一个字段各家叫法不同、还可能缺失」，手工 optXxx 反而更清楚。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
object JsonBuilders {

    fun jsonArrayOf(values: Collection<String>): JsonArray =
        JsonArray().apply { values.forEach { add(it) } }

    /**
     * 把 [com.chatwaifu.chat.core.ChatRequest.extras] 合并进请求体顶层。
     * 已有的同名字段会被覆盖 —— 这正是逃生阀该有的行为（用户显式指定优先）。
     */
    fun mergeExtras(root: JsonObject, extras: Map<String, Any?>) {
        if (extras.isEmpty()) return
        val gson = Gson()
        extras.forEach { (key, value) ->
            if (value == null) root.remove(key) else root.add(key, gson.toJsonTree(value))
        }
    }

    fun JsonObject.optObject(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    fun JsonObject.optArray(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    /** 空串按「没有」处理：SSE 的增量帧里经常出现 `"content": ""` 的占位帧。 */
    fun JsonObject.optString(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }

    fun JsonObject.optInt(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    fun JsonElement.optInt(): Int? = runCatching { asInt }.getOrNull()
}
