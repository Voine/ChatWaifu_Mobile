package com.chatwaifu.mobile.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.Constant.LIVE2D_BASE_PATH
import com.chatwaifu.mobile.data.Constant.LOCAL_MODEL_ATRI
import com.chatwaifu.mobile.data.Constant.LOCAL_MODEL_YUUKA
import com.chatwaifu.mobile.data.Constant.LOCAL_MODEL_AMADEUS
import com.chatwaifu.mobile.data.Constant.VITS_BASE_PATH
import com.chatwaifu.mobile.safeResume
import com.chatwaifu.mobile.ui.channellist.ChannelListBean
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.vits.utils.file.copyAssets2Local
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

/**
 * Description: LocalModelManager
 * Author: Voine
 * Date: 2023/2/25
 */
class LocalModelManager() {
    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(
            Constant.SAVED_STORE,
            Context.MODE_PRIVATE
        )
    }

    suspend fun initInnerModel(context: Context, finalModelList: MutableList<ChannelListBean>) {
        // copy live2d
        val live2DModelList = context.assets.list(LIVE2D_BASE_PATH)
        live2DModelList?.forEach { modelName ->
            val srcPath = "$LIVE2D_BASE_PATH/$modelName"
            suspendCancellableCoroutine {
                ChatWaifuApplication.context.copyAssets2Local(
                    deleteIfExists = true,
                    srcPath = srcPath,
                    desPath = ChatWaifuApplication.baseAppDir + File.separator
                ) { isSuccess: Boolean, absPath: String ->
                    if (!isSuccess) {
                        showToast("copy  live2d $modelName to disk failed....")
                        return@copyAssets2Local
                    }
                    finalModelList.add(
                        ChannelListBean(
                            avatarDrawable = getAvatarDrawableFromName(modelName),
                            characterName = modelName,
                            characterPath = ChatWaifuApplication.baseAppDir + File.separator + srcPath
                        )
                    )
                }
                it.safeResume(Unit)
            }
        }

        //copy vits
        val vitsModelList = context.assets.list(VITS_BASE_PATH)
        vitsModelList?.forEach { vitsName ->
            val srcPath = "$VITS_BASE_PATH/$vitsName"
            suspendCancellableCoroutine {
                ChatWaifuApplication.context.copyAssets2Local(
                    deleteIfExists = true,
                    srcPath = srcPath,
                    desPath = ChatWaifuApplication.baseAppDir + File.separator
                ) { isSuccess: Boolean, absPath: String ->
                    if (!isSuccess) {
                        showToast("copy vits $vitsName to disk failed....")
                        return@copyAssets2Local
                    }
                    finalModelList.find { target ->
                        target.characterName == vitsName
                    }.apply {
                        this?.characterVitsPath =
                            ChatWaifuApplication.baseAppDir + File.separator + srcPath
                    }
                }
                it.safeResume(Unit)
            }
        }
    }

    private fun getAvatarDrawableFromName(name: String): Int {
        return when {
            name == LOCAL_MODEL_YUUKA -> R.drawable.yuuka_head
            name == LOCAL_MODEL_AMADEUS -> R.drawable.kurisu_head
            name == LOCAL_MODEL_ATRI -> R.drawable.atri_head
            else -> R.drawable.external_default_icon
        }
    }


    fun initExternalModel(context: Context, finalModelList: MutableList<ChannelListBean>) {
        val chatWaifuExternalDir = Environment.getExternalStorageDirectory().path
        val externalLive2dModels =
            File(chatWaifuExternalDir, Constant.EXTEND_LIVE2D_PATH).listFiles()
        val externalVITSModels = File(chatWaifuExternalDir, Constant.EXTEND_VITS_PATH).listFiles()
        externalLive2dModels?.forEach { live2dModel ->
            val relativeVITSModel = externalVITSModels?.find { it.name == live2dModel.name }
            finalModelList.add(
                ChannelListBean(
                    avatarDrawable = R.drawable.external_default_icon,
                    characterPath = live2dModel.absolutePath,
                    characterName = live2dModel.name,
                    characterVitsPath = relativeVITSModel?.absolutePath ?: "",
                    fromExternal = true
                )
            )
        }
    }

    fun getModelSystemSetting(modelName: String): String? {
        return when (modelName) {
            LOCAL_MODEL_YUUKA -> {
                sp.getString(Constant.SAVED_YUUKA_SETTING, null)?.let {
                    it.ifBlank { null }
                } ?:  ChatWaifuApplication.context.resources.getString(R.string.default_system_yuuka)
            }
            LOCAL_MODEL_AMADEUS -> {
                sp.getString(Constant.SAVED_AMADEUS_SETTING, null)?.let {
                    it.ifBlank { null }
                } ?:  ChatWaifuApplication.context.resources.getString(R.string.default_system_amadeus)
            }
            LOCAL_MODEL_ATRI -> {
                sp.getString(Constant.SAVED_ATRI_SETTING, null)?.let {
                    it.ifBlank { null }
                } ?: ChatWaifuApplication.context.resources.getString(R.string.default_system_atri)
            }
            else -> {
                sp.getString(Constant.SAVED_EXTERNAL_SETTING, null)?.let {
                    it.ifBlank { null }
                }
            }
        }
    }

    /**
     * 由于奇妙的权限问题，即使申请了权限， listFile 也返回空数组，但指定具体路径又能找得到文件，所以暂时这么处理
     */
    fun getVITSModelFiles(basePath: String): List<File>? {
        val files = File(basePath).listFiles()
        if (!files.isNullOrEmpty()) {
            return files.toList()
        }
        val resultList = mutableListOf<File>()
        vitsFileArr.forEach {
            val file = File(basePath, it)
            if (file.exists()) {
                resultList.add(file)
            }
        }
        return resultList
    }


    fun getDefaultModelPosition(modelName: String): List<Float> {
        val list: List<Float>
        when (modelName) {
            Constant.LOCAL_MODEL_YUUKA -> {
                list = listOf(0f, -0.6f, 4f)
            }

            Constant.LOCAL_MODEL_ATRI -> {
                list = listOf(0f, -0.6f, 3f)
            }

            Constant.LOCAL_MODEL_AMADEUS -> {
                list = listOf(0f, 0f, 2f)
            }

            else -> {
                list = listOf(0f, 0f, 1f)
            }
        }
        return list
    }

    fun getChatLogExternalItemList(): List<String> {
        val chatWaifuExternalDir = Environment.getExternalStorageDirectory().path
        val externalLive2dNames =
            File(chatWaifuExternalDir, Constant.EXTEND_LIVE2D_PATH).listFiles()?.map { it.name }
        return externalLive2dNames ?: emptyList()
    }

    /**
     * if external model has multi speaker, get speaker id from setting
     */
    fun getVITSSpeakerId(currentLive2DModelName: String): Int {
        return when (currentLive2DModelName) {
            Constant.LOCAL_MODEL_YUUKA,
            Constant.LOCAL_MODEL_AMADEUS,
            Constant.LOCAL_MODEL_ATRI -> 0
            else -> {
                sp.getInt(Constant.SAVED_EXTERNAL_MODEL_SPEAKER_ID, 0)
            }
        }
    }

    companion object {
        val vitsFileArr= listOf(
            "config.json",
            "dec.ncnn.bin",
            "dp.ncnn.bin",
            "flow.ncnn.bin",
            "flow.reverse.ncnn.bin",
            "emb_g.bin",
            "emb_t.bin",
            "enc_p.ncnn.bin",
            "enc_q.ncnn.bin"
        )
    }
}