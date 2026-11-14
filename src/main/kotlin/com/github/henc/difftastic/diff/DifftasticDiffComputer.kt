package com.github.henc.difftastic.diff

import com.github.henc.difftastic.DifftasticNotifications
import com.github.henc.difftastic.engine.DifftResult
import com.github.henc.difftastic.engine.DifftRunner
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

/**
 * Feeds difftastic-computed fragments into the stock diff viewer.
 *
 * Installed on the request under [DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER], which
 * `DiffUtil.createTextDiffProvider` picks up — that is the whole integration. The
 * viewer calls this from its background rediff, so running a subprocess here is
 * expected and cancellable.
 */
class DifftasticDiffComputer(
    private val project: Project?,
    private val fileName: String,
) : DiffUserDataKeysEx.DiffComputer {
    private val lastChanged = AtomicReference<DifftResult.Changed?>()

    /** Last successful result, for the rich viewer to colour atom spans from. */
    fun lastChanged(): DifftResult.Changed? = lastChanged.get()

    override fun compute(
        text1: CharSequence,
        text2: CharSequence,
        policy: ComparisonPolicy,
        innerChanges: Boolean,
        indicator: ProgressIndicator,
    ): List<LineFragment> {
        // The comparison policy is deliberately not consulted: a structural diff is
        // already whitespace-insensitive, and honouring e.g. "ignore whitespaces"
        // by falling back would silently disable this viewer for many users.
        val leftText = text1.toString()
        val rightText = text2.toString()
        lastChanged.set(null)

        val result = try {
            indicator.checkCanceled()
            DifftRunner.run(leftText, rightText, fileName, indicator)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (t: Throwable) {
            LOG.warn("Difftastic diff failed", t)
            DifftResult.Unsupported(t.message ?: t.toString())
        }

        return when (result) {
            DifftResult.Unchanged -> emptyList()

            is DifftResult.Unsupported -> {
                DifftasticNotifications.reportFallback(project, result.reason)
                builtIn(text1, text2, policy, innerChanges, indicator)
            }

            is DifftResult.Changed -> {
                val fragments = if (result.alignment == null) {
                    FragmentBuilder.buildWholeSide(leftText, rightText)
                } else {
                    FragmentBuilder.buildFromAlignment(result.alignment, result.spans, leftText, rightText)
                }
                if (fragments != null) {
                    lastChanged.set(result)
                    fragments
                } else {
                    DifftasticNotifications.reportFallback(project, "difft returned an unusable line alignment")
                    builtIn(text1, text2, policy, innerChanges, indicator)
                }
            }
        }
    }

    private fun builtIn(
        text1: CharSequence,
        text2: CharSequence,
        policy: ComparisonPolicy,
        innerChanges: Boolean,
        indicator: ProgressIndicator,
    ): List<LineFragment> {
        val comparison = ComparisonManager.getInstance()
        return if (innerChanges) {
            comparison.compareLinesInner(text1, text2, policy, indicator)
        } else {
            comparison.compareLines(text1, text2, policy, indicator)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(DifftasticDiffComputer::class.java)
    }
}
