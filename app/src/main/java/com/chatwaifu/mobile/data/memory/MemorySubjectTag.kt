package com.chatwaifu.mobile.data.memory

import com.chatwaifu.log.MemorySubject

/** 抽取 prompt 里 `about` 字段的取值。和枚举名分开：prompt 里的词改了不该动库里存的值 */
internal val MemorySubject.tag: String
    get() = when (this) {
        MemorySubject.USER -> "user"
        MemorySubject.SELF -> "self"
        MemorySubject.PROMISE -> "promise"
    }
