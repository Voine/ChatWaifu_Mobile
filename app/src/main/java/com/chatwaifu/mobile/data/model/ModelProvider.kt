package com.chatwaifu.mobile.data.model

import android.content.Context
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.model.profile.CharacterProfileRepository
import com.chatwaifu.mobile.data.model.profile.CharacterProfileRepositoryImpl

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

    @Volatile
    private var profilesRef: CharacterProfileRepository? = null

    fun repository(context: Context): CharacterRepository =
        repositoryRef ?: synchronized(this) {
            repositoryRef ?: CharacterRepositoryImpl(
                context = context.applicationContext,
                storage = storage(context),
                sp = preferences(context),
            ).also { repositoryRef = it }
        }

    /** Persona / Voice profile 的入口。组装在 [repository] 之上，不是第二份存储。 */
    fun profiles(context: Context): CharacterProfileRepository =
        profilesRef ?: synchronized(this) {
            profilesRef ?: CharacterProfileRepositoryImpl(
                context = context.applicationContext,
                characters = repository(context),
                sp = preferences(context),
            ).also { profilesRef = it }
        }

    fun importer(context: Context): ModelImporter =
        importerRef ?: synchronized(this) {
            importerRef ?: ZipModelImporter(
                context = context.applicationContext,
                storage = storage(context),
            ).also { importerRef = it }
        }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)

    private fun storage(context: Context): ModelStorage =
        storageRef ?: synchronized(this) {
            storageRef ?: ModelStorage(context.applicationContext).also { storageRef = it }
        }
}
