package com.gagik.terminal.ui.swing.render.platform

import java.awt.image.BufferedImage
import java.util.*

/**
 * Rasterizes emoji with the host platform text stack when available.
 */
internal interface TerminalPlatformEmojiRasterizer {
    /**
     * Returns whether this rasterizer can attempt native emoji rendering.
     */
    val available: Boolean

    /**
     * Rasterizes [text] into a transparent image no larger than [pixelSize].
     */
    fun rasterize(text: String, pixelSize: Int): BufferedImage?

    companion object {
        fun create(): TerminalPlatformEmojiRasterizer {
            val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
            return if ("windows" in osName) {
                WindowsColorEmojiRasterizer.create()
            } else {
                UnavailablePlatformEmojiRasterizer
            }
        }
    }
}

private object UnavailablePlatformEmojiRasterizer : TerminalPlatformEmojiRasterizer {
    override val available: Boolean = false

    override fun rasterize(text: String, pixelSize: Int): BufferedImage? = null
}
