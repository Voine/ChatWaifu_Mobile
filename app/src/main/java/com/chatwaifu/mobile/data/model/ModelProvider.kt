package com.chatwaifu.mobile.data.model

import android.content.Context
import com.chatwaifu.mobile.data.Constant

/**
 * Description: [CharacterRepository] / [ModelImporter] 的入口。
 *
 * 工程里没有 DI 框架，各 ViewModel 原先都是自己 `by lazy { LocalModelManager() }`，
 * 结果每个 ViewModel 一份实例、各自扫一遍磁盘。这里收成单例，
 * 也方便后续接 Hilt 时只改这一个文件。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
object ModelProvider {

    @Volatile
    private var storageRef: ModelStorage? = null

    @Volatile
    private var repositoryRef: CharacterRepository? = null

    @Volatile
    private var importerRef: ModelImporter? = null

    fun repository(context: Context): CharacterRepository =
        repositoryRef ?: synchronized(this) {
            repositoryRef ?: CharacterRepositoryImpl(
                context = context.applicationContext,
                storage = storage(context),
                sp = context.applicationContext
                    .getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE),
            ).also { repositoryRef = it }
        }

    fun importer(context: Context): ModelImporter =
        importerRef ?: synchronized(this) {
            importerRef ?: ZipModelImporter(
                context = context.applicationContext,
                storage = storage(context),
            ).also { importerRef = it }
        }

    private fun storage(context: Context): ModelStorage =
        storageRef ?: synchronized(this) {
            storageRef ?: ModelStorage(context.applicationContext).also { storageRef = it }
        }
}
