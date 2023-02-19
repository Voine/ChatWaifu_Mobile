package com.chatwaifu.vits.utils.file

import android.content.Context
import java.io.File

/**
 * @param src asset file
 * @param des target dir
 */
fun Context.copyAssets2Local(
        deleteIfExists: Boolean = true,
        srcPath: String,
        desPath: String,
        callBack: ((isSuccess: Boolean, absPath: String) -> Unit)? = null
) {
    val localFile = File(desPath, srcPath)
    if (deleteIfExists && localFile.exists()) {
        FileUtils.deleteDirectory(localFile)
    }

    try {
        val targetDir = File(desPath)
        FileUtils.copyAssets(
            assets,
            srcPath,
            targetDir.absolutePath,
            ByteArray(FileUtils.DEFAULT_BUFFER)
        )
        callBack?.invoke(true, targetDir.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        FileUtils.deleteDirectory(localFile)
        callBack?.invoke(false, "")
    }
}