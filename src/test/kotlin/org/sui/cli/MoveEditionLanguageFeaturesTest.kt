package org.sui.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveEditionLanguageFeaturesTest {
    @Test
    fun `edition parser supports canonical values and aliases`() {
        assertEquals(MoveEdition.MOVE_1, MoveEdition.fromToml("1"))
        assertEquals(MoveEdition.MOVE_2024, MoveEdition.fromToml("2024"))
        assertEquals(MoveEdition.MOVE_2024_ALPHA, MoveEdition.fromToml("2024.alpha"))
        assertEquals(MoveEdition.MOVE_2024_BETA, MoveEdition.fromToml("2024.beta"))
        assertEquals(MoveEdition.MOVE_2024_ALPHA, MoveEdition.fromToml("move-2024-alpha"))
        assertEquals(MoveEdition.MOVE_2024_BETA, MoveEdition.fromToml("2024-BETA"))
    }

    @Test
    fun `edition parser trims and rejects invalid values`() {
        assertEquals(MoveEdition.MOVE_2024, MoveEdition.fromToml(" move-2024 "))
        assertNull(MoveEdition.fromToml(null))
        assertNull(MoveEdition.fromToml(" "))
        assertNull(MoveEdition.fromToml("2025"))
    }

    @Test
    fun `move 1 features keep move2024-only rules disabled`() {
        val features = MoveLanguageFeatures.fromEdition(MoveEdition.MOVE_1)

        assertFalse(features.receiverStyleFunctions)
        assertFalse(features.indexExpr)
        assertFalse(features.macroFunctions)
        assertFalse(features.typeKeyword)
        assertFalse(features.publicStructRequired)
        assertFalse(features.letMutRequired)
        assertFalse(features.publicFriendDisabled)
        assertTrue(features.publicPackageVisibility)
        assertFalse(features.resourceAccessControl)
    }

    @Test
    fun `move 2024 family enables move2024-only feature set`() {
        listOf(
            MoveEdition.MOVE_2024_ALPHA,
            MoveEdition.MOVE_2024_BETA,
            MoveEdition.MOVE_2024
        ).forEach { edition ->
            val features = MoveLanguageFeatures.fromEdition(edition)
            assertTrue(features.receiverStyleFunctions)
            assertTrue(features.indexExpr)
            assertTrue(features.macroFunctions)
            assertTrue(features.typeKeyword)
            assertTrue(features.publicStructRequired)
            assertTrue(features.letMutRequired)
            assertTrue(features.publicFriendDisabled)
            assertTrue(features.publicPackageVisibility)
            assertFalse(features.resourceAccessControl)
        }
    }
}
