package com.chatwaifu.mobile.data.model

import com.chatwaifu.mobile.ui.modelmanager.ModelManagerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPackageTest {

    @Test
    fun builtinIdIsStableAndIndependentFromDisplayName() {
        assertEquals("builtin:atri", ModelStorage.builtinCharacterId("ATRI"))
        assertEquals("builtin:atri", ModelStorage.builtinCharacterId("atri"))
    }

    @Test
    fun importedMigrationPersistsGeneratedIdAcrossReloads() {
        val legacy = legacyMeta(ModelSource.IMPORTED)
        val first = ModelStorage.migrateMetadata(legacy, "ImportedRole") {
            "imported:fixed-uuid"
        }
        val second = ModelStorage.migrateMetadata(first, "ImportedRole") {
            error("existing id must not be regenerated")
        }

        assertEquals("imported:fixed-uuid", first.id)
        assertEquals(first, second)
    }

    @Test
    fun metadataMigrationPreservesLegacyConfiguration() {
        val legacy = legacyMeta(ModelSource.IMPORTED).copy(
            speakerId = 7,
            language = 3,
            hasVits = true,
            sharedVoice = false,
        )

        val migrated = ModelStorage.migrateMetadata(legacy, "LegacyName") {
            "imported:legacy"
        }

        assertEquals("LegacyName", migrated.name)
        assertEquals("LegacyName", migrated.displayName)
        assertEquals("Role.model3.json", migrated.live2dEntryFileName)
        assertEquals(7, migrated.speakerId)
        assertEquals(3, migrated.language)
        assertTrue(migrated.hasVits)
    }

    @Test
    fun currentSelectionUsesStableIdBeforeLegacyStorageKey() {
        val first = character(
            id = "builtin:first",
            storageKey = "First",
            source = ModelSource.BUILT_IN,
        )
        val selected = character(
            id = "imported:selected",
            storageKey = "LegacySelected",
            source = ModelSource.IMPORTED,
        )

        val resolved = CurrentCharacterSelectionStore.resolveCharacter(
            characters = listOf(first, selected),
            preferredId = selected.id,
            legacyStorageKey = first.storageKey,
        )

        assertEquals(selected.id, resolved?.id)
    }

    @Test
    fun legacyNameMigratesToStableSelection() {
        val legacy = character(
            id = "imported:legacy",
            storageKey = "OldDisplayName",
            source = ModelSource.IMPORTED,
        )

        val resolved = CurrentCharacterSelectionStore.resolveCharacter(
            characters = listOf(legacy),
            preferredId = null,
            legacyStorageKey = "OldDisplayName",
        )

        assertEquals("imported:legacy", resolved?.id)
    }

    @Test
    fun missingCurrentResourceFallsBackToAvailableBuiltin() {
        val missing = character(
            id = "imported:missing",
            storageKey = "Missing",
            source = ModelSource.IMPORTED,
            availability = CharacterAvailability.MISSING_LIVE2D,
        )
        val fallback = character(
            id = "builtin:atri",
            storageKey = "ATRI",
            source = ModelSource.BUILT_IN,
        )

        val resolved = CurrentCharacterSelectionStore.resolveCharacter(
            characters = listOf(missing, fallback),
            preferredId = missing.id,
            legacyStorageKey = null,
        )

        assertEquals(fallback.id, resolved?.id)
    }

    @Test
    fun noAvailableCharacterReturnsNullWithoutCrashing() {
        val missing = character(
            id = "imported:missing",
            storageKey = "Missing",
            source = ModelSource.IMPORTED,
            availability = CharacterAvailability.MISSING_LIVE2D,
        )

        assertNull(
            CurrentCharacterSelectionStore.resolveCharacter(
                listOf(missing),
                preferredId = missing.id,
                legacyStorageKey = null,
            )
        )
    }

    @Test
    fun characterPackageHasNoInferenceProviderConfiguration() {
        val fieldNames = CharacterPackage::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("providerConfig" in fieldNames)
        assertFalse("apiKey" in fieldNames)
        assertFalse("endpoint" in fieldNames)
        assertFalse("modelId" in fieldNames)
    }

    @Test
    fun switchGuardRejectsLateResultFromPreviousCharacter() {
        val guard = CharacterSwitchGuard()
        val first = guard.begin("builtin:atri")
        assertEquals(first, guard.currentToken("builtin:atri"))
        val second = guard.begin("builtin:yuuka")

        assertFalse(guard.isCurrent(first, "builtin:atri"))
        assertTrue(guard.isCurrent(second, "builtin:yuuka"))
        assertFalse(guard.isCurrent(second, "builtin:atri"))
        assertNull(guard.currentToken("builtin:atri"))
    }

    @Test
    fun detailStateUsesStableIdAndReportsCurrentCharacter() {
        val current = character(
            id = "builtin:atri",
            storageKey = "ATRI",
            source = ModelSource.BUILT_IN,
        )
        val imported = character(
            id = "imported:one",
            storageKey = "Imported",
            source = ModelSource.IMPORTED,
        )
        val state = ModelManagerUiState(
            characters = listOf(current, imported),
            currentCharacterId = current.id,
            selectedCharacterId = imported.id,
        )

        assertEquals(current.id, state.currentCharacter?.id)
        assertEquals(imported.id, state.selectedCharacter?.id)
        assertEquals(ModelSource.IMPORTED, state.selectedCharacter?.source)
    }

    private fun legacyMeta(source: ModelSource) = ModelMeta(
        name = "LegacyName",
        source = source.name,
        live2dEntryFileName = "Role.model3.json",
    )

    private fun character(
        id: String,
        storageKey: String,
        source: ModelSource,
        availability: CharacterAvailability = CharacterAvailability.AVAILABLE,
    ) = CharacterPackage(
        id = id,
        displayName = storageKey,
        storageKey = storageKey,
        source = source,
        live2dDir = "/models/$storageKey/live2d",
        live2dEntryFileName = "$storageKey.model3.json",
        vitsDir = null,
        bertDir = null,
        speakerId = 0,
        language = 0,
        availability = availability,
    )
}
