package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.*
import com.gagik.terminal.ui.swing.render.primitives.TerminalBoxDrawingPainter
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
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
        fun `ascii mismatch path keeps absolute run origin`() {
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
        fun `ascii mismatch path leaves graphics transform unchanged`() {
            val image = BufferedImage(120, 40, BufferedImage.TYPE_INT_ARGB)
            val settings = TerminalSwingSettings(
                font = Font(Font.SERIF, Font.PLAIN, 18),
                palette = defaultTestSettings(foreground = TEST_RED, background = TEST_BLACK).palette,
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                fractionalMetrics = RenderingHints.VALUE_FRACTIONALMETRICS_ON,
            )
            val g = image.createGraphics()
            val initialTransform = AffineTransform.getTranslateInstance(3.0, 5.0)
            g.transform = initialTransform
            val metrics = testMetrics(image, settings)
            val painter = TerminalTextPainter(AwtColorCache(), TerminalDecorationPainter(AwtColorCache()))
            painter.updateSettings(settings)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
            val cache = renderCache(TestRenderFrame.text("ii"))

            painter.paintRow(g, cache, settings.palette, metrics, row = 0, fontRenderContext = g.fontRenderContext)

            assertEquals(initialTransform, g.transform)
            g.dispose()
        }

        @Test
        fun `ascii mismatch path does not rescale prefix when run grows`() {
            val settings = TerminalSwingSettings(
                font = Font(Font.SERIF, Font.PLAIN, 18),
                palette = defaultTestSettings(foreground = TEST_RED, background = TEST_BLACK).palette,
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                fractionalMetrics = RenderingHints.VALUE_FRACTIONALMETRICS_ON,
            )
            val shortImage = paintSerifAscii(settings, "Wi")
            val longImage = paintSerifAscii(settings, "Wii")
            val metrics = testMetrics(shortImage, settings)

            assertEquals(
                shortImage.countColorInRange(TEST_RED, 0, metrics.cellWidth, 0, metrics.cellHeight),
                longImage.countColorInRange(TEST_RED, 0, metrics.cellWidth, 0, metrics.cellHeight),
            )
        }

        @Test
        fun `ascii mismatch path does not also paint compact unpositioned run`() {
            val settings = TerminalSwingSettings(
                font = Font(Font.SERIF, Font.PLAIN, 18),
                palette = defaultTestSettings(foreground = TEST_RED, background = TEST_BLACK).palette,
                textAntialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
                fractionalMetrics = RenderingHints.VALUE_FRACTIONALMETRICS_ON,
            )
            val singleCell = paintSerifAscii(settings, "i")
            val twoCells = paintSerifAscii(settings, "ii")
            val metrics = testMetrics(singleCell, settings)

            assertEquals(
                singleCell.countColorInRange(TEST_RED, 0, metrics.cellWidth, 0, metrics.cellHeight),
                twoCells.countColorInRange(TEST_RED, 0, metrics.cellWidth, 0, metrics.cellHeight),
            )
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
    inner class CellPrimitives {
        @Test
        fun `box drawing horizontal spans adjacent cell boundary`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u2500\u2500"))

            fixture.paintRow(cache)

            val centerY = fixture.metrics.cellHeight / 2
            assertEquals(TEST_RED, fixture.image.getRGB(fixture.metrics.cellWidth - 1, centerY))
            assertEquals(TEST_RED, fixture.image.getRGB(fixture.metrics.cellWidth, centerY))
        }

        @Test
        fun `light box drawing stroke remains one pixel clean`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u2500"))

            fixture.paintRow(cache)

            val centerY = fixture.metrics.cellHeight / 2
            assertEquals(TEST_RED, fixture.image.getRGB(fixture.metrics.cellWidth / 2, centerY))
            assertTrue(fixture.image.getRGB(fixture.metrics.cellWidth / 2, centerY + 1) != TEST_RED)
        }

        @Test
        fun `box drawing vertical spans adjacent row boundary`() {
            val fixture = fixture()
            val cache = renderCache(
                TestRenderFrame(
                    arrayOf(
                        arrayOf(TestCell(codeWord = 0x2502, flags = TerminalRenderCellFlags.CODEPOINT)),
                        arrayOf(TestCell(codeWord = 0x2502, flags = TerminalRenderCellFlags.CODEPOINT)),
                    ),
                ),
            )

            fixture.paintRow(cache, row = 0)
            fixture.paintRow(cache, row = 1)

            val centerX = fixture.metrics.cellWidth / 2
            assertEquals(TEST_RED, fixture.image.getRGB(centerX, fixture.metrics.cellHeight - 1))
            assertEquals(TEST_RED, fixture.image.getRGB(centerX, fixture.metrics.cellHeight))
        }

        @Test
        fun `rounded box corner uses curved geometry instead of square elbow`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u256D"))

            fixture.paintRow(cache)

            val centerX = fixture.metrics.cellWidth / 2
            val centerY = fixture.metrics.cellHeight / 2
            assertTrue(fixture.image.getRGB(centerX, centerY) != TEST_RED)
            assertTrue(fixture.image.containsNonBackgroundInRange(centerX, fixture.metrics.cellWidth, 0, fixture.metrics.cellHeight))
        }

        @Test
        fun `rounded box corners use the correct arc quadrant`() {
            val fixture = fixture(width = 120)
            val cache = renderCache(TestRenderFrame.text("\u256D\u256E\u2570\u256F"))

            fixture.paintRow(cache)

            assertRoundedQuadrants(
                fixture,
                RoundedQuadrant(column = 0, right = true, lower = true),
                RoundedQuadrant(column = 1, right = false, lower = true),
                RoundedQuadrant(column = 2, right = true, lower = false),
                RoundedQuadrant(column = 3, right = false, lower = false),
            )
        }

        @Test
        fun `double dashed box drawing horizontal is painted`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u254C\u254D"))

            fixture.paintRow(cache)

            val centerY = fixture.metrics.cellHeight / 2
            assertTrue(fixture.image.containsColorInRange(TEST_RED, 0, fixture.metrics.cellWidth))
            assertTrue(
                fixture.image.containsColorInRange(
                    TEST_RED,
                    fixture.metrics.cellWidth,
                    fixture.metrics.cellWidth * 2,
                ),
            )
            assertEquals(TEST_RED, fixture.image.getRGB(0, centerY))
        }

        @Test
        fun `double dashed box drawing vertical is painted`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u254E\u254F"))

            fixture.paintRow(cache)

            val lightCenterX = fixture.metrics.cellWidth / 2
            val heavyCenterX = fixture.metrics.cellWidth + fixture.metrics.cellWidth / 2
            assertTrue(fixture.image.containsColor(TEST_RED, fixture.metrics.cellWidth, fixture.metrics.cellHeight))
            assertTrue(
                fixture.image.containsColorInRange(
                    TEST_RED,
                    fixture.metrics.cellWidth,
                    fixture.metrics.cellWidth * 2,
                ),
            )
            assertEquals(TEST_RED, fixture.image.getRGB(lightCenterX, 0))
            assertEquals(TEST_RED, fixture.image.getRGB(heavyCenterX, 0))
        }

        @Test
        fun `dashed box drawing glyphs use unicode dash counts`() {
            val image = BufferedImage(84, 24, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            val painter = TerminalBoxDrawingPainter()
            g.color = java.awt.Color(TEST_RED, true)

            painter.paint(g, 0x254C, x = 0, y = 0, width = 21, height = 21)
            painter.paint(g, 0x2504, x = 28, y = 0, width = 21, height = 21)
            painter.paint(g, 0x2508, x = 56, y = 0, width = 21, height = 21)
            g.dispose()

            val centerY = 21 / 2
            assertEquals(2, image.countColorRunsHorizontal(TEST_RED, xStart = 0, xEnd = 21, y = centerY))
            assertEquals(3, image.countColorRunsHorizontal(TEST_RED, xStart = 28, xEnd = 49, y = centerY))
            assertEquals(4, image.countColorRunsHorizontal(TEST_RED, xStart = 56, xEnd = 77, y = centerY))
        }

        @Test
        fun `block element fills full terminal cell`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u2588"))

            fixture.paintRow(cache)

            assertEquals(TEST_RED, fixture.image.getRGB(0, 0))
            assertEquals(TEST_RED, fixture.image.getRGB(fixture.metrics.cellWidth - 1, fixture.metrics.cellHeight - 1))
        }

        @Test
        fun `upper half block fills top half only`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u2580"))

            fixture.paintRow(cache)

            assertEquals(TEST_RED, fixture.image.getRGB(fixture.metrics.cellWidth / 2, 0))
            assertTrue(fixture.image.getRGB(fixture.metrics.cellWidth / 2, fixture.metrics.cellHeight - 1) != TEST_RED)
        }

        @Test
        fun `dark shade is dense but not solid`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u2593"))

            fixture.paintRow(cache)

            val painted = fixture.image.countColorInRange(
                TEST_RED,
                xStart = 0,
                xEnd = fixture.metrics.cellWidth,
                yStart = 0,
                yEnd = fixture.metrics.cellHeight,
            )
            assertTrue(painted > fixture.metrics.cellWidth * fixture.metrics.cellHeight / 2)
            assertTrue(painted < fixture.metrics.cellWidth * fixture.metrics.cellHeight)
        }

        @Test
        fun `shade blocks preserve foreground color and relative density`() {
            val fixture = fixture()
            val cache = renderCache(TestRenderFrame.text("\u2591\u2592\u2593"))

            fixture.paintRow(cache)

            val light = fixture.image.countColorInRange(
                TEST_RED,
                xStart = 0,
                xEnd = fixture.metrics.cellWidth,
                yStart = 0,
                yEnd = fixture.metrics.cellHeight,
            )
            val medium = fixture.image.countColorInRange(
                TEST_RED,
                xStart = fixture.metrics.cellWidth,
                xEnd = fixture.metrics.cellWidth * 2,
                yStart = 0,
                yEnd = fixture.metrics.cellHeight,
            )
            val dark = fixture.image.countColorInRange(
                TEST_RED,
                xStart = fixture.metrics.cellWidth * 2,
                xEnd = fixture.metrics.cellWidth * 3,
                yStart = 0,
                yEnd = fixture.metrics.cellHeight,
            )

            assertTrue(light > 0)
            assertTrue(light < medium)
            assertTrue(medium < dark)
        }

        @Test
        fun `cursor foreground repaints box drawing primitive`() {
            val fixture = fixture(foreground = TEST_WHITE)
            val cache = renderCache(TestRenderFrame.text("\u2500"))

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

            assertEquals(TEST_GREEN, fixture.image.getRGB(fixture.metrics.cellWidth / 2, fixture.metrics.cellHeight / 2))
        }

        private fun assertRoundedQuadrants(fixture: Fixture, vararg quadrants: RoundedQuadrant) {
            for (quadrant in quadrants) {
                assertRoundedQuadrant(fixture, quadrant)
            }
        }

        private fun assertRoundedQuadrant(
            fixture: Fixture,
            quadrant: RoundedQuadrant,
        ) {
            val cellX = quadrant.column * fixture.metrics.cellWidth
            val midX = cellX + fixture.metrics.cellWidth / 2
            val midY = fixture.metrics.cellHeight / 2
            val xStart = if (quadrant.right) midX else cellX
            val xEnd = if (quadrant.right) cellX + fixture.metrics.cellWidth else midX + 1
            val yStart = if (quadrant.lower) midY else 0
            val yEnd = if (quadrant.lower) fixture.metrics.cellHeight else midY + 1

            val painted = fixture.image.countNonBackgroundInRange(xStart, xEnd, yStart, yEnd)
            assertTrue(painted > 0, "rounded corner at column ${quadrant.column} did not paint the expected quadrant")
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
            paintRow(cache, row = 0)
        }

        fun paintRow(cache: com.gagik.terminal.render.cache.TerminalRenderCache, row: Int) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
            painter.paintRow(g, cache, settings.palette, metrics, row = row, fontRenderContext = g.fontRenderContext)
        }
    }

    private data class RoundedQuadrant(
        val column: Int,
        val right: Boolean,
        val lower: Boolean,
    )

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

    private fun paintSerifAscii(settings: TerminalSwingSettings, text: String): BufferedImage {
        val image = BufferedImage(140, 40, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        val painter = TerminalTextPainter(AwtColorCache(), TerminalDecorationPainter(AwtColorCache()))
        painter.updateSettings(settings)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
        painter.paintRow(
            g,
            renderCache(TestRenderFrame.text(text)),
            settings.palette,
            testMetrics(image, settings),
            row = 0,
            fontRenderContext = g.fontRenderContext,
        )
        g.dispose()
        return image
    }

    private fun BufferedImage.containsNonBackgroundInRange(
        xStart: Int,
        xEnd: Int,
        yStart: Int,
        yEnd: Int,
    ): Boolean {
        return countNonBackgroundInRange(xStart, xEnd, yStart, yEnd) > 0
    }

    private fun BufferedImage.countNonBackgroundInRange(
        xStart: Int,
        xEnd: Int,
        yStart: Int,
        yEnd: Int,
    ): Int {
        var count = 0
        var y = yStart
        while (y < yEnd) {
            var x = xStart
            while (x < xEnd) {
                if (getRGB(x, y) != TEST_BLACK) count++
                x++
            }
            y++
        }
        return count
    }

    private fun BufferedImage.countColorRunsHorizontal(argb: Int, xStart: Int, xEnd: Int, y: Int): Int {
        var runs = 0
        var insideRun = false
        var x = xStart
        while (x < xEnd) {
            val painted = getRGB(x, y) == argb
            if (painted && !insideRun) {
                runs++
            }
            insideRun = painted
            x++
        }
        return runs
    }
}
