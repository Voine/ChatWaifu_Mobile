package com.chatwaifu.mobile.data.attachment

import android.content.Context
import android.util.Log
import com.chatwaifu.log.AttachmentRef
import java.io.File
import java.util.UUID

/**
 * Description: 附件字节在磁盘上的归属地。DB 里只存相对路径，字节存这里。
 *
 * ```
 * <getExternalFilesDir("attachments")>/
 *   <characterId>/
 *     <uuid>.jpg
 * ```
 *
 * 和 `ModelStorage` 同样落应用专属外部目录：零权限、卸载自动清理、不占内部存储配额。
 *
 * ## 这个类**不懂格式**
 *
 * 早先它同时管落盘和图像归一化（解码、降采样、EXIF、JPEG 重编码）。加上音视频之后
 * 那条路走不通了——转码要 `MediaCodec`、抽帧要 `MediaMetadataRetriever`，
 * 每种媒体的参数和失败模式都不一样。现在格式知识全在 [MediaNormalizer] 那一族里，
 * 这里只剩四件事：落盘 / 定位 / 删除 / 孤儿回收。
 *
 * 入口是 [adopt]：接管一个**已经归一化好的临时文件**。它 rename 而不是复制，
 * 所以字节从头到尾只写一次，也从来没有整份进过内存——这是老实现最大的问题
 * （`readBytes()` 把整个文件读成 `ByteArray`，而体积闸门在读**之后**才判，
 * 选一个大视频就是必然 OOM，闸门根本没机会拦）。
 *
 * ## 为什么必须落地成真实文件而不是留着 `content://`
 *
 * 和 VITS 模型导入是同一个理由的变体：SAF 给的 `content://` URI 生命周期绑在
 * 那一次 `ACTION_OPEN_DOCUMENT` 的授权上，进程重启后可能已经失效，
 * 而历史重放要在任意时刻都能读到这个文件。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
class AttachmentStore(private val context: Context) {

    /**
     * 附件根目录。外部存储不可用时退回内部存储，保证功能可用而不是直接崩
     * （和 `ModelStorage.root` 一致）。
     */
    val root: File
        get() = (context.getExternalFilesDir(DIR) ?: File(context.filesDir, DIR))
            .also { if (!it.exists()) it.mkdirs() }

    /**
     * 归一化过程中的临时文件目录。
     *
     * 刻意放 `externalCacheDir` 而不是 `cacheDir`：它和 [root] 在**同一个存储卷**上，
     * [adopt] 才能用 `renameTo` 零拷贝地接管文件。放内部 `cacheDir` 的话跨卷 rename 会失败，
     * 退化成整份复制一遍——一个 200MB 的视频就是白写 200MB。
     *
     * 也刻意**不放在 [root] 底下**：[gc] 会把 root 里没被引用的文件全删，
     * 而正在转码的中间产物恰好符合「没被引用」这个描述。
     */
    val tempDir: File
        get() = File(context.externalCacheDir ?: context.cacheDir, TEMP_DIR)
            .also { if (!it.exists()) it.mkdirs() }

    /** 开一个新的临时文件路径给归一化写。 */
    fun newTempFile(ext: String): File = File(tempDir, "${UUID.randomUUID()}.$ext")

    /**
     * 接管一个归一化好的临时文件，落到 `<characterId>/<uuid>.<ext>`。
     *
     * 成功后 [NormalizedMedia.file] **已经不存在了**（被 rename 走），调用方不要再用它。
     * 失败时临时文件也已经被清掉。
     *
     * @param sourceRelPath 派生物填父附件的 relPath，父附件自己填 null。
     * @param orig 用户原本给的形态，写进 `origMime` / `origByteSize`。
     */
    fun adopt(
        characterId: String,
        media: NormalizedMedia,
        orig: OriginalForm,
        sourceRelPath: String? = null,
    ): AttachmentRef? {
        val dir = File(root, characterId).also { if (!it.exists()) it.mkdirs() }
        val name = "${UUID.randomUUID()}.${media.ext}"
        val target = File(dir, name)
        // 长度必须在 move 之前取：move 成功之后 media.file 就不存在了，length() 会返回 0
        val size = media.file.length()

        if (!move(media.file, target)) {
            media.file.delete()
            return null
        }
        return AttachmentRef(
            kind = media.kind,
            // 相对路径，不是 absolutePath —— 见 AttachmentRef 的第 1 条纪律
            relPath = "$characterId/$name",
            mime = media.mime,
            byteSize = size,
            width = media.width,
            height = media.height,
            durationMs = media.durationMs,
            sampleRate = media.sampleRate,
            channels = media.channels,
            sourceRelPath = sourceRelPath,
            posMs = media.posMs,
            origMime = orig.mime,
            origByteSize = orig.byteSize,
        )
    }

    /**
     * 相对路径 → 真实文件。**文件不在时返回 null**——专属目录的内容用户能在系统设置里
     * 清掉，调用方要能降级成文本占位符而不是崩。
     */
    fun resolve(relPath: String): File? =
        File(root, relPath).takeIf { it.isFile }

    /** 删掉这些相对路径对应的文件。删角色历史时配合 [com.chatwaifu.log.ChatLogRepository.attachmentPathsOf]。 */
    fun deletePaths(relPaths: Collection<String>) {
        relPaths.forEach { path ->
            val file = File(root, path)
            if (file.isFile && !file.delete()) Log.w(TAG, "delete failed: $path")
        }
        pruneEmptyDirs()
    }

    /**
     * 孤儿文件回收：磁盘上不在 [referenced] 里的附件文件全删。
     *
     * [referenced] 必须是**全库**的引用集合（`referencedAttachmentPaths()`），
     * 按角色算会把别人的文件误判成孤儿。
     *
     * @return 删掉几个文件。
     */
    fun gc(referenced: Set<String>): Int {
        var deleted = 0
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(root).path
                if (rel !in referenced) {
                    if (file.delete()) deleted++ else Log.w(TAG, "gc: delete failed: $rel")
                }
            }
        pruneEmptyDirs()
        if (deleted > 0) Log.i(TAG, "gc removed $deleted orphan attachment file(s)")
        return deleted
    }

    /**
     * 清掉上次残留的临时文件。
     *
     * 转码中途被杀进程会在 [tempDir] 里留下半截文件，而那个目录**不在 [gc] 的覆盖范围内**
     * （见 [tempDir] 的注释），所以要单独扫。启动时跟 [gc] 一起调一次。
     */
    fun clearTemp(): Int {
        val files = tempDir.listFiles()?.filter { it.isFile } ?: return 0
        val deleted = files.count { it.delete() }
        if (deleted > 0) Log.i(TAG, "cleared $deleted stale temp file(s)")
        return deleted
    }

    // ---- 内部实现 ----

    /**
     * 先 rename，失败退回复制。
     *
     * 正常路径下 [tempDir] 和 [root] 同卷，rename 是一次目录项改写、零字节拷贝。
     * 退路存在是因为 `externalCacheDir` 可能为 null（外部存储不可用），
     * 这时临时文件在内部存储上，跨卷 rename 一定失败。
     */
    private fun move(from: File, to: File): Boolean {
        if (from.renameTo(to)) return true
        Log.d(TAG, "rename failed (likely cross-volume), falling back to copy")
        return try {
            from.inputStream().use { input ->
                to.outputStream().use { output -> input.copyTo(output) }
            }
            from.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "adopt failed: cannot move ${from.name}", e)
            to.delete()
            false
        }
    }

    /** 删文件之后留下的空角色目录顺手清掉，不然根目录会越来越脏。 */
    private fun pruneEmptyDirs() {
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.listFiles()?.isEmpty() == true) dir.delete()
        }
    }

    companion object {
        private const val TAG = "AttachmentStore"
        private const val DIR = "attachments"
        private const val TEMP_DIR = "attach-tmp"
    }
}
