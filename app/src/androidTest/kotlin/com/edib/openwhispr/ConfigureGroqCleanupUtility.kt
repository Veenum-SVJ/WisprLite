package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit setup utility, never included in the regression suite or production APK. */
@RunWith(AndroidJUnit4::class)
class ConfigureGroqCleanupUtility {
    @Test fun useConfiguredGroqKeyForEmptyGroqCleanup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        require(InstrumentationRegistry.getArguments().getString("confirmedSetup") == "use_configured_groq_for_cleanup")
        val context = instrumentation.targetContext
        ProviderProfiles.locked {
            val profiles = ProviderProfiles(context)
            val speech = profiles.speechSelection()
            val cleanup = profiles.cleanupSelection()
            assertEquals("api.groq.com", speech.config.transcriptionUrl().host)
            assertEquals("api.groq.com", cleanup.config.chatCompletionsUrl().host)
            assertTrue(speech.key.isNotBlank())
            assertTrue("Do not replace an existing cleanup credential", cleanup.key.isBlank())
            assertNull("Do not replace a selected saved profile", profiles.activeCleanupId())
            assertEquals(CleanupProviderConfig.Provider.GROQ, cleanup.config.provider)
            SecureKeyStorage.saveGroqApiKey(context, speech.key)
            assertTrue(ProviderProfiles(context).cleanupSelection().key.isNotBlank())
        }
        println("GROQ_CLEANUP_SETUP_VERIFIED: reused configured same-provider key; key never exported")
    }
}
