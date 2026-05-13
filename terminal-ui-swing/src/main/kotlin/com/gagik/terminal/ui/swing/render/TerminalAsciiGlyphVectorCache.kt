package com.gagik.terminal.ui.swing.render

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.Point2D

/**
 * Bounded cache for ASCII glyph vectors with terminal-cell positions.
 */
internal class TerminalAsciiGlyphVectorCache(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be > 0, was $capacity" }
    }

    private val glyphPosition = Point2D.Float()
    private val layouts = object : LinkedHashMap<Key, GlyphVector>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GlyphVector>?): Boolean {
            return size > capacity
        }
    }
    private var fontRenderContext: FontRenderContext? = null

    /**
     * Clears all cached glyph vectors.
     */
    fun clear() {
        fontRenderContext = null
        layouts.clear()
    }

    /**
     * Returns a positioned glyph vector for [text].
     */
    fun glyphVector(
        text: String,
        font: Font,
        style: Int,
        cellWidth: Int,
        fontRenderContext: FontRenderContext,
    ): GlyphVector {
        prepare(fontRenderContext)

        val key = Key(text, font, style, cellWidth)
        val cached = layouts[key]
        if (cached != null) return cached

        val glyphVector = font.createGlyphVector(fontRenderContext, text)
        var glyph = 0
        val glyphCount = glyphVector.numGlyphs
        while (glyph <= glyphCount) {
            glyphPosition.x = glyph * cellWidth.toFloat()
            glyphPosition.y = 0f
            glyphVector.setGlyphPosition(glyph, glyphPosition)
            glyph++
        }
        layouts[key] = glyphVector
        return glyphVector
    }

    private fun prepare(nextFontRenderContext: FontRenderContext) {
        if (nextFontRenderContext == fontRenderContext) return

        fontRenderContext = nextFontRenderContext
        layouts.clear()
    }

    private data class Key(
        val text: String,
        val font: Font,
        val style: Int,
        val cellWidth: Int,
    )

    private companion object {
        private const val DEFAULT_CAPACITY = 4096
        private const val LOAD_FACTOR = 0.75f
    }
}
