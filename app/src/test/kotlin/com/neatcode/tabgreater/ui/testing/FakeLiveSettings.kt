package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.live.LiveSettings
import com.neatcode.tabgreater.core.live.LiveSettingsValues
import com.neatcode.tabgreater.core.live.WidgetRefresh
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [LiveSettings]; the Settings view model never touches DataStore in these tests. */
class FakeLiveSettings(initial: LiveSettingsValues = LiveSettingsValues()) : LiveSettings {

    private val state = MutableStateFlow(initial)

    override val values: Flow<LiveSettingsValues> = state

    override val widgetRefresh: Flow<WidgetRefresh> = state.map { it.widgetRefresh }
    override val wifiOnly: Flow<Boolean> = state.map { it.wifiOnly }

    /** Current values, for assertions. */
    val current: LiveSettingsValues get() = state.value

    override suspend fun setWidgetRefresh(refresh: WidgetRefresh) {
        state.value = state.value.copy(widgetRefresh = refresh)
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        state.value = state.value.copy(wifiOnly = enabled)
    }
}
