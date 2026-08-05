package com.chatwaifu.mobile.data.model

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.util.Log
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.vits.utils.file.FileUtils
import java.io.File

/**
 * Description: 把随包内置的模型从 assets 解到 [ModelStorage] 的统一布局下。
 *
 * assets 实际来自 Live2D / VITS 两个库模块（`Live2D/src/main/assets/Live2DModels/`、
 * `VITS/src/main/assets/VITSModels/`），运行时会合并进 app 的 asset 命名空间。
 *
 * 关键点是**版本 gate**：以前每次进角色列表都 deleteIfExists 全量重拷三百多 MB，
 * 现在只在内置资源版本变化或目录缺失时才解。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
internal class BuiltInModelInstaller(
    private val context: Context,
    private val storage: ModelStorage,
    private val sp: SharedPreferences,
) {
    companion object {
        private const val TAG = "BuiltInModelInstaller"
        private const val MODEL3_SUFFIX = ".model3.json"
        private const val VITS_CONFIG = "config.json"
    }

    /**
     * 确保内置模型都已就位。返回内置角色的 meta 列表。
     *
     * 幂等：版本没变且目录齐全时什么都不做。
     */
    fun ensureInstalled(): List<ModelMeta> {
        val assets = context.assets
        val names = assets.list(Constant.LIVE2D_BASE_PATH)?.toList().orEmpty()
        if (names.isEmpty()) {
            Log.w(TAG, "no built-in live2d model found in assets")
            return emptyList()
        }

        val installedVersion = sp.getInt(Constant.SAVED_BUILT_IN_MODEL_VERSION, -1)
        val versionChanged = installedVersion != Constant.BUILT_IN_MODEL_VERSION

        val result = mutableListOf<ModelMeta>()
        var allSucceeded = true
        names.forEach { name ->
            // 版本没变且已经解好了就跳过，这是省掉几百 MB 重拷的关键分支
            val existing = storage.readMeta(name)
            if (!versionChanged && existing != null) {
                result += existing
                return@forEach
            }
            val meta = install(assets, name)
            if (meta != null) {
                result += meta
            } else {
                allSucceeded = false
            }
        }

        // 有失败的就不落版本号，下次启动会重试，避免半成品被永久当成「已安装」
        if (versionChanged && allSucceeded) {
            sp.edit().putInt(
                Constant.SAVED_BUILT_IN_MODEL_VERSION,
                Constant.BUILT_IN_MODEL_VERSION
            ).apply()
        }
        return result
    }

    private fun install(assets: AssetManager, name: String): ModelMeta? {
        Log.d(TAG, "installing built-in model $name")
        val staged = File(storage.stagingRoot, name)
        if (staged.exists()) FileUtils.deleteDirectory(staged)

        return try {
            val live2dSrc = "${Constant.LIVE2D_BASE_PATH}/$name"
            val live2dDest = File(staged, ModelStorage.LIVE2D_DIR)
            copyAssetDir(assets, live2dSrc, live2dDest)

            val entry = live2dDest.list()?.firstOrNull { it.endsWith(MODEL3_SUFFIX) }
            if (entry == null) {
                Log.e(TAG, "$name has no $MODEL3_SUFFIX, skip")
                FileUtils.deleteDirectory(staged)
                return null
            }

            // vits 是可选的：只有 live2d 的角色也能用，只是不出声
            val vitsSrc = "${Constant.VITS_BASE_PATH}/$name"
            val hasVits = assets.list(vitsSrc)?.contains(VITS_CONFIG) == true
            if (hasVits) {
                copyAssetDir(assets, vitsSrc, File(staged, ModelStorage.VITS_DIR))
            }

            val meta = ModelMeta(
                name = name,
                source = ModelSource.BUILT_IN.name,
                live2dEntryFileName = entry,
                hasVits = hasVits,
                speakerId = 0,
            )
            storage.writeMeta(staged, meta)

            if (!storage.commit(staged, name)) {
                Log.e(TAG, "commit $name failed")
                FileUtils.deleteDirectory(staged)
                return null
            }
            meta
        } catch (e: Exception) {
            Log.e(TAG, "install $name failed", e)
            FileUtils.deleteDirectory(staged)
            null
        }
    }

    /**
     * 递归把 assets 下 [srcPath] 拷到 [dest]，**不保留 srcPath 前缀**。
     * [srcPath] 是目录时 [dest] 当目录用，是文件时 [dest] 就是目标文件。
     *
     * 没直接用 FileUtils.copyAssets：那个会把源路径原样拼到目标下（`dest/VITSModels/Yuuka/...`），
     * 而这里需要 rebase 成 `dest/vits/...`。字节搬运仍复用 FileUtils.copyToFileOrThrow。
     */
    private fun copyAssetDir(assets: AssetManager, srcPath: String, dest: File) {
        // assets.list() 对文件返回空数组，对目录返回子项，以此区分
        val children = assets.list(srcPath)
        if (children.isNullOrEmpty()) {
            dest.parentFile?.let { if (!it.exists()) it.mkdirs() }
            assets.open(srcPath).use { input ->
                FileUtils.copyToFileOrThrow(input, dest, ByteArray(FileUtils.DEFAULT_BUFFER))
            }
            return
        }
        if (!dest.exists() && !dest.mkdirs()) {
            throw IllegalStateException("mkdir failed: ${dest.absolutePath}")
        }
        children.forEach { child ->
            copyAssetDir(assets, "$srcPath/$child", File(dest, child))
        }
    }
}
