package com.chatwaifu.mobile.data.model

import android.content.Context
import android.util.Log
import com.chatwaifu.vits.utils.SoundGenerateHelper
import com.chatwaifu.vits.utils.file.FileUtils
import com.google.gson.Gson
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID

/**
 * Description: 模型在磁盘上的布局与 meta.json 读写。
 *
 * 落在应用专属外部目录，内置模型和导入模型共用同一套布局：
 * ```
 * <getExternalFilesDir("models")>/
 *   <characterName>/
 *     meta.json
 *     live2d/    xxx.model3.json, xxx.moc3, ...
 *     vits/      config.json, *.mnn     ← 角色自带声库（导入模型用）
 *   _bv2/                               ← BV2 共享声库，见 [Bv2ModelInstaller]
 *     bv2_model/<lang>/  bert/<lang>/
 * ```
 *
 * `_bv2` 用下划线前缀是为了和角色名区分：[listInstalled] 靠 meta.json 存在与否过滤，
 * 共享目录里没有 meta.json，所以不会被当成角色扫出来。
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
        private const val BV2_DIR = "_bv2"
        private const val META_FILE = "meta.json"
        const val LIVE2D_DIR = "live2d"
        const val VITS_DIR = "vits"

        internal fun builtinCharacterId(storageKey: String): String =
            "builtin:" + storageKey.trim().lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-')

        internal fun migrateMetadata(
            meta: ModelMeta,
            storageKey: String,
            importedId: () -> String = { "imported:${UUID.randomUUID()}" },
        ): ModelMeta {
            val source = runCatching { ModelSource.valueOf(meta.source) }
                .getOrDefault(ModelSource.IMPORTED)
            val stableId = meta.id.ifBlank {
                if (source == ModelSource.BUILT_IN) {
                    builtinCharacterId(storageKey)
                } else {
                    importedId()
                }
            }
            return meta.copy(
                id = stableId,
                name = storageKey,
                displayName = meta.displayName.ifBlank { meta.name.ifBlank { storageKey } },
                source = source.name,
            )
        }
    }

    private val gson by lazy { Gson() }

    /**
     * 模型根目录。外部存储不可用（未挂载等）时退回内部存储，
     * 保证功能可用而不是直接崩。
     */
    val root: File
        get() = (context.getExternalFilesDir(MODELS_DIR) ?: File(context.filesDir, MODELS_DIR))
            .also { if (!it.exists()) it.mkdirs() }

    /** BV2 共享声库根目录，内部布局照抄 AAR 的 assets（见 [Bv2ModelInstaller]） */
    val bv2Root: File
        get() = File(root, BV2_DIR).also { if (!it.exists()) it.mkdirs() }

    /** 共享的声学模型目录（内置角色用）。语种目录名的映射只在 BV2 侧有一份 */
    fun bv2AcousticDir(language: Int): File? =
        SoundGenerateHelper.languageDirName(language)?.let {
            File(bv2Root, "${SoundGenerateHelper.BV2_MODEL_DIR}/$it")
        }

    /**
     * 共享的 BERT 目录。**导入角色也用这里的** —— BERT 是「一个语种的编码器」，
     * 随包走，不该逼着每个导入模型自带一份 40MB。
     */
    fun bv2BertDir(language: Int): File? =
        SoundGenerateHelper.languageDirName(language)?.let {
            File(bv2Root, "${SoundGenerateHelper.BERT_DIR}/$it")
        }

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
            val parsed = gson.fromJson(file.readText(), ModelMeta::class.java)
                ?.takeIf { it.isValid() }
                ?: return null
            migrateMetadata(parsed, name).also { migrated ->
                if (migrated != parsed) writeMeta(name, migrated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse meta failed for $name", e)
            null
        }
    }

    fun writeMeta(dir: File, meta: ModelMeta) {
        val target = File(dir, META_FILE)
        val temporary = File(dir, "$META_FILE.tmp")
        temporary.writeText(gson.toJson(meta))
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun writeMeta(name: String, meta: ModelMeta) = writeMeta(modelDir(name), meta)

    /** 扫描根目录。meta 损坏会跳过；Live2D 资源缺失会保留为 unavailable 角色。 */
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

    /** 把 [ModelMeta] 补上绝对路径，变成角色包。 */
    fun toCharacterModel(meta: ModelMeta): CharacterPackage = CharacterPackage(
        id = meta.id,
        displayName = meta.displayName.ifBlank { meta.name },
        storageKey = meta.name,
        source = runCatching { ModelSource.valueOf(meta.source) }.getOrDefault(ModelSource.IMPORTED),
        live2dDir = live2dDir(meta.name).absolutePath,
        live2dEntryFileName = meta.live2dEntryFileName,
        vitsDir = resolveVoiceDir(meta),
        bertDir = bv2BertDir(meta.language)?.takeIf { it.isDirectory }?.absolutePath,
        speakerId = meta.speakerId,
        language = meta.language,
        preview = meta.previewFileName
            ?.let { File(modelDir(meta.name), it) }
            ?.takeIf { it.isFile }
            ?.let { CharacterPreview.FilePath(it.absolutePath) },
        personaProfileId = meta.personaProfileId,
        voiceProfileId = meta.voiceProfileId,
        behaviorProfileId = meta.behaviorProfileId,
        availability = if (
            File(live2dDir(meta.name), meta.live2dEntryFileName).isFile
        ) {
            CharacterAvailability.AVAILABLE
        } else {
            CharacterAvailability.MISSING_LIVE2D
        },
    )

    /**
     * 角色的声学模型目录。[ModelMeta.sharedVoice] 为真时指向共享的那份
     * （内置角色，BV2 是单模型多 speaker），否则是角色自己的 `vits/` 目录（导入模型）。
     */
    private fun resolveVoiceDir(meta: ModelMeta): String? {
        if (!meta.hasVits) return null
        val dir = if (meta.sharedVoice) bv2AcousticDir(meta.language) else vitsDir(meta.name)
        return dir?.takeIf { it.isDirectory }?.absolutePath
    }

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
    val id: String = "",
    val name: String = "",
    val displayName: String = "",
    /** [ModelSource] 的 name */
    val source: String = ModelSource.IMPORTED.name,
    val live2dEntryFileName: String = "",
    val hasVits: Boolean = false,
    val speakerId: Int = 0,
    /**
     * true 表示声库不在角色目录里，而是 [ModelStorage.bv2Root] 那份共享权重。
     * 内置角色都是 true —— BV2 是单模型多 speaker，没必要每个角色拷一份 90MB。
     */
    val sharedVoice: Boolean = false,
    /** `LANGUAGE_ZH/EN/JP/MIX_ZH_EN` 之一，决定走哪套 G2P 和哪份 BERT */
    val language: Int = 0,
    val previewFileName: String? = null,
    val personaProfileId: String? = null,
    val voiceProfileId: String? = null,
    val behaviorProfileId: String? = null,
) {
    fun isValid(): Boolean = name.isNotBlank() && live2dEntryFileName.isNotBlank()
}
