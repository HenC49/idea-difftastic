package com.github.henc.difftastic.settings

import com.github.henc.difftastic.diff.DifftasticDiffTool
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.diagnostic.Logger

/**
 * Makes Difftastic the default viewer for the known two-side diff places, using the
 * same mechanism as the platform's viewer chooser (the per-place tool order).
 * Switching viewers manually in a diff window still works and sticks.
 */
object DifftasticToolOrder {
    private val LOG = Logger.getInstance(DifftasticToolOrder::class.java)
    private val TOOL = DifftasticDiffTool::class.java.name
    private val PLACES = listOf(
        DiffPlaces.DEFAULT,
        DiffPlaces.CHANGES_VIEW,
        DiffPlaces.VCS_FILE_HISTORY_VIEW,
        DiffPlaces.SHELVE_VIEW,
        DiffPlaces.COMMIT_DIALOG,
        DiffPlaces.BLANK,
    )

    fun isDefault(): Boolean = DiffSettingsHolder.DiffSettings.getSettings(DiffPlaces.DEFAULT)
        .diffToolsOrder.firstOrNull() == TOOL

    fun makeDefault() {
        for (place in PLACES) {
            val settings = DiffSettingsHolder.DiffSettings.getSettings(place)
            val order = settings.diffToolsOrder
            if (order.firstOrNull() == TOOL) continue
            if (TOOL in order) {
                // Someone deliberately moved it down; leave their choice alone.
                LOG.info("Difftastic is already present in the $place tool order, not reordering")
                continue
            }
            settings.diffToolsOrder = listOf(TOOL) + order
        }
    }

    fun removeDefault() {
        for (place in PLACES) {
            val settings = DiffSettingsHolder.DiffSettings.getSettings(place)
            val order = settings.diffToolsOrder
            if (TOOL in order) {
                settings.diffToolsOrder = order.filterNot { it == TOOL }
            }
        }
    }
}
