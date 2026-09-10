package com.chatwaifu.mobile.data.model

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Description: 从用户选中的压缩包导入一个角色模型。
 *
 * 之所以要「导入」而不是像以前那样直接读 /sdcard/chatwaifu/：
 * 推理引擎在 native 侧是按**文件路径**加载模型的（老的 ncnn 是 fopen 那些 `.bin`，
 * 换成 Bert-VITS2-MNN 之后是 `setBertVITS2ModelPath` 收 `.mnn` 的绝对路径），
 * content:// URI 两者都喂不进去，所以必须先落地成应用专属目录下的真实路径。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
interface ModelImporter {

    /**
     * 导入 [source] 指向的 zip。返回的 Flow 会持续发进度，
     * 以 [ImportProgress.Success] 或 [ImportProgress.Failed] 结束。
     *
     * 实现不持有 UI，取消 Flow 即取消导入并清理中间产物。
     */
    fun import(source: Uri): Flow<ImportProgress>
}

sealed interface ImportProgress {
    /** 解压中，[percent] 为 0..100；zip 未提供总大小时可能长时间停在同一个值 */
    data class Extracting(val percent: Int) : ImportProgress

    /** 解压完成，正在校验目录结构和 config.json */
    data object Validating : ImportProgress

    data class Success(val model: CharacterModel) : ImportProgress

    data class Failed(val error: ImportError) : ImportProgress
}

/**
 * 每个分支都要能在 UI 上给出「用户下一步该干什么」的提示，
 * 所以按失败原因分类而不是统一抛一个 message。
 */
sealed interface ImportError {
    /** 选中的文件不是合法 zip */
    data object NotAZip : ImportError

    /** 包里找不到 *.model3.json，不是一个 live2d 模型 */
    data object NoLive2DEntry : ImportError

    /** 包里有多个 *.model3.json，无法确定用哪个当入口 */
    data class MultipleLive2DEntries(val candidates: List<String>) : ImportError

    /** 有 vits 目录但 config.json 不合法（cleaner 不支持 / symbols 为空 / 解析失败） */
    data class InvalidVitsConfig(val reason: String) : ImportError

    /** 已存在同名角色 */
    data class NameConflict(val name: String) : ImportError

    /** 包内有指向目录外的路径（zip slip），或超出体积/条目上限 */
    data class Unsafe(val detail: String) : ImportError

    data class Io(val message: String) : ImportError
}
