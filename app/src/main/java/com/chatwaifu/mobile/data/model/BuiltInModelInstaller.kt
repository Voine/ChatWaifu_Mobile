package com.chatwaifu.mobile.data.model

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.util.Log
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.vits.utils.file.FileUtils
import com.example.textpreprocess.preprocess.LANGUAGE_JP
import java.io.File

/**
 * Description: 把随包内置的模型从 assets 解到 [ModelStorage] 的统一布局下。
 *
 * live2d 的 assets 来自 `Live2D/src/main/assets/Live2DModels/`；
 * 声库的 assets 来自 **Bert-VITS2-MNN 的 AAR**（`bertvits2-jni` 的
 * `bv2_model/` + `bert/`）—— AAR 的 assets 在构建时就合并进了 app 的 asset 命名空间，
 * 所以读法和本模块自己的 assets 没区别。老的 `VITS/src/main/assets/VITSModels/`
 * 已经随 ncnn 引擎一起删掉了。
 *
 * 声库**不再按角色拷**：BV2 是单模型多 speaker，三个内置角色共用
 * [Bv2ModelInstaller] 装的那一份权重，各自只记一个 speaker id。
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
    private val bv2Installer by lazy { Bv2ModelInstaller(context, storage) }

    companion object {
        private const val TAG = "BuiltInModelInstaller"
        private const val MODEL3_SUFFIX = ".model3.json"

        /**
         * 内置角色统一用日文声库：主循环的 `fetchTranslateIfNeed()` 本来就把回复
         * 翻成日文，而且 BV2 只有日文链路的 LFS 权重被拉下来了（见 CLAUDE.md）。
         */
        private const val BUILT_IN_LANGUAGE = LANGUAGE_JP
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

        // 共享声库先装，角色的 speakerId 要从它的 config.json 里分配
        val bv2Ready = bv2Installer.ensureInstalled(BUILT_IN_LANGUAGE, force = versionChanged) != null
        if (!bv2Ready) Log.w(TAG, "bv2 voice unavailable, built-in characters will be mute")
        val speakers = if (bv2Ready) bv2Installer.availableSpeakers(BUILT_IN_LANGUAGE) else emptyList()

        val result = mutableListOf<ModelMeta>()
        var allSucceeded = true
        names.forEachIndexed { index, name ->
            // 版本没变且已经解好了就跳过，这是省掉几百 MB 重拷的关键分支
            val existing = storage.readMeta(name)
            val visualReady = existing != null &&
                File(storage.live2dDir(name), existing.live2dEntryFileName).isFile
            if (!versionChanged && visualReady) {
                result += existing
                return@forEachIndexed
            }
            // speaker 按角色在 assets 里的顺序轮着分，声库里 speaker 不够就重复用。
            // 皮套和声音本来就对不上（BV2 的日文角色和 Yuuka/Amadeus/ATRI 没关系），
            // 等用户自己炼了模型再替换，见 docs 里的说明
            val speaker = speakers.getOrNull(index % speakers.size.coerceAtLeast(1))
            val meta = install(assets, name, speaker?.second ?: 0, bv2Ready)
            if (meta != null) {
                result += meta
            } else {
                allSucceeded = false
            }
        }

        // 有失败的就不落版本号，下次启动会重试，避免半成品被永久当成「已安装」
        if (versionChanged && allSucceeded) {
            sp.edit()
                .putInt(Constant.SAVED_BUILT_IN_MODEL_VERSION, Constant.BUILT_IN_MODEL_VERSION)
                .putInt(
                    Constant.SAVED_BUILT_IN_VISUAL_MODEL_VERSION,
                    Constant.BUILT_IN_MODEL_VERSION
                )
                .apply()
        }
        return result
    }

    /**
     * 只解出 Live2D 展示资源。不会访问或复制 BV2/BERT assets，也不会把角色标记为有声。
     */
    fun ensureVisualsInstalled(): List<ModelMeta> {
        val assets = context.assets
        val names = assets.list(Constant.LIVE2D_BASE_PATH)?.toList().orEmpty()
        if (names.isEmpty()) return emptyList()

        val installedVersion = sp.getInt(Constant.SAVED_BUILT_IN_VISUAL_MODEL_VERSION, -1)
        val versionChanged = installedVersion != Constant.BUILT_IN_MODEL_VERSION
        val fullInstallCurrent =
            sp.getInt(Constant.SAVED_BUILT_IN_MODEL_VERSION, -1) == Constant.BUILT_IN_MODEL_VERSION
        val result = mutableListOf<ModelMeta>()
        var allSucceeded = true
        names.forEach { name ->
            val existing = storage.readMeta(name)
            val visualReady = existing != null &&
                File(storage.live2dDir(name), existing.live2dEntryFileName).isFile
            // 完整安装已经是当前版本时必须保留原 meta 的 hasVits/speakerId，
            // 不能让纯展示入口把已有角色降级成无声角色。
            if ((!versionChanged || fullInstallCurrent) && visualReady) {
                result += existing
                return@forEach
            }
            val meta = if (existing == null) {
                install(assets, name, speakerId = 0, hasVoice = false)
            } else {
                refreshVisuals(assets, existing)
            }
            if (meta != null) result += meta else allSucceeded = false
        }
        if (versionChanged && allSucceeded) {
            sp.edit().putInt(
                Constant.SAVED_BUILT_IN_VISUAL_MODEL_VERSION,
                Constant.BUILT_IN_MODEL_VERSION
            ).apply()
        }
        return result
    }

    private fun refreshVisuals(assets: AssetManager, existing: ModelMeta): ModelMeta? {
        val staged = File(storage.stagingRoot, "${existing.name}-visual")
        val backup = File(storage.stagingRoot, "${existing.name}-visual-backup")
        val target = storage.live2dDir(existing.name)
        if (staged.exists()) FileUtils.deleteDirectory(staged)
        if (backup.exists()) {
            if (!target.exists()) {
                if (!backup.renameTo(target)) {
                    Log.e(TAG, "restore ${existing.name} visual backup failed")
                    return null
                }
            } else {
                FileUtils.deleteDirectory(backup)
            }
        }

        return try {
            copyAssetDir(
                assets,
                "${Constant.LIVE2D_BASE_PATH}/${existing.name}",
                staged,
            )
            val entry = staged.list()?.firstOrNull { it.endsWith(MODEL3_SUFFIX) }
                ?: throw IllegalStateException("${existing.name} has no $MODEL3_SUFFIX")
            if (target.exists() && !target.renameTo(backup)) {
                throw IllegalStateException("backup existing Live2D directory failed")
            }
            if (!staged.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IllegalStateException("commit refreshed Live2D directory failed")
            }
            existing.copy(live2dEntryFileName = entry).also {
                storage.writeMeta(existing.name, it)
            }
                .also {
                    if (backup.exists()) FileUtils.deleteDirectory(backup)
                }
        } catch (e: Exception) {
            Log.e(TAG, "refresh ${existing.name} visual failed", e)
            if (staged.exists()) FileUtils.deleteDirectory(staged)
            if (backup.exists()) {
                if (target.exists()) FileUtils.deleteDirectory(target)
                backup.renameTo(target)
            }
            null
        }
    }

    private fun install(
        assets: AssetManager,
        name: String,
        speakerId: Int,
        hasVoice: Boolean,
    ): ModelMeta? {
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

            // 声库是可选的：只有 live2d 的角色也能用，只是不出声。
            // 这里不再往 staged 里拷任何声库文件 —— 权重在共享目录，见 Bv2ModelInstaller
            val meta = ModelMeta(
                name = name,
                source = ModelSource.BUILT_IN.name,
                live2dEntryFileName = entry,
                hasVits = hasVoice,
                speakerId = speakerId,
                sharedVoice = true,
                language = BUILT_IN_LANGUAGE,
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
