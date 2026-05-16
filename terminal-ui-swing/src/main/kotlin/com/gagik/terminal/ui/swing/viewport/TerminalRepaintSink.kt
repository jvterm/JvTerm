package com.gagik.terminal.ui.swing.viewport

/**
 * Receives repaint requests computed by [TerminalSwingRepaintPlanner].
 *
 * This interface keeps repaint coordinates as JVM primitives and avoids
 * allocating Kotlin function objects for each published render frame.
 */
internal interface TerminalRepaintSink {
    /**
     * Requests repainting the whole component.
     */
    fun requestFullRepaint()

    /**
     * Requests repainting one bounded component region.
     *
     * @param x left edge of the repaint region in component pixels.
     * @param y top edge of the repaint region in component pixels.
     * @param width repaint region width in pixels.
     * @param height repaint region height in pixels.
     */
    fun requestRegionRepaint(x: Int, y: Int, width: Int, height: Int)
}
