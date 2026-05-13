package com.gagik.terminal.ui.swing.render

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform

class TerminalAsciiGlyphVectorCacheTest {
    @Nested
    inner class Lookup {
        @Test
        fun `reuses glyph vector for same text font style cell width and render context`() {
            val cache = TerminalAsciiGlyphVectorCache()
            val font = Font(Font.SERIF, Font.PLAIN, 18)
            val frc = FontRenderContext(AffineTransform(), false, true)

            val first = cache.glyphVector("ii", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc)
            val second = cache.glyphVector("ii", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc)

            assertSame(first, second)
        }

        @Test
        fun `splits entries by style font and cell width`() {
            val cache = TerminalAsciiGlyphVectorCache()
            val plain = Font(Font.SERIF, Font.PLAIN, 18)
            val bold = Font(Font.SERIF, Font.BOLD, 18)
            val sans = Font(Font.SANS_SERIF, Font.PLAIN, 18)
            val frc = FontRenderContext(AffineTransform(), false, true)

            val base = cache.glyphVector("ii", plain, Font.PLAIN, cellWidth = 18, fontRenderContext = frc)
            val styleSplit = cache.glyphVector("ii", plain, Font.BOLD, cellWidth = 18, fontRenderContext = frc)
            val fontSplit = cache.glyphVector("ii", sans, Font.PLAIN, cellWidth = 18, fontRenderContext = frc)
            val widthSplit = cache.glyphVector("ii", bold, Font.BOLD, cellWidth = 20, fontRenderContext = frc)

            assertNotSame(base, styleSplit)
            assertNotSame(base, fontSplit)
            assertNotSame(styleSplit, widthSplit)
        }

        @Test
        fun `invalidates cached vectors when render context changes`() {
            val cache = TerminalAsciiGlyphVectorCache()
            val font = Font(Font.SERIF, Font.PLAIN, 18)
            val firstContext = FontRenderContext(AffineTransform(), false, false)
            val secondContext = FontRenderContext(AffineTransform.getScaleInstance(2.0, 2.0), false, false)

            val first = cache.glyphVector("ii", font, Font.PLAIN, cellWidth = 18, fontRenderContext = firstContext)
            val second = cache.glyphVector("ii", font, Font.PLAIN, cellWidth = 18, fontRenderContext = secondContext)

            assertNotSame(first, second)
        }
    }

    @Nested
    inner class Positioning {
        @Test
        fun `pins glyph origins to terminal cell boundaries`() {
            val cache = TerminalAsciiGlyphVectorCache()
            val font = Font(Font.SERIF, Font.PLAIN, 18)
            val frc = FontRenderContext(AffineTransform(), false, true)

            val glyphVector = cache.glyphVector("iii", font, Font.PLAIN, cellWidth = 17, fontRenderContext = frc)

            assertEquals(0.0, glyphVector.getGlyphPosition(0).x, 0.0)
            assertEquals(17.0, glyphVector.getGlyphPosition(1).x, 0.0)
            assertEquals(34.0, glyphVector.getGlyphPosition(2).x, 0.0)
            assertEquals(51.0, glyphVector.getGlyphPosition(3).x, 0.0)
        }
    }

    @Nested
    inner class Capacity {
        @Test
        fun `evicts least recently used vector when capacity is exceeded`() {
            val cache = TerminalAsciiGlyphVectorCache(capacity = 2)
            val font = Font(Font.SERIF, Font.PLAIN, 18)
            val frc = FontRenderContext(AffineTransform(), false, true)

            val first = cache.glyphVector("aa", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc)
            val second = cache.glyphVector("bb", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc)
            assertSame(first, cache.glyphVector("aa", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc))

            cache.glyphVector("cc", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc)

            assertSame(first, cache.glyphVector("aa", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc))
            assertNotSame(second, cache.glyphVector("bb", font, Font.PLAIN, cellWidth = 18, fontRenderContext = frc))
        }

        @Test
        fun `rejects non positive capacity`() {
            assertThrows(IllegalArgumentException::class.java) {
                TerminalAsciiGlyphVectorCache(capacity = 0)
            }
        }
    }
}
