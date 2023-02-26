package com.chatwaifu.vits.utils.permission

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {
    // request storage permission
    fun requestStoragePermission(activity: Activity){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
            // read permission
            val readRequest = Manifest.permission.READ_EXTERNAL_STORAGE

            // write permission
            val writeRequest = Manifest.permission.WRITE_EXTERNAL_STORAGE

            ActivityCompat.requestPermissions(
                activity,
                arrayOf(readRequest, writeRequest),
                1024
            )
        } else {
            AlertDialog.Builder(activity).apply {
                setTitle("警告")
                setMessage("是否允许文件权限？")
                setCancelable(false)
                setPositiveButton("是") { dialog, which ->
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    activity.startActivity(intent)
                }
                setNegativeButton("否") { dialog, which ->
                    activity.finish()
                }
                show()
            }
        }
    }

    fun requestNetPermission(activity: Activity){
        val request = Manifest.permission.INTERNET
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(request),
            1025
        )
    }

    // check storage permission
    fun checkStoragePermission(activity: Activity): Boolean{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // read permission
            val readRequest = Manifest.permission.READ_EXTERNAL_STORAGE
            val permissionRead = ContextCompat.checkSelfPermission(activity, readRequest)

            // write permission
            val writeRequest = Manifest.permission.WRITE_EXTERNAL_STORAGE
            val permissionWrite = ContextCompat.checkSelfPermission(activity, writeRequest)

            return (permissionRead == PackageManager.PERMISSION_GRANTED
                    && permissionWrite == PackageManager.PERMISSION_GRANTED)

        } else {
            return Environment.isExternalStorageManager()
        }
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