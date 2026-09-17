package com.chatwaifu.mobile.ui.modelmanager

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.ui.showToast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Description: Persona / Voice 两页在两个宿主 Fragment 里的共同接线。
 *
 * `ModelManagerContent` 有两个宿主（`ChannelListFragment` 是正式的角色页，
 * `ModelManagerFragment` 是抽屉里的模型管理页），profile 的事件处理、返回键逐级回退
 * 和试听转交在两边完全一致，所以收在这里一份 —— 两边各写一遍迟早会漂。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
@Composable
internal fun rememberCharacterProfileActions(
    uiState: ModelManagerUiState,
    viewModel: ModelManagerViewModel,
    activityViewModel: ChatActivityViewModel,
): CharacterProfileActions {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // 当前试听协程。持有它才能在「停止」和离开页面时打断挂起中的播放
    val previewJob = remember { PreviewJobHolder() }

    LaunchedEffect(viewModel) {
        viewModel.profileEvents.collect { event ->
            when (event) {
                CharacterProfileEvent.PersonaSaved ->
                    showToast(resources.getString(R.string.persona_saved))

                CharacterProfileEvent.PersonaReset ->
                    showToast(resources.getString(R.string.persona_reset_done))

                CharacterProfileEvent.VoiceSaved ->
                    showToast(resources.getString(R.string.voice_saved))

                // TTS 归 activity 级 ViewModel 持有（就是正在服务聊天的那一份 helper），
                // 这里只做转交，模型管理页的 ViewModel 不碰 native
                is CharacterProfileEvent.PreviewRequested -> {
                    val text = resources.getString(R.string.voice_preview_text)
                    // 必须另起协程：previewVoice 会挂起到整段播完，
                    // 在收集器里直接 await 会堵死事件流，后面的「停止」永远收不到
                    previewJob.job = scope.launch {
                        val played = activityViewModel.previewVoice(event.character, text)
                        previewJob.job = null
                        viewModel.onPreviewFinished(unavailable = !played)
                    }
                }

                CharacterProfileEvent.PreviewStopRequested -> {
                    previewJob.job?.cancel()
                    previewJob.job = null
                    activityViewModel.stopVoicePreview()
                }

                CharacterProfileEvent.PreviewUnavailable ->
                    showToast(resources.getString(R.string.voice_preview_unavailable))
            }
        }
    }

    // 三级路由在 Compose state 里，返回键要逐级回退；
    // 否则一次返回直接退出整个角色页，人格页的未保存提示也就没机会出现
    BackHandler(enabled = uiState.selectedCharacterId != null) {
        if (uiState.detailRoute == CharacterDetailRoute.DETAIL) {
            viewModel.closeDetail()
        } else {
            // 和页内返回箭头同一个入口，未保存确认才不会被返回键绕过
            viewModel.requestLeaveProfile()
        }
    }

    return remember(viewModel) {
        CharacterProfileActions(
            openPersona = viewModel::openPersona,
            openVoice = viewModel::openVoice,
            back = viewModel::requestLeaveProfile,
            dismissDiscard = viewModel::dismissDiscardPrompt,
            discardAndLeave = viewModel::discardAndLeave,
            editPersonaName = viewModel::editPersonaName,
            editPersonaPrompt = viewModel::editPersonaPrompt,
            savePersona = viewModel::savePersona,
            resetPersona = viewModel::resetPersona,
            editVoiceName = viewModel::editVoiceName,
            selectSpeaker = viewModel::selectSpeaker,
            saveVoice = viewModel::saveVoice,
            togglePreview = viewModel::requestPreview,
        )
    }
}

private class PreviewJobHolder(var job: Job? = null)
