package com.chatwaifu.vits.utils.file

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.chatwaifu.vits.data.Config
import com.google.gson.Gson
import java.io.*


object FileUtils {
    private val REQUEST_CODE_SELECT_MODEL = 1
    private val REQUEST_CODE_SELECT_CONFIG = 2

    fun getPathFromUri(context: Context, uri: Uri): String? {
        var path: String? = null
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            // android 8.0
            if (docId.startsWith("raw:")) {
                return docId.replaceFirst("raw:", "")
            }
            val splits = docId.split(":").toTypedArray()
            var type: String? = null
            var id: String? = null
            if (splits.size == 2) {
                type = splits[0]
                id = splits[1]
            }
            val contentUriPrefixesToTry = arrayOf(
                "content://downloads/public_downloads",
                "content://downloads/my_downloads",
                "content://downloads/all_downloads"
            )
            when (uri.authority) {
                "com.android.externalstorage.documents" -> if ("primary" == type) {
                    path =
                        Environment.getExternalStorageDirectory().toString() + File.separator + id
                }
                "com.android.providers.downloads.documents" -> {
                    if ("raw" == type) {
                        path = id
                    } else {
                        for (contentUriPrefix in contentUriPrefixesToTry) {
                            if (id != null) {
                                val contentUri = ContentUris.withAppendedId(
                                    Uri.parse(contentUriPrefix),
                                    id.toLong()
                                )
                                val t_path = getMediaPathFromUri(context, contentUri,
                                    null, null)
                                if (t_path != null) {
                                    path = t_path
                                    break
                                }
                            }
                        }
                    }
                }
                "com.android.providers.media.documents" -> {
                    var externalUri: Uri? = null
                    when (type) {
                        "image" -> externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> externalUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> externalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        "document" -> externalUri = MediaStore.Files.getContentUri("external")
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(id)
                    if (externalUri != null) {
                        path = getMediaPathFromUri(context, externalUri, selection, selectionArgs)
                    }
                }
            }
        } else if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
            path = getMediaPathFromUri(context, uri, null, null)
        } else if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
            path = uri.path
        }
        return if (path == null) null else if (File(path).exists()) path else null
    }

    private fun getMediaPathFromUri(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String?>?
    ): String? {
        var path: String?
        path = uri.path
        val sdPath = Environment.getExternalStorageDirectory().absolutePath
        if (!path!!.startsWith(sdPath)) {
            val sepIndex = path.indexOf(File.separator, 1)
            path = if (sepIndex == -1) null else {
                sdPath.toString() + path.substring(sepIndex)
            }
        }
        if (path == null || !File(path).exists()) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val cursor = resolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    try {
                        val index = cursor.getColumnIndexOrThrow(projection[0])
                        if (index != -1) path = cursor.getString(index)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                        path = null
                    } finally {
                        cursor.close()
                    }
                }
            }
        }
        return path
    }


    // parse and load config file
    fun parseConfig(context: Context, path: String): Config? {
        try {
            val configFile = File(path)
            val configStream = FileInputStream(configFile)
            val configBuffer = configStream.bufferedReader().use { it.readText() }
            configStream.close()
            val config = Gson().fromJson(configBuffer, Config::class.java)
            for (cleaner in config.data!!.text_cleaners!!) {
                if (cleaner !in listOf("japanese_cleaners", "japanese_cleaners2","chinese_cleaners")) {
                    Toast.makeText(context, "抱歉，目前还不支持此模型！", Toast.LENGTH_SHORT).show()
                    return null
                }
                if (config.symbols!!.isEmpty()) {
                    Toast.makeText(context, "配置文件必须包含symbols！", Toast.LENGTH_SHORT).show()
                    return null
                }
            }
            return config
        } catch (e: Exception) {
            Log.e("LoadConfig", e.message.toString())
            return null
        }
    }

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