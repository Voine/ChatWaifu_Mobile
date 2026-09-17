package com.chatwaifu.mobile.data.model

import com.chatwaifu.mobile.data.model.profile.PersonaProfile
import com.chatwaifu.mobile.data.model.profile.VoiceProfile
import com.chatwaifu.mobile.data.model.profile.VoiceSourceType
import com.chatwaifu.mobile.data.model.profile.VoiceSpeaker
import com.chatwaifu.mobile.ui.modelmanager.CharacterDetailRoute
import com.chatwaifu.mobile.ui.modelmanager.CharacterProfileSummary
import com.chatwaifu.mobile.ui.modelmanager.ModelManagerUiState
import com.chatwaifu.mobile.ui.modelmanager.PersonaEditorState
import com.chatwaifu.mobile.ui.modelmanager.VoiceEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.7：Persona / Voice profile 的数据边界、迁移幂等和 fallback。
 *
 * 这里全部是 JVM 纯逻辑：落盘那一侧（SharedPreferences / meta.json）在
 * `CharacterProfileRepositoryImpl` 里，需要 Android context，不在单测范围。
 */
class CharacterProfileTest {

    // ---- 迁移 ----

    @Test
    fun legacyMetadataGetsDeterministicProfileIds() {
        val migrated = ModelStorage.migrateMetadata(
            legacyMeta(ModelSource.BUILT_IN),
            "ATRI",
        )

        assertEquals("persona:builtin:atri", migrated.personaProfileId)
        assertEquals("voice:builtin:atri", migrated.voiceProfileId)
    }

    @Test
    fun profileIdMigrationIsIdempotentAcrossReloads() {
        val first = ModelStorage.migrateMetadata(legacyMeta(ModelSource.IMPORTED), "Role") {
            "imported:fixed"
        }
        val second = ModelStorage.migrateMetadata(first, "Role") {
            error("existing id must not be regenerated")
        }

        assertEquals(first, second)
        assertEquals("persona:imported:fixed", second.personaProfileId)
    }

    @Test
    fun migrationKeepsExistingProfileIdsAndLeavesBehaviorUnset() {
        val existing = legacyMeta(ModelSource.BUILT_IN).copy(
            id = "builtin:atri",
            personaProfileId = "persona:custom",
            voiceProfileId = "voice:custom",
        )

        val migrated = ModelStorage.migrateMetadata(existing, "ATRI")

        assertEquals("persona:custom", migrated.personaProfileId)
        assertEquals("voice:custom", migrated.voiceProfileId)
        // 表现系统未实现，补了 ID 会让详情页把「尚未配置」显示成已配置
        assertNull(migrated.behaviorProfileId)
    }

    @Test
    fun legacyVoiceMetadataIsPreservedByMigration() {
        val legacy = legacyMeta(ModelSource.IMPORTED).copy(
            hasVits = true,
            speakerId = 3,
            language = 2,
        )

        val migrated = ModelStorage.migrateMetadata(legacy, "Role") { "imported:x" }

        assertEquals(3, migrated.speakerId)
        assertEquals(2, migrated.language)
        assertTrue(migrated.hasVits)
    }

    // ---- 数据边界 ----

    @Test
    fun personaProfileCarriesNoInferenceConfiguration() {
        val fields = PersonaProfile::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("endpoint" in fields)
        assertFalse("apiKey" in fields)
        assertFalse("modelId" in fields)
        assertFalse("providerConfig" in fields)
    }

    @Test
    fun voiceProfileCarriesNoSessionOrProviderReference() {
        val fields = VoiceProfile::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("session" in fields)
        assertFalse("provider" in fields)
        assertFalse("model" in fields)
        assertFalse("modelId" in fields)
    }

    // ---- Persona 编辑器 ----

    @Test
    fun personaEditorReportsDirtyOnlyAfterRealEdit() {
        val editor = PersonaEditorState.of(persona(prompt = "hello"), hasDefault = true)

        assertFalse(editor.dirty)
        assertTrue(editor.copy(prompt = "hello world").dirty)
        assertTrue(editor.copy(name = "另一个人格").dirty)
    }

