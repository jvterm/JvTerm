package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.TerminalRenderAttrs
import com.gagik.terminal.render.api.TerminalRenderCellFlags
import java.awt.Font

internal fun terminalFontStyle(attr: Long): Int {
    var style = Font.PLAIN
    if (TerminalRenderAttrs.isBold(attr)) style = style or Font.BOLD
    if (TerminalRenderAttrs.isItalic(attr)) style = style or Font.ITALIC
    return style
}

internal fun hasDrawableText(flags: Int): Boolean {
    return flags and TerminalRenderCellFlags.CODEPOINT != 0 ||
        flags and TerminalRenderCellFlags.CLUSTER != 0
}

internal fun isFastAsciiCell(flags: Int, codeWord: Int): Boolean {
    return flags == TerminalRenderCellFlags.CODEPOINT && codeWord in 0x20..0x7e
}

internal fun cellSpan(flags: Int): Int {
    return if (flags and TerminalRenderCellFlags.WIDE_LEADING != 0) 2 else 1
}
