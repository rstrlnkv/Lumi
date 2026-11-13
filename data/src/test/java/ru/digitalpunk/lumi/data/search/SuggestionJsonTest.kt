package ru.digitalpunk.lumi.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionJsonTest {
    @Test fun googlesFourElementAnswerGivesOnlyTheSecondElement() {
        val body = """["git",["github","gitlab"],[],{"google:suggestsubtypes":[[512,433]]}]"""
        assertEquals(listOf("github", "gitlab"), parseSuggestions(body))
    }

    @Test fun theOpenSearchListAnswersOfTheOtherEnginesParse() {
        assertEquals(listOf("github", "git bash"), parseSuggestions(""" [ "git" , [ "github" , "git bash" ] ] """))
        assertEquals(listOf("github flowseal/zapret"), parseSuggestions("""["git",["github flowseal\/zapret"]]"""))
        assertEquals(emptyList(), parseSuggestions("""["zzzz",[]]"""))
    }

    @Test fun escapesAndNonLatinTextDecode() {
        assertEquals(listOf("café", "привет", "a\"b\\c", "😀 smile"), parseSuggestions(
            """["q",["café","привет","a\"b\\c","😀 smile"]]""",
        ))
    }

    @Test fun anythingButTheExpectedShapeGivesNull() {
        listOf(
            "",
            "{}",
            "null",
            """["git"]""",
            """["git",{"a":1}]""",
            """["git",["github""",
            """["git",["github",]""",
            """["git",["ok",1]]""",
            """["git",["ok",null]]""",
            """["git",["\ud83d alone"]]""",
            """["git",["\ude00"]]""",
            """["git",["bad \x escape"]]""",
            """["git",["short \u12"]]""",
        ).forEach { assertNull(parseSuggestions(it), it) }
    }

    @Test fun textAfterTheListIsIgnored() {
        assertEquals(listOf("a"), parseSuggestions("""["q",["a"]] trailing junk <body>"""))
    }

    @Test fun itemsAreCappedTrimmedAndCleaned() {
        val many = (1..30).joinToString(",") { "\"s$it\"" }
        assertEquals((1..20).map { "s$it" }, parseSuggestions("""["q",[$many]]"""))
        val long = "x".repeat(201)
        assertEquals(listOf("keep", "tab here"), parseSuggestions("""["q",["  keep  ","$long","   ","tab\there\u0007"]]"""))
    }

    @Test fun hostileBodiesFinishQuickly() {
        val start = System.nanoTime()
        assertNull(parseSuggestions("[".repeat(1_048_576)))
        assertNull(parseSuggestions("[\"" + "a".repeat(1_048_576)))
        assertNull(parseSuggestions("[\"q\",[" + "\"a\",".repeat(200_000)))
        assertTrue(System.nanoTime() - start < 2_000_000_000L)
    }
}
