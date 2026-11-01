package com.edib.openwhispr
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class RedesignBackupTest {
 @Test fun snippetsAndToneRoundTripInEncryptedBackup() {
  val entries=listOf(Snippet("a","my signature","Regards,\nAlex"))
  val snapshot=SettingsBackup.Snapshot(mapOf(ToneRules.KEY to ToneRuleCodec.encode(ToneConfiguration(true)),ToneRules.NEXT_KEY to "FORMAL"), emptyMap(), entries)
  val file=SettingsBackup.encrypt(snapshot,"test passphrase")
  assertFalse(file.contains("Alex"))
  val actual=SettingsBackup.decrypt(file,"test passphrase")
  assertEquals(entries,actual.snippets)
  assertFalse(actual.prefs.containsKey(ToneRules.NEXT_KEY))
  assertTrue(ToneRuleCodec.decode(actual.prefs[ToneRules.KEY] as String).enabled)
 }
 @Test fun legacyBackupWithoutSnippetsDoesNotRequestDeletingThem() {
  val file=SettingsBackup.encrypt(SettingsBackup.Snapshot(emptyMap(),emptyMap()),"test passphrase")
  val legacy=JSONObject(file).put("version",1).toString()
  assertNull(SettingsBackup.decrypt(legacy,"test passphrase").snippets)
 }
 @Test fun duplicateTriggersCannotBeExported() {
  assertThrows(IllegalArgumentException::class.java) {
   SettingsBackup.encrypt(SettingsBackup.Snapshot(emptyMap(),emptyMap(),listOf(Snippet("a","SIGNATURE","a"),Snippet("b","signature","b"))),"test passphrase")
  }
 }
}
