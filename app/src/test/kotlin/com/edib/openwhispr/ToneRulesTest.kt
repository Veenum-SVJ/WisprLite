package com.edib.openwhispr
import org.junit.Assert.*
import org.junit.Test
class ToneRulesTest {
 @Test fun disabledStyleAndUnknownPackagesStayNeutral() {
  assertEquals(Tone.NEUTRAL,ToneConfiguration().toneFor("com.whatsapp"))
  assertEquals(Tone.NEUTRAL,ToneConfiguration(enabled=true).toneFor("some.unknown.app"))
 }
 @Test fun defaultsAndPerAppOverridesRoundTripWithoutLeakingAppNamesIntoInstructions() {
  val config=ToneConfiguration(true, mapOf(ToneCategory.WORK to Tone.EXCITED),mapOf("example.app" to AppToneRule(ToneCategory.WORK,Tone.CASUAL)))
  val restored=ToneRuleCodec.decode(ToneRuleCodec.encode(config))
  assertEquals(config,restored)
  assertEquals(Tone.CASUAL,restored.toneFor("example.app"))
  assertEquals(Tone.EXCITED,restored.toneFor("com.Slack"))
  assertFalse(restored.toneFor("example.app").instruction.contains("example.app"))
 }
 @Test fun invalidRuleVersionIsRejected() {
  assertThrows(IllegalArgumentException::class.java){ToneRuleCodec.decode("{\"version\":2}")}
 }
}
