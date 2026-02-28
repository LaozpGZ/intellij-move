package org.sui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sui.ide.colors.MvColor
import org.sui.lang.MvElementTypes.*

class MvHighlighterMapTest {
    @Test
    fun `move 2024 keywords are mapped to keyword color`() {
        assertEquals(MvColor.KEYWORD, MvHighlighter.map(MACRO))
        assertEquals(MvColor.KEYWORD, MvHighlighter.map(MATCH_KW))
        assertEquals(MvColor.KEYWORD, MvHighlighter.map(ENUM_KW))
        assertEquals(MvColor.KEYWORD, MvHighlighter.map(TYPE_KW))
    }

    @Test
    fun `macro punctuation and literals keep expected colors`() {
        assertEquals(MvColor.OPERATORS, MvHighlighter.map(EXCL))
        assertEquals(MvColor.NUMBER, MvHighlighter.map(INTEGER_LITERAL))
        assertEquals(MvColor.STRING, MvHighlighter.map(BYTE_STRING_LITERAL))
    }

    @Test
    fun `plain identifier has no direct syntax color mapping`() {
        assertNull(MvHighlighter.map(IDENTIFIER))
    }
}
