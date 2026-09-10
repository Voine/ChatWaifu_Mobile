package com.chatwaifu.mobile.data.model

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.chatwaifu.vits.utils.file.ConfigParseResult
import com.chatwaifu.vits.utils.file.FileUtils
import com.example.textpreprocess.preprocess.LANGUAGE_JP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Description: 从 zip 导入角色模型。
 *
 * 流程：SAF 流式解压到暂存区 → 嗅探目录结构 → 校验 → rebase 成统一布局 → rename 落地。
 * 全程不把整包读进内存，也不依赖 zip 内部的固定层级。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
internal class ZipModelImporter(
    private val context: Context,
    private val storage: ModelStorage,
) : ModelImporter {

    companion object {
        private const val TAG = "ZipModelImporter"
        private const val MODEL3_SUFFIX = ".model3.json"
        private const val VITS_CONFIG = "config.json"
        private const val MNN_SUFFIX = ".mnn"

        /** 导入模型的默认语种。BV2 的 config.json 不带语种信息，只能给个默认值 */
        private const val IMPORTED_DEFAULT_LANGUAGE = LANGUAGE_JP

        /** 防 zip bomb：条目数和解压后总体积上限。内置模型约 330MB，2GB 留足余量 */
        private const val MAX_ENTRIES = 10_000
        private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    }

    override fun import(source: Uri): Flow<ImportProgress> = flow {
        val name = resolveName(source)
        if (name == null) {
            emit(ImportProgress.Failed(ImportError.NotAZip))
            return@flow
        }
        if (storage.exists(name)) {
            emit(ImportProgress.Failed(ImportError.NameConflict(name)))
            return@flow
        }

        storage.clearStaging()
        val raw = File(storage.stagingRoot, "${name}_raw")
        val stagedModel = File(storage.stagingRoot, name)

        try {
            when (val extracted = extractTo(source, raw)) {
                is ExtractResult.Failure -> {
                    emit(ImportProgress.Failed(extracted.error))
                    return@flow
                }

                is ExtractResult.Success -> Unit
            }

            emit(ImportProgress.Validating)

            // ---- 嗅探结构 ----
            val model3Files = raw.walkTopDown().filter {
                it.isFile && it.name.endsWith(MODEL3_SUFFIX)
            }.toList()
            if (model3Files.isEmpty()) {
                emit(ImportProgress.Failed(ImportError.NoLive2DEntry))
                return@flow
            }
            if (model3Files.size > 1) {
                emit(
                    ImportProgress.Failed(
                        ImportError.MultipleLive2DEntries(
                            model3Files.map { it.relativeTo(raw).path }
                        )
                    )
                )
                return@flow
            }
            val live2dEntry = model3Files.first()
            val live2dRoot = live2dEntry.parentFile
            if (live2dRoot == null) {
                emit(ImportProgress.Failed(ImportError.NoLive2DEntry))
                return@flow
            }

            // 声库目录 = 含 config.json 且同级有 .mnn 的目录。加 .mnn 判断是为了
            // 不把 live2d 侧可能存在的同名 config.json 误认成声库配置。
            // 后缀从 .bin 换成 .mnn 是因为引擎从 VITS-ncnn 换成了 Bert-VITS2-MNN
            val vitsRoot = raw.walkTopDown().firstOrNull { dir ->
                dir.isDirectory &&
                    File(dir, VITS_CONFIG).isFile &&
                    dir.listFiles()?.any { it.name.endsWith(MNN_SUFFIX) } == true
            }

            if (vitsRoot != null) {
                when (val parsed = FileUtils.parseConfig(File(vitsRoot, VITS_CONFIG).absolutePath)) {
                    is ConfigParseResult.Failure -> {
                        emit(ImportProgress.Failed(ImportError.InvalidVitsConfig(parsed.reason)))
                        return@flow
                    }

                    is ConfigParseResult.Success -> Unit
                }
            }

            // ---- rebase 成统一布局 ----
            if (!stagedModel.mkdirs()) {
                emit(ImportProgress.Failed(ImportError.Io("cant create staging dir")))
                return@flow
            }
            val live2dTarget = File(stagedModel, ModelStorage.LIVE2D_DIR)
            val vitsTarget = File(stagedModel, ModelStorage.VITS_DIR)

            if (vitsRoot != null && vitsRoot == live2dRoot) {
                // 扁平包：两种文件混在同一层，没法按目录搬，只能按文件名分流。
                // 声库侧就是 config.json 和那堆 .mnn，live2d 的资源里不含 .mnn
                if (!splitFlatDir(live2dRoot, live2dTarget, vitsTarget)) {
                    emit(ImportProgress.Failed(ImportError.Io("split flat archive failed")))
                    return@flow
                }
            } else {
                // 两者可能互相嵌套（比如 vits/ 就在 live2d/ 里面），先搬里层的那个，
                // 否则搬外层时会把里层一起带走
                val moves = buildList {
                    if (vitsRoot != null) add(vitsRoot to vitsTarget)
                    add(live2dRoot to live2dTarget)
                }.sortedByDescending { it.first.absolutePath.length }

                moves.forEach { (from, to) ->
                    if (!from.renameTo(to)) {
                        emit(ImportProgress.Failed(ImportError.Io("move ${from.name} failed")))
                        return@flow
                    }
                }
            }

            val meta = ModelMeta(
                name = name,
                source = ModelSource.IMPORTED.name,
                live2dEntryFileName = live2dEntry.name,
                hasVits = vitsRoot != null,
                speakerId = 0,
                // 导入模型自带声学模型，不共享（sharedVoice = false）；
                // 但 BERT 仍走随包的共享那份，见 ModelStorage.bv2BertDir
                sharedVoice = false,
                // 语种默认按日文算：内置底模是日文，主循环也把回复翻成日文。
                // 用户导入的模型如果是中/英，得在模型管理页改 —— 这是一道有意留的缝，
                // config.json 里没有语种字段，猜不出来
                language = IMPORTED_DEFAULT_LANGUAGE,
            )
            storage.writeMeta(stagedModel, meta)

            if (!storage.commit(stagedModel, name)) {
                emit(ImportProgress.Failed(ImportError.Io("commit failed")))
                return@flow
            }
            emit(ImportProgress.Success(storage.toCharacterModel(meta)))
        } catch (e: Exception) {
            Log.e(TAG, "import failed", e)
            emit(ImportProgress.Failed(ImportError.Io(e.message ?: e.javaClass.simpleName)))
        } finally {
            // 成功路径下 raw 已被搬空、stagedModel 已 rename 走，这里只是兜底清残留
            FileUtils.deleteDirectory(raw)
            FileUtils.deleteDirectory(stagedModel)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * live2d 和声库文件混在同一目录时按文件名分流：
     * `config.json` 和 `*.mnn` 归声库，其余归 live2d。
     */
    private fun splitFlatDir(from: File, live2dTarget: File, vitsTarget: File): Boolean {
        if (!live2dTarget.mkdirs() || !vitsTarget.mkdirs()) return false
        val children = from.listFiles() ?: return false
        children.forEach { child ->
            val isVits = child.isFile &&
                (child.name == VITS_CONFIG || child.name.endsWith(MNN_SUFFIX))
            val target = File(if (isVits) vitsTarget else live2dTarget, child.name)
            if (!child.renameTo(target)) return false
        }
        return true
    }

    private sealed interface ExtractResult {
        data object Success : ExtractResult
        data class Failure(val error: ImportError) : ExtractResult
    }

    /**
     * 流式解压到 [dest]。进度按**已消耗的压缩字节数**算 —— ZipInputStream 拿不到总条目数，
     * 二次遍历又要整包解压一遍，所以在原始流上套计数器，对着 SAF 报的文件大小算百分比。
     */
    private suspend fun FlowCollector<ImportProgress>.extractTo(
        source: Uri,
        dest: File,
    ): ExtractResult {
        if (dest.exists()) FileUtils.deleteDirectory(dest)
        if (!dest.mkdirs()) return ExtractResult.Failure(ImportError.Io("cant create temp dir"))

        val totalCompressed = querySize(source)
        val destCanonical = dest.canonicalPath + File.separator
        var entryCount = 0
        var totalBytes = 0L
        var lastPercent = -1

        val input = try {
            context.contentResolver.openInputStream(source)
        } catch (e: Exception) {
            Log.e(TAG, "open uri failed", e)
            null
        } ?: return ExtractResult.Failure(ImportError.Io("cant open selected file"))

        val counting = CountingInputStream(input)
        try {
            ZipInputStream(BufferedInputStream(counting)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                if (entry == null) return ExtractResult.Failure(ImportError.NotAZip)

                val buffer = ByteArray(FileUtils.DEFAULT_BUFFER)
                while (entry != null) {
                    if (++entryCount > MAX_ENTRIES) {
                        return ExtractResult.Failure(
                            ImportError.Unsafe("too many entries (> $MAX_ENTRIES)")
                        )
                    }

                    val target = File(dest, entry.name)
                    // zip slip：解析成绝对路径后必须仍在 dest 之内
                    if (!target.canonicalPath.startsWith(destCanonical)) {
                        Log.e(TAG, "zip slip blocked: ${entry.name}")
                        return ExtractResult.Failure(
                            ImportError.Unsafe("entry escapes target dir: ${entry.name}")
                        )
                    }

                    if (entry.isDirectory) {
                        if (!target.exists() && !target.mkdirs()) {
                            return ExtractResult.Failure(
                                ImportError.Io("mkdir failed: ${entry.name}")
                            )
                        }
                    } else {
                        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        target.outputStream().use { out ->
                            while (true) {
                                val read = zis.read(buffer)
                                if (read <= 0) break
                                totalBytes += read
                                if (totalBytes > MAX_TOTAL_BYTES) {
                                    return ExtractResult.Failure(
                                        ImportError.Unsafe("uncompressed size exceeds limit")
                                    )
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                    }

                    if (totalCompressed > 0) {
                        val percent =
                            ((counting.count * 100) / totalCompressed).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            emit(ImportProgress.Extracting(percent))
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            Log.e(TAG, "not a valid zip", e)
            return ExtractResult.Failure(ImportError.NotAZip)
        }
        return ExtractResult.Success
    }

    /**
     * 从 SAF 拿显示名，去掉 .zip 后当角色名。
     * 非 zip 或名字不安全时返回 null。
     */
    private fun resolveName(source: Uri): String? {
        val displayName = queryDisplayName(source) ?: return null
        if (!displayName.endsWith(".zip", ignoreCase = true)) return null
        val bare = displayName.substringBeforeLast('.').trim()
        if (bare.isEmpty()) return null
        // 角色名会被当目录名用，不能带路径分隔符或以点开头
        if (bare.contains('/') || bare.contains('\\') || bare.startsWith('.')) return null
        return bare.take(64)
    }

    private fun queryDisplayName(uri: Uri): String? = queryColumn(uri, OpenableColumns.DISPLAY_NAME) {
        it.getString(0)
    }

    private fun querySize(uri: Uri): Long =
        queryColumn(uri, OpenableColumns.SIZE) { if (it.isNull(0)) -1L else it.getLong(0) } ?: -1L

    private fun <T> queryColumn(uri: Uri, column: String, read: (Cursor) -> T?): T? {
        return try {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) read(cursor) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "query $column failed", e)
            null
        }
    }

    /** 记录已从底层流读走多少字节，用来算解压进度 */
    private class CountingInputStream(delegate: InputStream) : FilterInputStream(delegate) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it != -1) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }
}
