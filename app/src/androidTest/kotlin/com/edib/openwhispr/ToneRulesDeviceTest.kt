package com.edib.openwhispr
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class ToneRulesDeviceTest {
 @Test fun overrideIsLocalAndConsumedOnceWithoutChangingSavedAppRules() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val prefs=context.getSharedPreferences("wisprlite-tone-tests",0)
  try {
   prefs.edit().clear().commit()
   val rules=ToneRules(prefs)
   rules.save(ToneConfiguration(true,apps=mapOf("example.app" to AppToneRule(ToneCategory.WORK))))
   rules.overrideNext(Tone.CASUAL)
   assertEquals(Tone.CASUAL,rules.consumeFor("example.app"))
   assertNull(rules.nextOverride())
   assertEquals(Tone.FORMAL,rules.consumeFor("example.app"))
   assertEquals(ToneCategory.WORK,rules.configuration().categoryFor("example.app"))
  } finally { prefs.edit().clear().commit(); context.deleteSharedPreferences("wisprlite-tone-tests") }
 }
}
