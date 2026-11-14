package com.github.henc.difftastic.diff

import com.github.henc.difftastic.engine.DifftSide
import com.intellij.diff.DiffContext
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffChange
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter

/**
 * The stock two-side viewer with difftastic's novel atoms highlighted on top.
 * Deliberately a subclass of [SimpleDiffViewer] rather than a new viewer, so the
 * gutter, folding, change navigation, aligned mode and every diff action keep
 * working; the only addition is a set of removable range highlighters.
 */
class DifftasticDiffViewer(
    context: DiffContext,
    private val contentRequest: ContentDiffRequest,
    private val computer: DifftasticDiffComputer,
) : SimpleDiffViewer(context, contentRequest) {

    private val atomHighlighters = ArrayList<RangeHighlighter>()

    override fun apply(changes: MutableList<out SimpleDiffChange>?, isContentsEqual: Boolean): Runnable {
        val applyBase = super.apply(changes, isContentsEqual)
        return Runnable {
            applyBase.run()
            installAtomHighlighters()
        }
    }

    private fun installAtomHighlighters() {
        val result = computer.lastChanged() ?: return
        for (span in result.spans) {
            val editor = if (span.side == DifftSide.LEFT) editor1 else editor2
            val document = editor.document
            if (span.line >= document.lineCount) continue
            val lineStart = document.getLineStartOffset(span.line)
            val lineEnd = document.getLineEndOffset(span.line)
            val start = (lineStart + span.start).coerceIn(lineStart, lineEnd)
            val end = (lineStart + span.end).coerceIn(lineStart, lineEnd)
            if (start >= end) continue
            atomHighlighters.add(
                editor.markupModel.addRangeHighlighter(
                    start,
                    end,
                    HighlighterLayer.SELECTION - 1,
                    DifftColors.attributesFor(span.side),
                    HighlighterTargetArea.EXACT_RANGE,
                ),
            )
        }
    }

    private fun removeAtomHighlighters() {
        atomHighlighters.forEach { it.dispose() }
        atomHighlighters.clear()
    }

    override fun clearDiffPresentation() {
        removeAtomHighlighters()
        super.clearDiffPresentation()
    }

    override fun onDispose() {
        removeAtomHighlighters()
        // Drop the custom computer so that switching back to a built-in viewer
        // computes its own diff instead of silently reusing difftastic's.
        contentRequest.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, null)
        super.onDispose()
    }
}
