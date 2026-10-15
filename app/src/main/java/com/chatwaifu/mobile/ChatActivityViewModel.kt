package com.chatwaifu.mobile

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.CharacterRepository
import com.chatwaifu.mobile.data.model.ModelProvider
import com.chatwaifu.vits.utils.SoundGenerateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Description: 二级页面宿主（[ChatActivity]）的共享状态。
 *
 * 2.0 起聊天本身归 Companion（`CompanionActivity` + `CompanionRealResponseDriver`），
 * 这里**不再有对话链路**：以前的 `mainLoop()`（`while(true)` + continuation 唤醒）
 * 和它那套 session / 翻译 / TTS 编排已经删掉。剩下的只有几个 Fragment 共用的东西：
 * 当前角色、角色列表、抽屉开关，以及语音设置页的试听。
 *
 * Author: Voine
 * Date: 2023/2/18（2026/10 收缩为二级页面宿主）
 */
class ChatActivityViewModel : ViewModel() {
    companion object {
        private const val TAG = "ChatActivityViewModel"
    }

    val drawerShouldBeOpened = MutableLiveData<Boolean>()

    private val characterRepository: CharacterRepository by lazy {
        ModelProvider.repository(ChatWaifuApplication.context)
    }

    private val _currentCharacter = MutableStateFlow<CharacterModel?>(null)

    /** 当前选中的角色。记忆页按它隔离；进程刚起来时由 init 从仓库恢复 */
    val currentCharacter = _currentCharacter.asStateFlow()

    private val _characters = MutableStateFlow<List<CharacterModel>>(emptyList())
    val characters = _characters.asStateFlow()

    private val _navigateHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 角色页选定角色后回到 Companion。由 [ChatActivity] 收集，负责先释放 TTS 再跳转 */
    val navigateHome = _navigateHome.asSharedFlow()

    /**
     * 试听用的 TTS。**懒加载**：只有用户真的点了试听才初始化 BV2 loader，
     * 否则从 Companion 进来看一眼设置就要白付一次模型装载。
     */
    private val vitsHelper: SoundGenerateHelper by lazy {
        SoundGenerateHelper(ChatWaifuApplication.context)
    }
    private var vitsHelperCreated = false
    private val voiceMutex = Mutex()

    /** [vitsHelper] 里当前装着谁的声库。只在 [voiceMutex] 里读写 */
    private var loadedVoiceCharacterId: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _characters.value = characterRepository.loadCharacters()
            if (_currentCharacter.value == null) {
                _currentCharacter.value = characterRepository.getCurrentCharacter()
            }
        }
    }

    /**
     * 角色页 / 模型管理页选定了角色（仓库里的「当前角色」已经由调用方写好）。
     *
     * @param returnHome true = 选完就回 Companion（角色页的语义）；
     *   模型管理页只是改个标记，留在原页
     */
    fun selectCharacter(character: CharacterModel, returnHome: Boolean) {
        _currentCharacter.value = character
        if (returnHome) _navigateHome.tryEmit(Unit)
    }

    /**
     * 语音设置页的试听。
     *
     * BV2 的 loader 是**进程级单例**（`destroyBertVITS2Loader` 销毁的是全局那一份），
     * 所以这里和 Companion 的 TTS 不能同时活着 —— 能这样用的前提是 Companion 在跳过来之前
     * 已经 `awaitClosed()` 释放了自己那份，而回 Companion 之前 [releaseVoice] 会释放这一份。
     * 因为此时没有对话在跑，试听可以给**任何**角色装声库，不再限于当前角色。
     *
     * 不驱动口型：这个 Activity 里没有 Live2D。
     *
     * @return true 表示播完了；false 表示没有语音 / 模型装载失败 / 被打断
     */
    suspend fun previewVoice(character: CharacterModel, text: String): Boolean {
        if (!character.hasVoice) return false
        return voiceMutex.withLock {
            if (loadedVoiceCharacterId != character.id && !initVoiceModel(character)) {
                return@withLock false
            }
            vitsHelper.generateAndPlay(text = text) { _, _ -> }
        }
    }

    /** 必须在 [voiceMutex] 里调 */
    private suspend fun initVoiceModel(character: CharacterModel): Boolean {
        val vitsDir = character.vitsDir ?: return false
        vitsHelperCreated = true
        val success = vitsHelper.init(
            bv2Dir = vitsDir,
            bertDir = character.bertDir,
            language = character.language,
            targetSpeakerId = character.speakerId,
        )
        loadedVoiceCharacterId = character.id.takeIf { success }
        return success
    }

    /** 试听页退出 / 重复点击时立刻停掉当前 AudioTrack 轮次。 */
    fun stopVoicePreview() {
        if (vitsHelperCreated) vitsHelper.cancelPlayback()
    }

    /**
     * 回 Companion 之前调，**必须等它返回再起新 Activity**：
     * 否则 Companion 刚装好的 loader 会被这边迟到的 `onCleared` 销毁。
     */
    suspend fun releaseVoice() = withContext(NonCancellable + Dispatchers.IO) {
        if (!vitsHelperCreated) return@withContext
        vitsHelper.cancelPlayback()
        voiceMutex.withLock {
            vitsHelper.clear()
            loadedVoiceCharacterId = null
            vitsHelperCreated = false
        }
        Log.d(TAG, "preview voice released")
    }

    override fun onCleared() {
        // 正常路径已经由 releaseVoice() 释放过；这里兜底的是「直接被系统回收」的情况
        if (vitsHelperCreated) vitsHelper.clear()
        // 不调 super.onCleared()：ViewModel.onCleared() 的实现是空的，调了触发 EmptySuperCall
    }

    fun openDrawer() {
        drawerShouldBeOpened.value = true
    }

    fun resetOpenDrawerAction() {
        drawerShouldBeOpened.value = false
    }
}
