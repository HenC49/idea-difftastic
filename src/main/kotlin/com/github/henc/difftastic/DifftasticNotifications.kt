package com.github.henc.difftastic

import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

object DifftasticNotifications {
    private const val GROUP = "Difftastic"
    private const val REASON_BINARY_MISSING = "binary-missing"

    private val reported = ConcurrentHashMap.newKeySet<String>()

    fun reportFallback(project: Project?, reason: String) {
        if (!reported.add(reason)) return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP)
            .createNotification(
                DifftasticBundle.message("notification.fallback.title"),
                if (reason == REASON_BINARY_MISSING) {
                    DifftasticBundle.message("notification.fallback.binary.missing")
                } else {
                    DifftasticBundle.message("notification.fallback.reason", reason)
                },
                NotificationType.WARNING,
            )
        if (reason == REASON_BINARY_MISSING) {
            notification.addAction(
                BrowseNotificationAction(
                    DifftasticBundle.message("settings.install.link"),
                    "https://github.com/Wilfred/difftastic#installation",
                ),
            )
        }
        notification.notify(project)
    }

    fun reportMissingBinary(project: Project?) = reportFallback(project, REASON_BINARY_MISSING)
}
