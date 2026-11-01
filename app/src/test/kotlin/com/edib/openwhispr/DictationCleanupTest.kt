package com.edib.openwhispr
import org.junit.Assert.*
import org.junit.Test
class DictationCleanupTest {
 @Test fun savedSnippetNeverLeaksToPromptAndRestoresExactly() {
  val draft=DictationCleanup.prepare("Please use my signature.",listOf(Snippet("a","my signature","Best regards,\nAlex")))
  assertFalse(draft.protectedText.contains("Alex"))
  assertEquals("Please use Best regards,\nAlex.",draft.accept(draft.protectedText))
  assertNull(draft.accept("Please use something else."))
  val record=DictationRecord("1",1,"Please use my signature.",null,1000,"Direct",draft.expanded,draft.snippetState)
  assertEquals(draft.expanded,DictationCleanup.input(record)?.expanded)
 }
 @Test fun corruptedCapturedStateDoesNotRerunAgainstNewSnippets() {
  assertNull(DictationCleanup.input(DictationRecord("1",1,"my signature",null,1000,"Direct","Old signature","bad")))
 }
 @Test fun selectedToneReplacesOnlyRegisterRule() {
  assertTrue(PostProcessor.effectivePrompt("",tone=Tone.FORMAL).contains(Tone.FORMAL.instruction))
  assertFalse(PostProcessor.effectivePrompt("",tone=Tone.FORMAL).contains("Never make it more formal"))
  assertTrue(PostProcessor.effectivePrompt("").contains("Never make it more formal"))
 }
}
