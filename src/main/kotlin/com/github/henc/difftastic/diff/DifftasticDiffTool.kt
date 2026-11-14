package com.github.henc.difftastic.diff

import com.github.henc.difftastic.engine.DifftLocator
import com.github.henc.difftastic.settings.DifftasticSettings
import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.util.Key

/**
 * Registers "Difftastic" in the diff window's viewer chooser. Being a [FrameDiffTool]
 * is what produces the toolbar toggle; the platform remembers the chosen viewer per
 * diff place.
 */
class DifftasticDiffTool : FrameDiffTool {
    override fun getName(): String = "Difftastic"

    override fun canShow(context: DiffContext, request: DiffRequest): Boolean {
        if (!DifftasticSettings.getInstance().state.enabled) return false
        val contentRequest = request as? ContentDiffRequest ?: return false
        if (contentRequest.contents.size != 2) return false
        if (contentRequest.contents.any { it !is DocumentContent }) return false
        if (DifftLocator.locate(DifftasticSettings.getInstance().state.difftPath ?: "") == null) return false
        return SimpleDiffViewer.canShowRequest(context, request)
    }

    override fun createComponent(context: DiffContext, request: DiffRequest): FrameDiffTool.DiffViewer {
        val contentRequest = request as ContentDiffRequest
        val computer = DifftasticDiffComputer(context.project, fileNameFor(contentRequest))
        contentRequest.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, computer)
        contentRequest.putUserData(COMPUTER_KEY, computer)
        return DifftasticDiffViewer(context, contentRequest, computer)
    }

    /** Prefers a real file name (difft language detection), then the request title, then a plain fallback. */
    private fun fileNameFor(request: ContentDiffRequest): String {
        for (content in request.contents) {
            (content as? FileContent)?.file?.name?.let { return it }
            (content as? DocumentContent)?.highlightFile?.name?.let { return it }
        }
        val title = (request as? SimpleDiffRequest)?.title
        val fromTitle = title?.lineSequence()?.firstOrNull()?.let { line ->
            line.substringAfterLast(' ').takeIf { it.length in 1..64 && it.contains('.') }
        }
        return fromTitle ?: "content.txt"
    }

    companion object {
        /** Lets the rich viewer reach the computer that produced its fragments. */
        val COMPUTER_KEY: Key<DifftasticDiffComputer> = Key.create("difftastic.computer")
    }
}