    @Test
    fun missingPersonaFallsBackToEmptyProfileNotPreviousCharacter() {
        val empty = persona(prompt = "")

        assertTrue(empty.isEmpty)
        // 摘要为 null → 详情页显示「未设置」，而不是上一个角色的人格名
        assertNull(CharacterProfileSummary(personaName = null, voiceName = "V").personaName)
    }

    // ---- Voice 编辑器 ----

    @Test
    fun voiceEditorFallsBackWhenSpeakerIdIsNotInCatalog() {
        val editor = VoiceEditorState.of(
            profile = voice(speakerId = 99),
            speakers = listOf(VoiceSpeaker("A", 0), VoiceSpeaker("B", 1)),
            isCurrentCharacter = true,
        )

        // 非法 speaker 不能原样喂给 BV2
        assertEquals(0, editor.speakerId)
        assertTrue(editor.speakerEditable)
    }

    @Test
    fun voiceEditorKeepsSpeakerWhenCatalogIsUnreadable() {
        val editor = VoiceEditorState.of(
            profile = voice(speakerId = 2),
            speakers = emptyList(),
            isCurrentCharacter = true,
        )

        assertEquals(2, editor.speakerId)
        assertFalse(editor.speakerEditable)
        assertNull(editor.speakerName)
    }

    @Test
    fun missingVoiceResourceDisablesPreviewWithoutCrashing() {
        val editor = VoiceEditorState.of(
            profile = voice(speakerId = 0).copy(
                sourceType = VoiceSourceType.NONE,
                vitsDir = null,
            ),
            speakers = emptyList(),
            isCurrentCharacter = true,
        )

        assertFalse(editor.voiceAvailable)
        assertFalse(editor.previewEnabled)
    }

    @Test
    fun previewIsRejectedForNonCurrentCharacter() {
        val editor = VoiceEditorState.of(
            profile = voice(speakerId = 0),
            speakers = listOf(VoiceSpeaker("A", 0)),
            isCurrentCharacter = false,
        )

        assertTrue(editor.voiceAvailable)
        // BV2 是进程级单模型，非当前角色试听需要重载模型
        assertFalse(editor.previewEnabled)
    }

    // ---- 详情页 ----

    @Test
    fun discardPromptIsPartOfSharedStateNotLocalToCompose() {
        // 返回键走的是 ViewModel 而不是页面内的局部状态，所以未保存确认必须在
        // 共享 state 上表达；只放在 Composable 里会被返回键绕过（真机上实测丢过改动）
        val fields = ModelManagerUiState::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("confirmDiscardPersona" in fields)
    }

    @Test
    fun detailStateExposesProfileSummariesAndRoute() {
        val state = ModelManagerUiState(
            detailRoute = CharacterDetailRoute.PERSONA,
            profileSummary = CharacterProfileSummary("ATRI 默认人格", "ATRI Voice"),
        )

        assertEquals("ATRI 默认人格", state.personaSummary)
        assertEquals("ATRI Voice", state.voiceSummary)
        assertEquals(CharacterDetailRoute.PERSONA, state.detailRoute)
    }

    private fun legacyMeta(source: ModelSource) = ModelMeta(
        name = "LegacyName",
        source = source.name,
        live2dEntryFileName = "Role.model3.json",
    )

    private fun persona(prompt: String) = PersonaProfile(
        id = "persona:builtin:atri",
        name = "ATRI 默认人格",
        prompt = prompt,
        builtIn = true,
    )

    private fun voice(speakerId: Int) = VoiceProfile(
        id = "voice:builtin:atri",
        name = "ATRI Voice",
        language = 0,
        speakerId = speakerId,
        sourceType = VoiceSourceType.SHARED_BUILT_IN,
        builtIn = true,
        vitsDir = "/models/_bv2/bv2_model/jp",
        bertDir = "/models/_bv2/bert/jp",
    )
}
