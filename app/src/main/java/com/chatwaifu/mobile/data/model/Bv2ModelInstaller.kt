package com.chatwaifu.mobile.data.model

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.chatwaifu.vits.utils.SoundGenerateHelper
import com.chatwaifu.vits.utils.file.ConfigParseResult
import com.chatwaifu.vits.utils.file.FileUtils
import java.io.File

/**
 * Description: Bert-VITS2 共享模型的安装。
 *
 * **为什么和 [ModelStorage] 的「每角色一个 vits 目录」分开**：BV2 的声学模型是
 * 单模型多 speaker（config.json 的 `spk2id`），三个内置角色共用同一份权重、
 * 只差一个 speaker id。照老布局每角色拷一份的话磁盘上会躺三份 90MB。
 * 所以权重装一份到共享目录，角色侧只记 speaker id。
 *
 * 落盘布局**照抄 BV2 AAR 的 assets 结构**，安装就是直接拷两个目录，
 * 也让 [SoundGenerateHelper.init] 不用关心「哪个 .mnn 是 BERT」：
 * ```
 * <getExternalFilesDir("models")>/_bv2/
 *   bv2_model/jp/   config.json + 6 个 *.mnn
 *   bert/jp/        deberta-v2-*.mnn
 * ```
 *
 * 另外有一处**绕不开的硬编码路径**：`text-preprocess` 的 `JPBV2Impl` 直接读
 * `filesDir/bert/jp/vocab.txt`（AAR 里写死的，不是我们能选的），
 * 所以 vocab 必须额外装到 filesDir 下那个位置。
 *
 * Author: Voine
 * Date: 2026/9/10
 */
internal class Bv2ModelInstaller(
    private val context: Context,
    private val storage: ModelStorage,
) {

    /**
     * 确保 [language] 对应的 BV2 模型就位。幂等，[force] 时强制重拷。
     *
     * @return 装好的共享根目录（仅用于判定成功；实际路径由 [ModelStorage] 提供），失败返回 null
     */
    fun ensureInstalled(language: Int, force: Boolean): File? {
        val langDir = SoundGenerateHelper.languageDirName(language) ?: run {
            Log.e(TAG, "unsupported language $language")
            return null
        }
        val bv2Dest = storage.bv2AcousticDir(language) ?: return null
        val bertDest = storage.bv2BertDir(language) ?: return null
        val assets = context.assets

        return try {
            if (force) {
                FileUtils.deleteDirectory(bv2Dest)
                FileUtils.deleteDirectory(bertDest)
            }
            // assets 侧的路径就是 AAR 里的原样，两边同构所以不用 rebase
            copyAssetDirIfNeeded(assets, "${SoundGenerateHelper.BV2_MODEL_DIR}/$langDir", bv2Dest)
            copyAssetDirIfNeeded(assets, "${SoundGenerateHelper.BERT_DIR}/$langDir", bertDest)

            if (!File(bv2Dest, SoundGenerateHelper.CONFIG_JSON).isFile) {
                Log.e(TAG, "bv2 config.json missing after install: $bv2Dest")
                return null
            }
            installJpVocabIfNeeded(assets, langDir, force)
            storage.bv2Root
        } catch (e: Exception) {
            Log.e(TAG, "install bv2 model for $langDir failed", e)
            null
        }
    }

    /**
     * 从 [ensureInstalled] 装好的 config.json 里读 `spk2id`，**按 speaker id 升序**返回名字。
     * 给内置角色分配 speaker 用，顺序稳定才能保证同一个角色每次拿到同一个声音。
     */
    fun availableSpeakers(language: Int): List<Pair<String, Int>> {
        val dir = storage.bv2AcousticDir(language) ?: return emptyList()
        val config = File(dir, SoundGenerateHelper.CONFIG_JSON)
        if (!config.isFile) return emptyList()
        val spk2id = (FileUtils.parseConfig(config.absolutePath) as? ConfigParseResult.Success)
            ?.config?.data?.spk2id
            ?: return emptyList()
        return spk2id.toList().sortedBy { it.second }
    }

    /**
     * `JPBV2Impl` 硬编码读 `filesDir/bert/jp/vocab.txt`，只能装到这。
     * 非日文语种不需要（zh 走 tokenizer.json、en 走 spm.model，那两条链路
     * 由 BV2 的 `initPreprocessor()` 负责拷）。
     */
    private fun installJpVocabIfNeeded(assets: AssetManager, langDir: String, force: Boolean) {
        if (langDir != JP_DIR) return
        val dest = File(context.filesDir, "${SoundGenerateHelper.BERT_DIR}/$JP_DIR/$JP_VOCAB")
        if (force) dest.delete()
        if (dest.isFile) return
        dest.parentFile?.let { if (!it.exists()) it.mkdirs() }
        assets.open("${SoundGenerateHelper.BERT_DIR}/$JP_DIR/$JP_VOCAB").use { input ->
            FileUtils.copyToFileOrThrow(input, dest, ByteArray(FileUtils.DEFAULT_BUFFER))
        }
        Log.d(TAG, "installed jp vocab to ${dest.absolutePath}")
    }

    /**
     * 递归拷 assets 目录，**逐文件跳过已存在的**。
     *
     * `copyToFileOrThrow` 本身就会跳过已存在的目标文件，所以这里天然增量 ——
     * 90MB 的权重不会每次启动重拷一遍。
     */
    private fun copyAssetDirIfNeeded(assets: AssetManager, srcPath: String, dest: File) {
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
            copyAssetDirIfNeeded(assets, "$srcPath/$child", File(dest, child))
        }
    }

    companion object {
        private const val TAG = "Bv2ModelInstaller"
        private const val JP_DIR = "jp"
        private const val JP_VOCAB = "vocab.txt"
    }
}
