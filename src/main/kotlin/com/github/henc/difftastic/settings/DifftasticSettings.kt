package com.github.henc.difftastic.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "DifftasticSettings", storages = [Storage("difftastic.xml")])
class DifftasticSettings : SimplePersistentStateComponent<DifftasticSettingsState>(DifftasticSettingsState()) {
    companion object {
        @JvmStatic
        fun getInstance(): DifftasticSettings = service()
    }
}

class DifftasticSettingsState : BaseState() {
    var enabled by property(true)
    var difftPath by string("")
    var extraArgs by string("")
    var timeoutSeconds by property(10)
    var maxFileSizeKb by property(1024)
}
