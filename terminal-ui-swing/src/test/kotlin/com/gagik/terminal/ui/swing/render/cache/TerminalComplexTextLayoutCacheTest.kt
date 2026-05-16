package com.gagik.terminal.ui.swing.render.cache

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Font
import java.awt.font.FontRenderContext

class TerminalComplexTextLayoutCacheTest {

    @Test
    fun `constructor rejects non-positive capacities`() {
        assertThrows<IllegalArgumentException> {
            TerminalComplexTextLayoutCache(codePointCapacity = 0)
        }
        assertThrows<IllegalArgumentException> {
            TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 0)
        }
    }

    @Test
    fun `code point layouts are reused without rebuilding layout objects`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val frc = FontRenderContext(null, false, false)

        val first = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val second = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)

        assertSame(first, second)
    }

    @Test
    fun `code point layouts are split by style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val frc = FontRenderContext(null, false, false)

        val plain = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val bold = layoutCache.codePointLayout(0x03A9, Font.BOLD, frc, fontCache)
        val italic = layoutCache.codePointLayout(0x03A9, Font.ITALIC, frc, fontCache)

        assertNotSame(plain, bold)
        assertNotSame(plain, italic)
        assertSame(bold, layoutCache.codePointLayout(0x03A9, Font.BOLD, frc, fontCache))
    }

    @Test
    fun `code point layouts are bounded by lru capacity`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 2)
        val frc = FontRenderContext(null, false, false)

        val omega = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val cjk = layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache)
        layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        layoutCache.codePointLayout(0x3042, Font.PLAIN, frc, fontCache)

        assertSame(omega, layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache))
        assertNotSame(cjk, layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache))
    }

    @Test
    fun `code point access refreshes lru order`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 2)
        val frc = FontRenderContext(null, false, false)

        val omega = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        val cjk = layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache)
        assertSame(omega, layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache))
        layoutCache.codePointLayout(0x3042, Font.PLAIN, frc, fontCache)

        assertSame(omega, layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache))
        assertNotSame(cjk, layoutCache.codePointLayout(0x4E2D, Font.PLAIN, frc, fontCache))
    }

    @Test
    fun `code point cache bulk eviction creates reusable free slots`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 20)
        val frc = FontRenderContext(null, false, false)

        repeat(21) { index ->
            layoutCache.codePointLayout(0x0400 + index, Font.PLAIN, frc, fontCache)
        }

        assertEquals(19, layoutCache.cachedCodePointLayoutCount())
    }

    @Test
    fun `clear invalidates code point layouts`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val frc = FontRenderContext(null, false, false)

        val first = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)
        layoutCache.clear()
        val second = layoutCache.codePointLayout(0x03A9, Font.PLAIN, frc, fontCache)

        assertNotSame(first, second)
    }

    @Test
    fun `cluster layouts are reused and split by style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 4)
        val frc = FontRenderContext(null, false, false)
        val cluster = "\u0E01\u0E34"

        val plain = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        val plainAgain = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        val bold = layoutCache.clusterLayout(cluster, Font.BOLD, frc, fontCache)

        assertSame(plain, plainAgain)
        assertNotSame(plain, bold)
    }

    @Test
    fun `cluster layouts are bounded by lru capacity per style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 2)
        val frc = FontRenderContext(null, false, false)

        val first = layoutCache.clusterLayout("\u0E01\u0E34", Font.PLAIN, frc, fontCache)
        val second = layoutCache.clusterLayout("\u0E02\u0E34", Font.PLAIN, frc, fontCache)
        layoutCache.clusterLayout("\u0E01\u0E34", Font.PLAIN, frc, fontCache)
        layoutCache.clusterLayout("\u0E03\u0E34", Font.PLAIN, frc, fontCache)

        assertSame(first, layoutCache.clusterLayout("\u0E01\u0E34", Font.PLAIN, frc, fontCache))
        assertNotSame(second, layoutCache.clusterLayout("\u0E02\u0E34", Font.PLAIN, frc, fontCache))
    }

    @Test
    fun `cluster capacity is independent for each style`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 1)
        val frc = FontRenderContext(null, false, false)
        val cluster = "\u0E01\u0E34"

        val plain = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        val bold = layoutCache.clusterLayout(cluster, Font.BOLD, frc, fontCache)
        layoutCache.clusterLayout("\u0E02\u0E34", Font.PLAIN, frc, fontCache)

        assertNotSame(plain, layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache))
        assertSame(bold, layoutCache.clusterLayout(cluster, Font.BOLD, frc, fontCache))
    }

    @Test
    fun `cluster cache bulk eviction creates reusable free slots`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 20)
        val frc = FontRenderContext(null, false, false)

        repeat(21) { index ->
            val cluster = intArrayOf(0x1000 + index, 0x0301)
            layoutCache.clusterLayout(cluster, 0, cluster.size, Font.PLAIN, frc, fontCache)
        }

        assertEquals(19, layoutCache.cachedClusterLayoutCount())
    }

    @Test
    fun `clear invalidates cluster layouts`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 4)
        val frc = FontRenderContext(null, false, false)
        val cluster = "\u0E01\u0E34"

        val first = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)
        layoutCache.clear()
        val second = layoutCache.clusterLayout(cluster, Font.PLAIN, frc, fontCache)

        assertNotSame(first, second)
    }

    @Test
    fun `layouts are invalidated when font render context changes`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(codePointCapacity = 4)
        val firstFrc = FontRenderContext(null, false, false)
        val secondFrc = FontRenderContext(null, true, false)

        val first = layoutCache.codePointLayout(0x03A9, Font.PLAIN, firstFrc, fontCache)
        val second = layoutCache.codePointLayout(0x03A9, Font.PLAIN, secondFrc, fontCache)

        assertNotSame(first, second)
    }

    @Test
    fun `clusterLayout shapes bounded prefix for sequences exceeding structural length limits`() {
        // Arrange
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 4)
        val frc = FontRenderContext(null, false, false)
        val longInput = "A".repeat(TerminalComplexTextLayoutCache.MAX_CLUSTER_LENGTH + 1)

        // Act
        val layout = layoutCache.clusterLayout(longInput, Font.PLAIN, frc, fontCache)
        val replacementLayout = layoutCache.clusterLayout("\uFFFD", Font.PLAIN, frc, fontCache)

        // Assert
        assertEquals(TerminalComplexTextLayoutCache.MAX_CLUSTER_LENGTH, layout.characterCount)
        assertNotSame(
            replacementLayout,
            layout,
            "Long clusters must preserve a visible bounded prefix instead of collapsing to U+FFFD",
        )
    }

    @Test
    fun `clusterLayout reuses identical bounded prefixes for long inputs`() {
        // Arrange
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 4)
        val frc = FontRenderContext(null, false, false)
        val attackSequenceAlpha = "X".repeat(40)
        val attackSequenceBeta = "X".repeat(100)

        // Act
        val firstLayout = layoutCache.clusterLayout(attackSequenceAlpha, Font.PLAIN, frc, fontCache)
        val secondLayout = layoutCache.clusterLayout(attackSequenceBeta, Font.PLAIN, frc, fontCache)

        // Assert
        assertSame(
            firstLayout,
            secondLayout,
            "Long inputs with the same bounded prefix should reuse one shaped layout",
        )
    }

    @Test
    fun `clusterLayout does not collapse distinct long visible prefixes`() {
        val fontCache = fontCache()
        val layoutCache = TerminalComplexTextLayoutCache(clusterCapacityPerStyle = 4)
        val frc = FontRenderContext(null, false, false)

        val firstLayout = layoutCache.clusterLayout("X".repeat(40), Font.PLAIN, frc, fontCache)
        val secondLayout = layoutCache.clusterLayout("Y".repeat(40), Font.PLAIN, frc, fontCache)

        assertNotSame(firstLayout, secondLayout)
    }

    private fun fontCache(): TerminalFontCache {
        val cache = TerminalFontCache()
        cache.update(Font(Font.MONOSPACED, Font.PLAIN, 14), emptyList(), useSystemFallbackFonts = false)
        return cache
    }

    private fun TerminalComplexTextLayoutCache.cachedCodePointLayoutCount(): Int {
        val cache = declaredField("codePointLayouts").get(this)
        return cache.countCachedLayouts()
    }

    private fun TerminalComplexTextLayoutCache.cachedClusterLayoutCount(): Int {
        val caches = declaredField("clusterLayouts").get(this) as Array<*>
        return caches[Font.PLAIN]!!.countCachedLayouts()
    }

    private fun Any.countCachedLayouts(): Int {
        val layouts = declaredField("entryLayouts").get(this) as Array<*>
        return layouts.count { it != null }
    }

    private fun Any.declaredField(name: String) =
        javaClass.getDeclaredField(name).apply { isAccessible = true }
}
