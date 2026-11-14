package com.github.henc.difftastic.diff

import com.github.henc.difftastic.engine.DifftSide
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Colors for difftastic's novel atoms, layered on top of the stock diff line
 * backgrounds. Deliberately word-diff-like so the result reads as "which exact
 * tokens changed inside the changed line".
 */
object DifftColors {
    private val LEFT_ATTRIBUTES = TextAttributes().apply {
        backgroundColor = JBColor(Color(0xFF, 0xC9, 0xC9), Color(0x53, 0x2B, 0x30))
    }
    private val RIGHT_ATTRIBUTES = TextAttributes().apply {
        backgroundColor = JBColor(Color(0xB5, 0xE8, 0xA6), Color(0x2D, 0x53, 0x27))
    }

    fun attributesFor(side: DifftSide): TextAttributes =
        when (side) {
            DifftSide.LEFT -> LEFT_ATTRIBUTES
            DifftSide.RIGHT -> RIGHT_ATTRIBUTES
        }
}
