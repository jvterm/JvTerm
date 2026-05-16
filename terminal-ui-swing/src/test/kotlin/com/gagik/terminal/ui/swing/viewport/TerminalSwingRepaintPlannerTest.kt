package com.gagik.terminal.ui.swing.viewport

import com.gagik.terminal.render.api.*
import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalSwingRepaintPlannerTest {
    @Test
    fun `dirty rows repaint only changed row runs`() {
        val frame = MutableFrame(columns = 4, rows = 4)
        val cache = TerminalRenderCache(columns = 4, rows = 4)
        val planner = TerminalSwingRepaintPlanner()

        cache.updateFrom(frame.reader)
        planner.requestFrameRepaint(cache, METRICS, WIDTH, HEIGHT, 0.0, NoOpRepaintSink)

        frame.setRow(2, "WXYZ")
        cache.updateFrom(frame.reader)

        val repaintSink = RecordingRepaintSink(failOnFullRepaint = true)
        planner.requestFrameRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = 0.0,
            repaintSink = repaintSink,
        )

        assertEquals(listOf(Region(0, 2 * CELL_HEIGHT, WIDTH, CELL_HEIGHT)), repaintSink.regions)
    }

    @Test
    fun `contiguous dirty rows are coalesced into one repaint region`() {
        val frame = MutableFrame(columns = 4, rows = 4)
        val cache = TerminalRenderCache(columns = 4, rows = 4)
        val planner = TerminalSwingRepaintPlanner()

        cache.updateFrom(frame.reader)
        planner.requestFrameRepaint(cache, METRICS, WIDTH, HEIGHT, 0.0, NoOpRepaintSink)

        frame.setRow(1, "BBBB")
        frame.setRow(2, "CCCC")
        cache.updateFrom(frame.reader)

        val repaintSink = RecordingRepaintSink(failOnFullRepaint = true)
        planner.requestFrameRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = 0.0,
            repaintSink = repaintSink,
        )

        assertEquals(listOf(Region(0, CELL_HEIGHT, WIDTH, 2 * CELL_HEIGHT)), repaintSink.regions)
    }

    @Test
    fun `cursor-only update repaints old and new cursor cells`() {
        val frame = MutableFrame(columns = 4, rows = 4)
        val cache = TerminalRenderCache(columns = 4, rows = 4)
        val planner = TerminalSwingRepaintPlanner()

        frame.cursor = cursor(column = 1, row = 0, generation = 1)
        cache.updateFrom(frame.reader)
        planner.requestFrameRepaint(cache, METRICS, WIDTH, HEIGHT, 0.0, NoOpRepaintSink)

        frame.cursor = cursor(column = 3, row = 2, generation = 2)
        frame.frameGeneration++
        cache.updateFrom(frame.reader)

        val repaintSink = RecordingRepaintSink(failOnFullRepaint = true)
        planner.requestFrameRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = 0.0,
            repaintSink = repaintSink,
        )

        assertEquals(
            listOf(
                Region(CELL_WIDTH, 0, CELL_WIDTH, CELL_HEIGHT),
                Region(3 * CELL_WIDTH, 2 * CELL_HEIGHT, CELL_WIDTH, CELL_HEIGHT),
            ),
            repaintSink.regions,
        )
    }

    @Test
    fun `cursor blink repaints only the cursor cell`() {
        val frame = MutableFrame(columns = 4, rows = 4)
        frame.cursor = cursor(column = 2, row = 1, blinking = true, generation = 1)
        val cache = TerminalRenderCache(columns = 4, rows = 4)
        cache.updateFrom(frame.reader)

        val repaintSink = RecordingRepaintSink()
        TerminalSwingRepaintPlanner().requestCursorBlinkRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = 0.0,
            repaintSink = repaintSink,
        )

        assertEquals(listOf(Region(2 * CELL_WIDTH, CELL_HEIGHT, CELL_WIDTH, CELL_HEIGHT)), repaintSink.regions)
    }

    @Test
    fun `fractional content offset shifts dirty row repaint bounds`() {
        val frame = MutableFrame(columns = 4, rows = 4)
        val cache = TerminalRenderCache(columns = 4, rows = 4)
        val planner = TerminalSwingRepaintPlanner()

        cache.updateFrom(frame.reader)
        planner.requestFrameRepaint(cache, METRICS, WIDTH, HEIGHT, 0.0, NoOpRepaintSink)

        frame.setRow(1, "BBBB")
        cache.updateFrom(frame.reader)

        val repaintSink = RecordingRepaintSink(failOnFullRepaint = true)
        planner.requestFrameRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = -12.0,
            repaintSink = repaintSink,
        )

        assertEquals(listOf(Region(0, 4, WIDTH, CELL_HEIGHT)), repaintSink.regions)
    }

    @Test
    fun `fractional content offset clips dirty row repaint bounds at component top`() {
        val frame = MutableFrame(columns = 4, rows = 4)
        val cache = TerminalRenderCache(columns = 4, rows = 4)
        val planner = TerminalSwingRepaintPlanner()

        cache.updateFrom(frame.reader)
        planner.requestFrameRepaint(cache, METRICS, WIDTH, HEIGHT, 0.0, NoOpRepaintSink)

        frame.setRow(0, "BBBB")
        cache.updateFrom(frame.reader)

        val repaintSink = RecordingRepaintSink(failOnFullRepaint = true)
        planner.requestFrameRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = -12.0,
            repaintSink = repaintSink,
        )

        assertEquals(listOf(Region(0, 0, WIDTH, 4)), repaintSink.regions)
    }

    @Test
    fun `cursor blink repaint uses fractional content offset`() {
        val frame = MutableFrame(columns = 4, rows = 4)
        frame.cursor = cursor(column = 2, row = 1, blinking = true, generation = 1)
        val cache = TerminalRenderCache(columns = 4, rows = 4)
        cache.updateFrom(frame.reader)

        val repaintSink = RecordingRepaintSink()
        TerminalSwingRepaintPlanner().requestCursorBlinkRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = -12.0,
            repaintSink = repaintSink,
        )

        assertEquals(listOf(Region(2 * CELL_WIDTH, 4, CELL_WIDTH, CELL_HEIGHT)), repaintSink.regions)
    }

    @Test
    fun `resized render cache requests full repaint`() {
        val cache = TerminalRenderCache(columns = 2, rows = 2)
        val planner = TerminalSwingRepaintPlanner()
        val frame = MutableFrame(columns = 3, rows = 3)
        var fullRepaints = 0

        cache.updateFrom(frame.reader)

        planner.requestFrameRepaint(
            cache = cache,
            metrics = METRICS,
            componentWidth = WIDTH,
            componentHeight = HEIGHT,
            contentYOffset = 0.0,
            repaintSink = object : TerminalRepaintSink {
                override fun requestFullRepaint() {
                    fullRepaints++
                }

                override fun requestRegionRepaint(x: Int, y: Int, width: Int, height: Int) {
                    error("resize must not request partial repaint")
                }
            },
        )

        assertEquals(1, fullRepaints)
    }

    private data class Region(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private class RecordingRepaintSink(
        private val failOnFullRepaint: Boolean = false,
    ) : TerminalRepaintSink {
        val regions = mutableListOf<Region>()

        override fun requestFullRepaint() {
            if (failOnFullRepaint) {
                error("update must not request full repaint")
            }
        }

        override fun requestRegionRepaint(x: Int, y: Int, width: Int, height: Int) {
            regions.add(Region(x, y, width, height))
        }
    }

    private object NoOpRepaintSink : TerminalRepaintSink {
        override fun requestFullRepaint() = Unit

        override fun requestRegionRepaint(x: Int, y: Int, width: Int, height: Int) = Unit
    }

    private class MutableFrame(
        override val columns: Int,
        override val rows: Int,
    ) : TerminalRenderFrame {
        private val textRows = Array(rows) { row ->
            CharArray(columns) { column -> ('a'.code + row * columns + column).toChar() }
        }
        private val lineGenerations = LongArray(rows) { 1L }

        override var frameGeneration: Long = 1L
        override var structureGeneration: Long = 1L
        override var activeBuffer: TerminalRenderBufferKind = TerminalRenderBufferKind.PRIMARY
        override var cursor: TerminalRenderCursor = cursor(column = 0, row = 0, generation = 1)

        val reader = object : TerminalRenderFrameReader {
            override fun readRenderFrame(consumer: TerminalRenderFrameConsumer) {
                consumer.accept(this@MutableFrame)
            }
        }

        fun setRow(row: Int, text: String) {
            require(text.length == columns)
            var column = 0
            while (column < columns) {
                textRows[row][column] = text[column]
                column++
            }
            lineGenerations[row]++
            frameGeneration++
        }

        override fun lineGeneration(row: Int): Long = lineGenerations[row]

        override fun lineWrapped(row: Int): Boolean = false

        override fun copyLine(
            row: Int,
            codeWords: IntArray,
            codeOffset: Int,
            attrWords: LongArray,
            attrOffset: Int,
            flags: IntArray,
            flagOffset: Int,
            extraAttrWords: LongArray?,
            extraAttrOffset: Int,
            hyperlinkIds: IntArray?,
            hyperlinkOffset: Int,
            clusterSink: TerminalRenderClusterSink?,
            clusterDataSink: TerminalRenderClusterDataSink?,
        ) {
            var column = 0
            while (column < columns) {
                codeWords[codeOffset + column] = textRows[row][column].code
                attrWords[attrOffset + column] = TerminalRenderAttrs.DEFAULT
                flags[flagOffset + column] = TerminalRenderCellFlags.CODEPOINT
                extraAttrWords?.set(extraAttrOffset + column, TerminalRenderExtraAttrs.DEFAULT)
                hyperlinkIds?.set(hyperlinkOffset + column, 0)
                column++
            }
        }
    }

    private companion object {
        private const val CELL_WIDTH = 8
        private const val CELL_HEIGHT = 16
        private const val WIDTH = 120
        private const val HEIGHT = 80
        private val METRICS = TerminalSwingMetrics(
            cellWidth = CELL_WIDTH,
            cellHeight = CELL_HEIGHT,
            baseline = 12,
            underlineY = 13,
            strikethroughY = 8,
            overlineY = 0,
            cursorStrokeWidth = 1,
        )
        private fun cursor(
            column: Int,
            row: Int,
            blinking: Boolean = false,
            generation: Long,
        ): TerminalRenderCursor {
            return TerminalRenderCursor(
                column = column,
                row = row,
                visible = true,
                blinking = blinking,
                shape = TerminalRenderCursorShape.BLOCK,
                generation = generation,
            )
        }
    }
}

