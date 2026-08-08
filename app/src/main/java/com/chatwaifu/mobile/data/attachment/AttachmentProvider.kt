package com.chatwaifu.mobile.data.attachment

import android.content.Context
import com.chatwaifu.log.AttachmentKind

/**
 * Description: 附件管线的单例入口，和 `ModelProvider` 同一个套路。
 *
 * 工程里没有 DI 框架。这里收成单例有两个实际原因，不只是整齐：
 *
 * 1. **`ImageLoader` 很贵**。它自带线程池和缓存配置，每个 ViewModel 造一个
 *    就是 N 份线程池
 * 2. **`AttachmentStore` 有磁盘状态**（临时目录、GC），多份实例的 `clearTemp()`
 *    会互相踩
 *
 * 接 Hilt 时只改这一个文件。
 *
 * Author: Voine
 * Date: 2026/8/8
 */
object AttachmentProvider {

    @Volatile
    private var storeRef: AttachmentStore? = null

    @Volatile
    private var ingestRef: AttachmentIngest? = null

    fun store(context: Context): AttachmentStore =
        storeRef ?: synchronized(this) {
            storeRef ?: AttachmentStore(context.applicationContext).also { storeRef = it }
        }

    fun ingest(context: Context): AttachmentIngest =
        ingestRef ?: synchronized(this) {
            ingestRef ?: build(context.applicationContext).also { ingestRef = it }
        }

    private fun build(app: Context): AttachmentIngest {
        val store = store(app)
        val loader = ImageNormalizer.defaultLoader(app)
        return AttachmentIngest(
            probe = MediaProbe(app),
            store = store,
            normalizers = mapOf(
                AttachmentKind.IMAGE to ImageNormalizer(app, store, loader),
                AttachmentKind.AUDIO to AudioNormalizer(app, store),
                AttachmentKind.VIDEO to VideoNormalizer(app, store),
                AttachmentKind.DOC to DocNormalizer(app, store),
            ),
        )
    }
}
