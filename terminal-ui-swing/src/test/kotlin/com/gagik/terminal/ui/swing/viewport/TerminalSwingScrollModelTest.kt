package com.gagik.terminal.ui.swing.viewport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalSwingScrollModelTest {
    @Test
    fun `fractional deltas accumulate before crossing a row boundary`() {
        val model = TerminalSwingScrollModel()

        assertTrue(model.scrollBy(0.4, historySize = 10))
        assertEquals(0, model.offset)

        assertTrue(model.scrollBy(0.7, historySize = 10))
        assertEquals(1, model.offset)
    }

    @Test
    fun `scroll offset clamps to available history`() {
        val model = TerminalSwingScrollModel()

        assertTrue(model.scrollBy(12.0, historySize = 5))

        assertEquals(5, model.offset)
    }

    @Test
    fun `zero or clamped deltas report no movement`() {
        val model = TerminalSwingScrollModel()

        assertFalse(model.scrollBy(0.0, historySize = 5))
        assertFalse(model.scrollBy(-1.0, historySize = 5))
        assertEquals(0, model.offset)
    }

    @Test
    fun `reset returns to live viewport`() {
        val model = TerminalSwingScrollModel()

        model.scrollBy(3.0, historySize = 5)
        model.reset()

        assertEquals(0, model.offset)
    }
}
