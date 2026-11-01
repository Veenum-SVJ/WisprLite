package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class ProviderProfileRestoreTest {
    private val a = ProviderProfile("a".repeat(32), "Existing", "https://one.example/v1", "speech", "cleanup")
    private val b = a.copy(id = "b".repeat(32), name = "Imported", baseUrl = "https://two.example/v1")
    private val current = mapOf<String, Any>(ProviderProfiles.METADATA_KEY to ProviderProfileCodec.encode(listOf(a)), ProviderProfiles.SPEECH_ID to a.id)
    @Test fun metadataImportMergesWithoutOrphaningExistingSelection() {
        val changes = ProviderProfileRestore.prepare(current, mapOf(ProviderProfiles.METADATA_KEY to ProviderProfileCodec.encode(listOf(b))), setOf(ProviderProfiles.METADATA_KEY), emptySet())
        assertEquals(listOf(a, b), ProviderProfileCodec.decode(changes.getValue(ProviderProfiles.METADATA_KEY) as String))
        assertFalse(changes.containsKey(ProviderProfiles.SPEECH_ID))
    }
    @Test fun selectionWithoutItsProfileIsRejectedBeforeWrites() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderProfileRestore.prepare(current, mapOf(ProviderProfiles.SPEECH_ID to b.id), setOf(ProviderProfiles.SPEECH_ID), emptySet())
        }
    }
    @Test fun changedEndpointRequiresSelectingItsMatchingKey() {
        val imported = mapOf<String, Any>(ProviderProfiles.METADATA_KEY to ProviderProfileCodec.encode(listOf(a.copy(baseUrl = b.baseUrl))))
        assertThrows(IllegalArgumentException::class.java) { ProviderProfileRestore.prepare(current, imported, imported.keys, emptySet()) }
        assertEquals(imported, ProviderProfileRestore.prepare(current, imported, imported.keys, setOf(SecureKeyStorage.profileKey(a.id))))
    }
    @Test fun cleanupOnlyProfileCannotBeRestoredAsSpeechSelection() {
        val incoming = mapOf<String, Any>(ProviderProfiles.METADATA_KEY to ProviderProfileCodec.encode(listOf(b.copy(speechModel = ""))), ProviderProfiles.SPEECH_ID to b.id)
        assertThrows(IllegalArgumentException::class.java) { ProviderProfileRestore.prepare(current, incoming, incoming.keys, emptySet()) }
    }
    @Test fun keyOnlyRestoreCannotSendImportedKeyToAnotherEndpoint() {
        val key = setOf(SecureKeyStorage.profileKey(a.id))
        val incoming = mapOf<String, Any>(ProviderProfiles.METADATA_KEY to ProviderProfileCodec.encode(listOf(a.copy(baseUrl = b.baseUrl))))
        assertThrows(IllegalArgumentException::class.java) { ProviderProfileRestore.prepare(current, incoming, emptySet(), key) }
        assertThrows(IllegalArgumentException::class.java) { ProviderProfileRestore.prepare(current, emptyMap(), emptySet(), key) }
        assertTrue(ProviderProfileRestore.prepare(current, current, emptySet(), key).isEmpty())
    }
}
