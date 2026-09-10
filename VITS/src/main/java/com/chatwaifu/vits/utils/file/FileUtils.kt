package com.chatwaifu.vits.utils.file

import android.content.res.AssetManager
import android.util.Log
import com.chatwaifu.vits.data.Bv2Config
import com.google.gson.Gson
import java.io.*


object FileUtils {

    /**
     * 解析并校验 Bert-VITS2 的 config.json。
     *
     * 和老 VITS 的 config 不是一回事：老的靠 `symbols` + `text_cleaners` 驱动我们自己写的
     * 音素化（`ChineseTextUtils` / `JapaneseTextUtils`，已随 ncnn 引擎一起删掉），
     * BV2 的 G2P 在 `text-preprocess` 里做，config 只需要提供两样东西——
     * 采样率（要拿去配 AudioTrack）和 `spk2id`（多人模型的 speaker 表）。
     *
     * 失败原因回给调用方而不是在这里 Toast：模型导入要把它写进 ImportError.InvalidVitsConfig。
     */
    fun parseConfig(path: String): ConfigParseResult {
        return try {
            val configBuffer = File(path).bufferedReader().use { it.readText() }
            val config = Gson().fromJson(configBuffer, Bv2Config::class.java)
                ?: return ConfigParseResult.Failure("config.json 解析为空")
            val data = config.data
                ?: return ConfigParseResult.Failure("config.json 缺少 data 段")
            if (data.sampling_rate == null || data.sampling_rate <= 0) {
                return ConfigParseResult.Failure("config.json 缺少合法的 sampling_rate")
            }
            if (data.spk2id.isNullOrEmpty()) {
                return ConfigParseResult.Failure("config.json 缺少 spk2id，无法确定 speaker")
            }
            ConfigParseResult.Success(config)
        } catch (e: Exception) {
            Log.e(TAG, "parse config failed: $path", e)
            ConfigParseResult.Failure(e.message ?: "config.json 解析失败")
        }
    }

    private const val TAG = "FileUtils"

    @Throws(IOException::class)
    fun copy(src: File?, dst: File?) {
        val `in`: InputStream = FileInputStream(src)
        val out: OutputStream = FileOutputStream(dst)

        // Transfer bytes from in to out
        val buf = ByteArray(1024)
        var len: Int
        while (`in`.read(buf).also { len = it } > 0) {
            out.write(buf, 0, len)
        }
        `in`.close()
        out.close()
    }

    /**
     * Delete a directory and all its contents
     */
    fun deleteDirectory(fileOrDirectory: File) {
        val children = fileOrDirectory.listFiles()
        if (fileOrDirectory.isDirectory && children != null) {
            for (child in children) {
                deleteDirectory(child)
            }
        }
        fileOrDirectory.delete()
    }

    /**
     * Recursively copy the files in the Asset directory to rootDir
     * @param assets
     * @param path
     * @param rootDir
     * @throws IOException
     */
    @Throws(IOException::class)
    fun copyAssets(assets: AssetManager, path: String, rootDir: String, buffer: ByteArray?) {
        if (isAssetsDir(assets, path)) {
            val dir = File(rootDir + File.separator + path)
            check(!(!dir.exists() && !dir.mkdirs())) { "mkdir failed" }
            for (s in assets.list(path)!!) {
                copyAssets(assets, "$path/$s", rootDir, buffer)
            }
        } else {
            val input = assets.open(path)
            val dest = File(rootDir, path)
            copyToFileOrThrow(input, dest, buffer)
        }
    }

    fun isAssetsDir(assets: AssetManager, path: String?): Boolean {
        try {
            val files = assets.list(path!!)
            return files != null && files.size > 0
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    var DEFAULT_BUFFER = 64 * 1024

    @Throws(IOException::class)
    fun copyToFileOrThrow(inputStream: InputStream, destFile: File, buffer: ByteArray?) {
        var buffer = buffer
        if (destFile.exists()) {
            return
        }
        val file = destFile.parentFile
        if (file != null && !file.exists()) {
            file.mkdirs()
        }
        val out = FileOutputStream(destFile)
        try {
            if (buffer == null || buffer.size == 0) {
                buffer = ByteArray(DEFAULT_BUFFER)
            }
            // byte[] buffer = new byte[BUFFER];
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                out.write(buffer, 0, bytesRead)
            }
        } finally {
            out.flush()
            try {
                out.fd.sync()
            } catch (e: IOException) {
            }
            out.close()
        }
    }
}

/**
 * [FileUtils.parseConfig] 的结果。失败带上可直接展示给用户的原因。
 */
sealed interface ConfigParseResult {
    data class Success(val config: Bv2Config) : ConfigParseResult
    data class Failure(val reason: String) : ConfigParseResult
}
