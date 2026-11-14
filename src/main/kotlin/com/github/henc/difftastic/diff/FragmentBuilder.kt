package com.github.henc.difftastic.diff

import com.github.henc.difftastic.engine.DifftSide
import com.github.henc.difftastic.engine.DifftSpan
import com.github.henc.difftastic.engine.TextLines
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl

/**
 * Converts difft's line alignment into the change-block fragment list expected by the
 * stock diff viewer: fragments cover only changed blocks, gaps between them are equal.
 *
 * Returns null when the alignment is not usable (non-monotonic, malformed), so the
 * caller can fall back to the built-in comparison.
 */
object FragmentBuilder {

    fun buildFromAlignment(
        alignment: List<Pair<Int?, Int?>>,
        spans: List<DifftSpan>,
        leftText: String,
        rightText: String,
    ): List<LineFragment>? {
        val leftCount = TextLines.count(leftText)
        val rightCount = TextLines.count(rightText)
        val leftStarts = TextLines.startOffsets(leftText, leftCount)
        val rightStarts = TextLines.startOffsets(rightText, rightCount)

        val novelLeft = HashSet<Int>()
        val novelRight = HashSet<Int>()
        for (span in spans) (if (span.side == DifftSide.LEFT) novelLeft else novelRight).add(span.line)

        // Validate monotonicity while collecting entries; phantom trailing lines
        // (difft assumes every file ends with a newline) are already null by here,
        // but keep the bounds check as defense in depth.
        var lastLeft = -1
        var lastRight = -1
        val entries = ArrayList<Pair<Int?, Int?>>(alignment.size)
        for ((rawLeft, rawRight) in alignment) {
            val left = rawLeft?.takeIf { it in 0 until leftCount }
            val right = rawRight?.takeIf { it in 0 until rightCount }
            if (left == null && right == null) continue
            if (left != null && left <= lastLeft) return null
            if (right != null && right <= lastRight) return null
            if (left != null) lastLeft = left
            if (right != null) lastRight = right
            entries.add(left to right)
        }

        val fragments = ArrayList<LineFragmentImpl>()
        var runLeftStart: Int? = null
        var runLeftEnd = 0
        var runRightStart: Int? = null
        var runRightEnd = 0
        var prevLeft = -1
        var prevRight = -1

        fun flushRun() {
            val leftStart = runLeftStart
            val rightStart = runRightStart
            if (leftStart != null || rightStart != null) {
                val startLine1 = leftStart ?: prevLeft + 1
                val endLine1 = if (leftStart != null) runLeftEnd + 1 else startLine1
                val startLine2 = rightStart ?: prevRight + 1
                val endLine2 = if (rightStart != null) runRightEnd + 1 else startLine2
                fragments.add(
                    LineFragmentImpl(
                        startLine1, endLine1, startLine2, endLine2,
                        leftStarts[startLine1], leftStarts[endLine1],
                        rightStarts[startLine2], rightStarts[endLine2],
                        null,
                    ),
                )
            }
            runLeftStart = null
            runRightStart = null
        }

        for ((left, right) in entries) {
            val changed = left == null || right == null || left in novelLeft || right in novelRight
            if (changed) {
                if (left != null) {
                    if (runLeftStart == null) runLeftStart = left
                    runLeftEnd = left
                }
                if (right != null) {
                    if (runRightStart == null) runRightStart = right
                    runRightEnd = right
                }
            } else {
                flushRun()
            }
            if (left != null) prevLeft = left
            if (right != null) prevRight = right
        }
        flushRun()

        // Lines difft never mentioned (dropped phantoms, trailing blanks) become a
        // final changed block rather than being lost.
        if (prevLeft + 1 < leftCount || prevRight + 1 < rightCount) {
            fragments.add(
                LineFragmentImpl(
                    prevLeft + 1, leftCount, prevRight + 1, rightCount,
                    leftStarts[prevLeft + 1], leftStarts[leftCount],
                    rightStarts[prevRight + 1], rightStarts[rightCount],
                    null,
                ),
            )
        }
        return fragments
    }

    /** Single whole-document change block, for created/deleted files (no alignment data). */
    fun buildWholeSide(leftText: String, rightText: String): List<LineFragment> {
        val leftCount = TextLines.count(leftText)
        val rightCount = TextLines.count(rightText)
        val leftStarts = TextLines.startOffsets(leftText, leftCount)
        val rightStarts = TextLines.startOffsets(rightText, rightCount)
        return listOf(
            LineFragmentImpl(
                0, leftCount, 0, rightCount,
                0, leftStarts[leftCount],
                0, rightStarts[rightCount],
                null,
            ),
        )
    }
}
