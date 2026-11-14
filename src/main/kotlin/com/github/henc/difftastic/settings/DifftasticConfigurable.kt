package com.github.henc.difftastic.settings

import com.github.henc.difftastic.DifftasticBundle
import com.github.henc.difftastic.engine.DifftLocator
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class DifftasticConfigurable : Configurable {
    private val state get() = DifftasticSettings.getInstance().state
    private lateinit var panel: DialogPanel
    private val statusLabel = JBLabel()

    override fun getDisplayName(): String = DifftasticBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        panel = panel {
            group(displayName) {
                row {
                    checkBox(DifftasticBundle.message("settings.enabled"))
                        .bindSelected(state::enabled)
                }
                row(DifftasticBundle.message("settings.executable.label")) {
                    textField()
                        .bindText({ state.difftPath ?: "" }, { state.difftPath = it })
                        .align(AlignX.FILL)
                }
                row {
                    comment(DifftasticBundle.message("settings.executable.empty"))
                }
                row {
                    button(DifftasticBundle.message("settings.detect.button")) { detect() }
                    cell(statusLabel)
                }
                row(DifftasticBundle.message("settings.extra.args.label")) {
                    textField()
                        .bindText({ state.extraArgs ?: "" }, { state.extraArgs = it })
                        .align(AlignX.FILL)
                }
                row(DifftasticBundle.message("settings.timeout.label")) {
                    intField({ state.timeoutSeconds }, { state.timeoutSeconds = it }, 1..600)
                }
                row(DifftasticBundle.message("settings.max.size.label")) {
                    intField({ state.maxFileSizeKb }, { state.maxFileSizeKb = it }, 16..102400)
                }
            }
            group {
                row {
                    val defaultBox = checkBox(DifftasticBundle.message("settings.default.checkbox")).component
                    defaultBox.isSelected = DifftasticToolOrder.isDefault()
                    defaultBox.addActionListener {
                        // Applied immediately: it is a global IDE preference, not part of this page's state.
                        if (defaultBox.isSelected) DifftasticToolOrder.makeDefault() else DifftasticToolOrder.removeDefault()
                    }
                }
                row {
                    comment(DifftasticBundle.message("settings.default.hint"))
                }
                row {
                    link(DifftasticBundle.message("settings.install.link")) {
                        BrowserUtil.browse("https://github.com/Wilfred/difftastic#installation")
                    }
                }
            }
        }
        return panel
    }

    /** Plain text field bound to an int with range clamping; empty input keeps the previous value. */
    private fun com.intellij.ui.dsl.builder.Row.intField(
        get: () -> Int,
        set: (Int) -> Unit,
        range: IntRange,
    ): com.intellij.ui.dsl.builder.Cell<com.intellij.ui.components.JBTextField> {
        val field = com.intellij.ui.components.JBTextField()
        return cell(field).bindText(
            { get().toString() },
            { text -> text.trim().toIntOrNull()?.coerceIn(range.first, range.last)?.let(set) },
        )
    }

    private fun detect() {
        statusLabel.text = "…"
        DifftLocator.checkConfigurationAsync(state.difftPath ?: "") { located ->
            statusLabel.text = when {
                located == null -> DifftasticBundle.message("settings.detect.fail")
                located.version == null -> located.executable.toString()
                else -> DifftasticBundle.message("settings.detect.ok", located.version, located.executable.toString())
            }
        }
    }

    override fun isModified(): Boolean = panel.isModified()

    override fun apply() = panel.apply()

    override fun reset() = panel.reset()
}
