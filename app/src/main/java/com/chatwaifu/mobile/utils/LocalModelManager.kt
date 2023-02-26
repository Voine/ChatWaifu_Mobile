package com.chatwaifu.mobile.utils

import android.content.Context
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.Constant.LIVE2D_BASE_PATH
import com.chatwaifu.mobile.data.Constant.LOCAL_MODEL_HIYORI
import com.chatwaifu.mobile.data.Constant.LOCAL_MODEL_KURISU
import com.chatwaifu.mobile.data.Constant.VITS_BASE_PATH
import com.chatwaifu.mobile.safeResume
import com.chatwaifu.mobile.ui.ChannelListBean
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.vits.utils.file.copyAssets2Local
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

/**
 * Description: LocalModelManager
 * Author: Voine
 * Date: 2023/2/25
 */
class LocalModelManager {

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
            name == LOCAL_MODEL_HIYORI -> R.drawable.hiyori_head
            name == LOCAL_MODEL_KURISU -> R.drawable.kurisu_head
            else -> R.drawable.external_default_icon
        }
    }


    fun initExternalModel(context: Context, finalModelList: MutableList<ChannelListBean>) {
        val chatWaifuExternalDir = "${context.getExternalFilesDir(null)}"
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
                    characterVitsPath = relativeVITSModel?.absolutePath ?: ""
                )
            )
        }
    }
}