package com.chatwaifu.mobile.ui.modelmanager

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.model.ImportError
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

class ModelManagerFragment : Fragment() {

    private val activityViewModel: ChatActivityViewModel by activityViewModels()
    private val fragmentViewModel: ModelManagerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(inflater.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContent {
            // 用 LocalResources 而不是 LocalContext.current.getString：
            // 后者在配置变更（切语言/深色模式）时不会让 composition 重新读资源
            val resources = LocalResources.current
            val uiState by fragmentViewModel.uiState.collectAsStateWithLifecycle()

            // 只读一个 zip，不需要持久化授权：内容当场解压落地到应用专属目录，
            // 之后再也不需要回去访问这个 uri
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> uri?.let { fragmentViewModel.import(it) } }

            LaunchedEffect(Unit) {
                fragmentViewModel.events.collect { event ->
                    when (event) {
                        is ModelManagerEvent.ImportSucceeded -> {
                            showToast(
                                resources.getString(R.string.model_manager_import_done, event.name)
                            )
                        }

                        is ModelManagerEvent.ImportFailed ->
                            showToast(event.error.toMessage(resources))

                        is ModelManagerEvent.Deleted -> {
                            showToast(
                                resources.getString(R.string.model_manager_delete_done, event.name)
                            )
                        }

                        ModelManagerEvent.ConfigSaved ->
                            showToast(resources.getString(R.string.model_manager_saved))
                    }
                }
            }

            ChatWaifu_MobileTheme {
                ModelManagerContent(
                    uiState = uiState,
                    onNavIconPressed = { activityViewModel.openDrawer() },
                    onImportClick = {
                        // 不少文件管理器把 zip 报成 octet-stream，两个 mime 都放开，
                        // 实际是不是 zip 由 ZipModelImporter 校验
                        picker.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onDelete = fragmentViewModel::delete,
                    onSaveConfig = fragmentViewModel::saveCharacterConfig,
                    systemPromptOf = fragmentViewModel::getSystemPrompt,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentViewModel.refresh()
    }
}

private fun ImportError.toMessage(resources: Resources): String = when (this) {
    ImportError.NotAZip -> resources.getString(R.string.import_error_not_zip)
    ImportError.NoLive2DEntry -> resources.getString(R.string.import_error_no_live2d)
    is ImportError.MultipleLive2DEntries ->
        resources.getString(R.string.import_error_multiple_live2d, candidates.joinToString())

    is ImportError.InvalidVitsConfig ->
        resources.getString(R.string.import_error_invalid_vits, reason)

    is ImportError.NameConflict -> resources.getString(R.string.import_error_name_conflict, name)
    is ImportError.Unsafe -> resources.getString(R.string.import_error_unsafe, detail)
    is ImportError.Io -> resources.getString(R.string.import_error_io, message)
}
