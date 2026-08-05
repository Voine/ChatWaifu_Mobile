package com.chatwaifu.mobile.data.model

import android.content.Context
import android.util.Log
import com.chatwaifu.vits.utils.file.FileUtils
import com.google.gson.Gson
import java.io.File

/**
 * Description: 模型在磁盘上的布局与 meta.json 读写。
 *
 * 落在应用专属外部目录，内置模型和导入模型共用同一套布局：
 * ```
 * <getExternalFilesDir("models")>/
 *   <characterName>/
 *     meta.json
 *     live2d/    xxx.model3.json, xxx.moc3, ...
 *     vits/      config.json, *.bin
 * ```
 *
 * 选专属目录而不是 /sdcard/chatwaifu/：targetSdk 升上去之后非媒体文件已经没有
 * 任何运行时权限能访问任意路径了。专属目录零权限、卸载自动清理，且不占内部存储配额。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
internal class ModelStorage(private val context: Context) {

    companion object {
        private const val TAG = "ModelStorage"
        private const val MODELS_DIR = "models"
        private const val STAGING_DIR = "staging"
        private const val META_FILE = "meta.json"
        const val LIVE2D_DIR = "live2d"
        const val VITS_DIR = "vits"
    }

    private val gson by lazy { Gson() }

    /**
     * 模型根目录。外部存储不可用（未挂载等）时退回内部存储，
     * 保证功能可用而不是直接崩。
     */
    val root: File
        get() = (context.getExternalFilesDir(MODELS_DIR) ?: File(context.filesDir, MODELS_DIR))
            .also { if (!it.exists()) it.mkdirs() }

    /** 导入过程中的暂存区，和 [root] 同分区，才能用 rename 原子落地 */
    val stagingRoot: File
        get() = File(root.parentFile, STAGING_DIR).also { if (!it.exists()) it.mkdirs() }

    fun modelDir(name: String): File = File(root, name)

    fun live2dDir(name: String): File = File(modelDir(name), LIVE2D_DIR)

    fun vitsDir(name: String): File = File(modelDir(name), VITS_DIR)

    fun exists(name: String): Boolean = readMeta(name) != null

    fun readMeta(name: String): ModelMeta? {
        val file = File(modelDir(name), META_FILE)
        if (!file.isFile) return null
        return try {
            gson.fromJson(file.readText(), ModelMeta::class.java)?.takeIf { it.isValid() }
        } catch (e: Exception) {
            Log.e(TAG, "parse meta failed for $name", e)
            null
        }
    }

    fun writeMeta(dir: File, meta: ModelMeta) {
        File(dir, META_FILE).writeText(gson.toJson(meta))
    }

    fun writeMeta(name: String, meta: ModelMeta) = writeMeta(modelDir(name), meta)

    /** 扫描根目录，返回所有 meta.json 合法的模型。目录存在但 meta 坏掉的会被跳过 */
    fun listInstalled(): List<ModelMeta> {
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        return dirs.mapNotNull { readMeta(it.name) }
    }

    fun delete(name: String): Boolean {
        val dir = modelDir(name)
        if (!dir.exists()) return false
        FileUtils.deleteDirectory(dir)
        return !dir.exists()
    }

    /** 清理上次导入残留的暂存目录 */
    fun clearStaging() {
        stagingRoot.listFiles()?.forEach { FileUtils.deleteDirectory(it) }
    }

    /**
     * 把 [staged] 原子落地成名为 [name] 的模型。同分区 rename，失败返回 false。
     */
    fun commit(staged: File, name: String): Boolean {
        val target = modelDir(name)
        if (target.exists()) FileUtils.deleteDirectory(target)
        return staged.renameTo(target)
    }

    /** 把 [ModelMeta] 补上绝对路径，变成领域模型 */
    fun toCharacterModel(meta: ModelMeta): CharacterModel = CharacterModel(
        name = meta.name,
        source = runCatching { ModelSource.valueOf(meta.source) }.getOrDefault(ModelSource.IMPORTED),
        live2dDir = live2dDir(meta.name).absolutePath,
        live2dEntryFileName = meta.live2dEntryFileName,
        vitsDir = vitsDir(meta.name).takeIf { meta.hasVits && it.isDirectory }?.absolutePath,
        speakerId = meta.speakerId,
    )
}

/**
 * meta.json 的落盘结构。
 *
 * 存在的意义是干掉两个隐式约定：以前 live2d 目录名必须和 vits 目录名一致，
 * 且 model3.json 文件名必须和目录名一致（ChatFragment 里硬编码拼的）。
 * 现在入口文件名在导入时解析出来直接写死在这里。
 *
 * 字段给默认值是为了兼容 Gson 反序列化缺字段的情况，读出来后一律走 [isValid] 校验。
 */
internal data class ModelMeta(
    val name: String = "",
    /** [ModelSource] 的 name */
    val source: String = ModelSource.IMPORTED.name,
    val live2dEntryFileName: String = "",
    val hasVits: Boolean = false,
    val speakerId: Int = 0,
) {
    fun isValid(): Boolean = name.isNotBlank() && live2dEntryFileName.isNotBlank()
}
