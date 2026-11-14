package com.github.henc.difftastic.diff

import com.github.henc.difftastic.engine.DifftHighlight
import com.github.henc.difftastic.engine.DifftSide
import com.github.henc.difftastic.engine.DifftSpan
import com.intellij.diff.fragments.LineFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentBuilderTest {
    private fun span(side: DifftSide, line: Int, start: Int, end: Int) =
        DifftSpan(side, line, start, end, DifftHighlight.NORMAL)

    private fun assertMonotonic(fragments: List<LineFragment>, leftCount: Int, rightCount: Int) {
        var lastLeft = 0
        var lastRight = 0
        for (fragment in fragments) {
            assertTrue("startLine1 after previous end", fragment.startLine1 >= lastLeft)
            assertTrue("startLine2 after previous end", fragment.startLine2 >= lastRight)
            assertTrue(fragment.startLine1 <= fragment.endLine1)
            assertTrue(fragment.startLine2 <= fragment.endLine2)
            assertTrue(fragment.endLine1 <= leftCount)
            assertTrue(fragment.endLine2 <= rightCount)
            assertTrue(fragment.startOffset1 <= fragment.endOffset1)
            assertTrue(fragment.startOffset2 <= fragment.endOffset2)
            lastLeft = fragment.endLine1
            lastRight = fragment.endLine2
        }
    }

    @Test
    fun `matched line with novel atom becomes a changed block`() {
        val left = "a\nb\nc"
        val right = "a\nb2\nc"
        val fragments = FragmentBuilder.buildFromAlignment(
            alignment = listOf(0 to 0, 1 to 1, 2 to 2),
            spans = listOf(span(DifftSide.RIGHT, 1, 1, 2)),
            leftText = left,
            rightText = right,
        )!!

        assertEquals(1, fragments.size)
        val fragment = fragments[0]
        assertEquals(1, fragment.startLine1)
        assertEquals(2, fragment.endLine1)
        assertEquals(1, fragment.startLine2)
        assertEquals(2, fragment.endLine2)
        assertEquals(2, fragment.startOffset1)
        assertEquals(4, fragment.endOffset1)
        assertEquals(2, fragment.startOffset2)
        assertEquals(5, fragment.endOffset2)
        assertMonotonic(fragments, 2, 2)
    }

    @Test
    fun `adjacent delete and insert merge into one block`() {
        val fragments = FragmentBuilder.buildFromAlignment(
            alignment = listOf(0 to 0, 1 to null, null to 1, 2 to 2),
            spans = emptyList(),
            leftText = "a\nb\nc",
            rightText = "a\nx\nc",
        )!!

        assertEquals(1, fragments.size)
        assertEquals(1, fragments[0].startLine1)
        assertEquals(2, fragments[0].endLine1)
        assertEquals(1, fragments[0].startLine2)
        assertEquals(2, fragments[0].endLine2)
    }

    @Test
    fun `pure insertion has an empty left range`() {
        val fragments = FragmentBuilder.buildFromAlignment(
            alignment = listOf(null to 0, 0 to 1),
            spans = listOf(span(DifftSide.RIGHT, 0, 0, 1)),
            leftText = "b",
            rightText = "a\nb",
        )!!

        assertEquals(1, fragments.size)
        val fragment = fragments[0]
        assertEquals(0, fragment.startLine1)
        assertEquals(0, fragment.endLine1)
        assertEquals(0, fragment.startOffset1)
        assertEquals(0, fragment.endOffset1)
        assertEquals(0, fragment.startLine2)
        assertEquals(1, fragment.endLine2)
        assertEquals(0, fragment.startOffset2)
        assertEquals(2, fragment.endOffset2)
    }

    @Test
    fun `pure deletion has an empty right range`() {
        val fragments = FragmentBuilder.buildFromAlignment(
            alignment = listOf(0 to 0, 1 to null),
            spans = emptyList(),
            leftText = "a\nb",
            rightText = "a",
        )!!

        assertEquals(1, fragments.size)
        assertEquals(1, fragments[0].startLine1)
        assertEquals(2, fragments[0].endLine1)
        assertEquals(1, fragments[0].startLine2)
        assertEquals(1, fragments[0].endLine2)
        assertEquals(1, fragments[0].startOffset2)
        assertEquals(1, fragments[0].endOffset2)
    }

    @Test
    fun `separated changes produce two blocks`() {
        val fragments = FragmentBuilder.buildFromAlignment(
            alignment = listOf(0 to 0, 1 to null, null to 1, 2 to 2, 3 to 3, 4 to null, null to 4, 5 to 5),
            spans = emptyList(),
            leftText = "a\nb\nc\nd\ne\nf",
            rightText = "a\nx\nc\nd\ny\nf",
        )!!

        assertEquals(2, fragments.size)
        assertEquals(1, fragments[0].startLine1)
        assertEquals(4, fragments[1].startLine1)
        assertMonotonic(fragments, 6, 6)
    }

    @Test
    fun `uncovered trailing lines become a final block`() {
        val fragments = FragmentBuilder.buildFromAlignment(
            alignment = listOf(0 to 0),
            spans = emptyList(),
            leftText = "a\nb",
            rightText = "a\nb",
        )!!

        assertEquals(1, fragments.size)
        assertEquals(1, fragments[0].startLine1)
        assertEquals(2, fragments[0].endLine1)
        assertEquals(1, fragments[0].startLine2)
        assertEquals(2, fragments[0].endLine2)
    }

    @Test
    fun `non-monotonic alignment is rejected`() {
        val fragments = FragmentBuilder.buildFromAlignment(
            alignment = listOf(1 to 1, 0 to 0),
            spans = emptyList(),
            leftText = "a\nb",
            rightText = "a\nb",
        )
        assertNull(fragments)
    }

    @Test
    fun `created file becomes one whole-side block`() {
        val fragments = FragmentBuilder.buildWholeSide("", "a\nb\nc")
        assertEquals(1, fragments.size)
        assertEquals(0, fragments[0].startLine1)
        assertEquals(0, fragments[0].endLine1)
        assertEquals(0, fragments[0].startLine2)
        assertEquals(3, fragments[0].endLine2)
        assertEquals(5, fragments[0].endOffset2) // "a\nb\nc".length
        assertNotNull(fragments)
    }

    @Test
    fun `deleted file becomes one whole-side block`() {
        val fragments = FragmentBuilder.buildWholeSide("a\nb", "")
        assertEquals(1, fragments.size)
        assertEquals(0, fragments[0].startLine1)
        assertEquals(2, fragments[0].endLine1)
        assertEquals(0, fragments[0].startLine2)
        assertEquals(0, fragments[0].endLine2)
        assertEquals(3, fragments[0].endOffset1)
    }
}
