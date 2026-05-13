package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.*
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalTextPainterTest {
    @Nested
    inner class AsciiRuns {
        @Test
        fun `paints contiguous ascii run in terminal cells`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("ii"))

            fixture.paintRow(cache)

            assertTrue(
                fixture.image.containsColorInRange(TEST_RED, fixture.metrics.cellWidth, fixture.metrics.cellWidth * 2),
                "second glyph was not painted in the second terminal cell",
            )
        }

        @Test
        fun `glyph vector fallback preserves grid cells when measured width differs`() {
            val image = BufferedImage(120, 40, BufferedImage.TYPE_INT_ARGB)
            val settings = TerminalSwingSettings(
                font = Font(Font.SERIF, Font.PLAIN, 18),
                palette = defaultTestSettings(foreground = TEST_RED, background = TEST_BLACK).palette,
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                fractionalMetrics = RenderingHints.VALUE_FRACTIONALMETRICS_ON,
            )
            val g = image.createGraphics()
            val fontMetrics = g.getFontMetrics(settings.font)
            val metrics = com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics(
                cellWidth = maxOf(1, fontMetrics.charWidth('W')),
                cellHeight = fontMetrics.height,
                baseline = fontMetrics.ascent,
                underlineY = minOf(fontMetrics.height - 1, fontMetrics.ascent + 1),
                strikethroughY = maxOf(0, fontMetrics.ascent - fontMetrics.ascent / 3),
                overlineY = 0,
                cursorStrokeWidth = 1,
            )
            val painter = TerminalTextPainter(AwtColorCache(), TerminalDecorationPainter(AwtColorCache()))
            painter.updateSettings(settings)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
            val cache = renderCache(TestRenderFrame.text("ii"))

            painter.paintRow(g, cache, settings.palette, metrics, row = 0, fontRenderContext = g.fontRenderContext)
            g.dispose()

            assertTrue(image.containsColorInRange(TEST_RED, 0, metrics.cellWidth))
            assertTrue(image.containsColorInRange(TEST_RED, metrics.cellWidth, metrics.cellWidth * 2))
        }

        @Test
        fun `ascii decorations split runs by extra attributes`() {
            val fixture = fixture(foreground = TEST_WHITE)
            val cache = renderCache(
                TestRenderFrame.text(
                    text = "AB",
                    attrs = longArrayOf(
                        TerminalRenderAttrs.pack(underlineStyle = TerminalRenderUnderline.SINGLE),
                        TerminalRenderAttrs.pack(underlineStyle = TerminalRenderUnderline.SINGLE),
                    ),
                    extraAttrs = longArrayOf(
                        TerminalRenderExtraAttrs.pack(
                            underlineColorKind = TerminalRenderColorKind.RGB,
                            underlineColorValue = 0x00FF00,
                        ),
                        TerminalRenderExtraAttrs.pack(
                            underlineColorKind = TerminalRenderColorKind.RGB,
                            underlineColorValue = 0x0000FF,
                        ),
                    ),
                ),
            )

            fixture.paintRow(cache)

            assertEquals(TEST_GREEN, fixture.image.getRGB(1, fixture.metrics.underlineY))
            assertEquals(TEST_BLUE, fixture.image.getRGB(fixture.metrics.cellWidth + 1, fixture.metrics.underlineY))
        }
    }

    @Nested
    inner class ComplexText {
        @Test
        fun `paints non ascii code point`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u03A9"))

            fixture.paintRow(cache)
            fixture.paintRow(cache)

            assertTrue(fixture.image.containsColor(TEST_RED, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }

        @Test
        fun `paints grapheme cluster from cluster sink`() {
            val fixture = fixture()
            val cache = renderCache(
                TestRenderFrame(
                    arrayOf(
                        arrayOf(
                            TestCell(
                                flags = TerminalRenderCellFlags.CLUSTER,
                                attr = TerminalRenderAttrs.DEFAULT,
                                cluster = "\u0E01\u0E34",
                            ),
                        ),
                    ),
                ),
            )

            fixture.paintRow(cache)

            assertTrue(fixture.image.containsColor(TEST_RED, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }

        @Test
        fun `wide complex cell decoration spans two cells`() {
            val fixture = fixture(foreground = TEST_WHITE, width = 80)
            val cache = renderCache(
                TestRenderFrame(
                    arrayOf(
                        arrayOf(
                            TestCell(
                                codeWord = 0x4E2D,
                                flags = TerminalRenderCellFlags.CODEPOINT or TerminalRenderCellFlags.WIDE_LEADING,
                                attr = TerminalRenderAttrs.pack(underlineStyle = TerminalRenderUnderline.SINGLE),
                            ),
                            TestCell(flags = TerminalRenderCellFlags.WIDE_TRAILING),
                        ),
                    ),
                ),
            )

            fixture.paintRow(cache)

            assertTrue(
                fixture.image.containsColorInRange(TEST_WHITE, fixture.metrics.cellWidth, fixture.metrics.cellWidth * 2),
            )
        }
    }

    @Nested
    inner class CursorForeground {
        @Test
        fun `paints ascii cell with supplied foreground`() {
            val fixture = fixture(foreground = TEST_WHITE)
            val cache = renderCache(TestRenderFrame.text("A"))

            fixture.painter.paintCellForeground(
                fixture.g,
                cache,
                fixture.settings.palette,
                fixture.metrics,
                column = 0,
                row = 0,
                foreground = TEST_GREEN,
                fontRenderContext = fixture.g.fontRenderContext,
            )

            assertTrue(fixture.image.containsColor(TEST_GREEN, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }

        @Test
        fun `empty cell does not paint foreground`() {
            val fixture = fixture(foreground = TEST_WHITE)
            val cache = renderCache(TestRenderFrame(arrayOf(arrayOf(TestCell()))))

            fixture.painter.paintCellForeground(
                fixture.g,
                cache,
                fixture.settings.palette,
                fixture.metrics,
                column = 0,
                row = 0,
                foreground = TEST_GREEN,
                fontRenderContext = fixture.g.fontRenderContext,
            )

            assertTrue(!fixture.image.containsColor(TEST_GREEN, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
        }
    }

    private data class Fixture(
        val image: BufferedImage,
        val g: java.awt.Graphics2D,
        val settings: com.gagik.terminal.ui.swing.settings.TerminalSwingSettings,
        val metrics: com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics,
        val painter: TerminalTextPainter,
    ) {
        fun paintRow(cache: com.gagik.terminal.render.cache.TerminalRenderCache) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
            painter.paintRow(g, cache, settings.palette, metrics, row = 0, fontRenderContext = g.fontRenderContext)
        }
    }

    private fun fixture(
        foreground: Int = TEST_RED,
        background: Int = TEST_BLACK,
        width: Int = 80,
    ): Fixture {
        val image = BufferedImage(width, 40, BufferedImage.TYPE_INT_ARGB)
        val settings = defaultTestSettings(foreground = foreground, background = background)
        val colorCache = AwtColorCache()
        val painter = TerminalTextPainter(colorCache, TerminalDecorationPainter(colorCache))
        painter.updateSettings(settings)
        return Fixture(
            image = image,
            g = image.createGraphics(),
            settings = settings,
            metrics = testMetrics(image, settings),
            painter = painter,
        )
    }
}
