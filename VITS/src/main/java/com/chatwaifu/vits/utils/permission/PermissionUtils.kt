package com.chatwaifu.vits.utils.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/**
 * 只剩录音和网络两个权限。
 * 存储权限已随「模型迁到应用专属目录 + SAF 导入」一起移除 —— targetSdk >= 33 后
 * 非媒体文件本来也没有运行时权限能授予任意路径访问。
 */
object PermissionUtils {
    fun requestNetPermission(activity: Activity){
        val request = Manifest.permission.INTERNET
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(request),
            1025
        )
    }

    fun checkNetPermission(activity: Activity): Boolean {
        val netRequest = Manifest.permission.INTERNET
        val permissionRecord = ActivityCompat.checkSelfPermission(activity, netRequest)
        return permissionRecord == PackageManager.PERMISSION_GRANTED
    }

    // request record permission
    fun requestRecordPermission(activity: Activity){
        // record permission
        val recordRequest = Manifest.permission.RECORD_AUDIO
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(recordRequest),
            1024
        )
    }

    // check record permission
    fun checkRecordPermission(activity: Activity):Boolean{
        val recordRequest = Manifest.permission.RECORD_AUDIO
        val permissionRecord = ActivityCompat.checkSelfPermission(activity, recordRequest)
        return permissionRecord == PackageManager.PERMISSION_GRANTED
    }
}