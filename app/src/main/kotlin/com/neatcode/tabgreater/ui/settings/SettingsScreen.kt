package com.neatcode.tabgreater.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neatcode.tabgreater.BuildConfig
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.live.LiveIntents
import com.neatcode.tabgreater.core.live.WidgetRefresh
import com.neatcode.tabgreater.core.model.ImportMode
import com.neatcode.tabgreater.ui.components.TGAppBar
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import org.koin.androidx.compose.koinViewModel

/**
 * Settings: the display switches, the live/widget knobs, the JSON backup of every watchlist and
 * the about block.
 *
 * The chrome is the watchlist's own app bar so the bottom navigation does not jump between the two
 * root destinations; its ☰ / 🔍 are inert here. Export and import go through the Storage Access
 * Framework, which needs no storage permission at all.
 *
 * @param isSamsung whether to offer the One UI checklist. A parameter rather than a direct
 *   [Build.MANUFACTURER] read so previews — and the emulator, which is not a Samsung — can see it.
 */
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    isSamsung: Boolean = IS_SAMSUNG_DEVICE,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var openSheet by rememberSaveable { mutableStateOf(RefreshSheet.NONE) }
    var showSamsungChecklist by rememberSaveable { mutableStateOf(false) }
    var showDonate by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFiles.MIME_JSON),
    ) { uri -> uri?.let { viewModel.exportTo(it.toString()) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.loadImport(it.toString()) } }

    // The battery dialog reports nothing useful in its result, so the answer is re-read from
    // PowerManager afterwards — as it is on every resume, for the trip through app settings.
    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _: ActivityResult -> viewModel.refreshPermissions() }

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> snackbarHostState.showSnackbar(context.describe(event)) }
    }

    Column(modifier.fillMaxSize().background(TG.Background)) {
        TGAppBar()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SectionHeader(R.string.settings_section_display)
                SettingRow(
                    title = stringResource(R.string.settings_shrink_zeros),
                    subtitle = stringResource(R.string.settings_shrink_zeros_sub),
                    onClick = { viewModel.setShrinkZeros(!state.shrinkZeros) },
                    trailing = {
                        Switch(
                            checked = state.shrinkZeros,
                            onCheckedChange = viewModel::setShrinkZeros,
                            colors = ttSwitchColors(),
                        )
                    },
                )
                SettingRow(
                    title = stringResource(R.string.settings_watchlist_rate),
                    subtitle = stringResource(
                        R.string.settings_watchlist_rate_sub,
                        secondsLabel(state.watchlistRefreshMs),
                    ),
                    subtitleMaxLines = 2,
                    onClick = { openSheet = RefreshSheet.WATCHLIST },
                )

                SectionHeader(R.string.settings_section_watchlists)
                SettingRow(
                    title = stringResource(R.string.settings_export),
                    subtitle = stringResource(R.string.settings_export_sub),
                    leadingIcon = TGIcons.Upload,
                    enabled = !state.busy,
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                )
                SettingRow(
                    title = stringResource(R.string.settings_import),
                    subtitle = stringResource(R.string.settings_import_sub),
                    leadingIcon = TGIcons.Download,
                    enabled = !state.busy,
                    onClick = { importLauncher.launch(BackupFiles.OPEN_MIME_TYPES) },
                )

                SectionHeader(R.string.settings_section_widgets)
                SettingRow(
                    title = stringResource(R.string.settings_widget_refresh),
                    subtitle = widgetRefreshLabel(state.live.widgetRefresh),
                    onClick = { openSheet = RefreshSheet.WIDGET },
                )
                HintText(R.string.settings_widget_refresh_hint)
                // The Wi-Fi gate governs the socket, and only Live opens one — in a timed mode the
                // row would be a switch that changes nothing, so it is not offered at all.
                if (state.live.widgetRefresh == WidgetRefresh.LIVE) {
                    SettingRow(
                        title = stringResource(R.string.settings_wifi_only),
                        subtitle = stringResource(R.string.settings_wifi_only_sub),
                        subtitleMaxLines = 2,
                        onClick = { viewModel.setWifiOnly(!state.live.wifiOnly) },
                        trailing = {
                            Switch(
                                checked = state.live.wifiOnly,
                                onCheckedChange = viewModel::setWifiOnly,
                                colors = ttSwitchColors(),
                            )
                        },
                    )
                }
                SettingRow(
                    title = stringResource(R.string.settings_battery),
                    subtitle = stringResource(
                        if (state.batteryUnrestricted) {
                            R.string.settings_battery_unrestricted
                        } else {
                            R.string.settings_battery_restricted
                        },
                    ),
                    subtitleMaxLines = 2,
                    onClick = {
                        // null = already allowlisted; the list screen is then the only thing left
                        // to offer, and it is also the fallback for OEMs without the one-tap dialog.
                        // The play flavour has no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission,
                        // so it always goes to the list screen instead of the one-tap dialog.
                        val intent = LiveIntents.requestIgnoreBatteryOptimizations(context)
                            ?.takeIf { Donate.isFossBuild }
                            ?: LiveIntents.batteryOptimizationSettings()
                        systemSettingsLauncher.launchOrIgnore(intent)
                    },
                )
                SettingRow(
                    title = stringResource(R.string.settings_live_status),
                    subtitle = liveStatusText(state.live, state.diagnostics),
                    subtitleMaxLines = STATUS_MAX_LINES,
                )
                if (isSamsung) {
                    SettingRow(
                        title = stringResource(R.string.settings_samsung),
                        subtitle = stringResource(R.string.settings_samsung_sub),
                        onClick = { showSamsungChecklist = true },
                    )
                }

                SectionHeader(R.string.settings_section_about)
                SettingRow(
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_sub, BuildConfig.VERSION_NAME),
                    subtitleMaxLines = 2,
                    onClick = onOpenAbout,
                )
                SettingRow(
                    title = stringResource(R.string.settings_source_title),
                    subtitle = stringResource(R.string.settings_source_sub),
                    onClick = { context.openUrl(SOURCE_URL) },
                )
                // Play forbids donation links outside Play Billing, so the `play` flavour has no row.
                if (Donate.ENABLED) {
                    SettingRow(
                        title = stringResource(R.string.settings_donate_title),
                        subtitle = stringResource(R.string.settings_donate_sub),
                        onClick = { showDonate = true },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = TG.Surface,
                    contentColor = TG.TextPrimary,
                    actionColor = TG.Accent,
                )
            }
        }
    }

    state.pendingImport?.let { pending ->
        ImportModeDialog(
            pending = pending,
            onReplace = { viewModel.confirmImport(ImportMode.REPLACE) },
            onMerge = { viewModel.confirmImport(ImportMode.MERGE) },
            onDismiss = viewModel::cancelImport,
        )
    }

    // Picking a value has to take the sheet out of the composition itself: the row hides the sheet
    // programmatically, and ModalBottomSheet only reports onDismissRequest for a scrim tap, a drag
    // or the back gesture. Left open, `openSheet` would keep the (hidden) dialog window on screen
    // and tapping the row again would write the same value, so the picker never reopened.
    val closeSheet = { openSheet = RefreshSheet.NONE }
    when (openSheet) {
        RefreshSheet.NONE -> Unit
        RefreshSheet.WATCHLIST -> WatchlistRateSheet(
            current = state.watchlistRefreshMs,
            onPick = { ms -> viewModel.setWatchlistRefreshMs(ms); closeSheet() },
            onDismiss = closeSheet,
        )

        RefreshSheet.WIDGET -> WidgetRefreshSheet(
            current = state.live.widgetRefresh,
            onPick = { refresh -> viewModel.setWidgetRefresh(refresh); closeSheet() },
            onDismiss = closeSheet,
        )
    }

    if (showSamsungChecklist) {
        SamsungChecklistDialog(
            onOpenAppSettings = {
                showSamsungChecklist = false
                systemSettingsLauncher.launchOrIgnore(appDetailsSettings(context))
            },
            onDismiss = { showSamsungChecklist = false },
        )
    }

    if (showDonate) {
        DonateDialog(onDismiss = { showDonate = false })
    }
}

/**
 * One UI is the only OEM whose background-limit screens are documented well enough to spell out
 *; everywhere else the checklist would be a guess, so the row stays hidden.
 */
internal val IS_SAMSUNG_DEVICE: Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

/** Accent track, white thumb: the app's switch, shared by every switch on this screen. */
@Composable
private fun ttSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = TG.TextPrimary,
    checkedTrackColor = TG.Accent,
    checkedBorderColor = TG.Accent,
    uncheckedThumbColor = TG.TextSecondary,
    uncheckedTrackColor = TG.ChipFill,
    uncheckedBorderColor = TG.Outline,
)

/** `Settings → Apps → TabGreater`, the entry point of the Samsung checklist's first step. */
private fun appDetailsSettings(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

/** OEMs do remove these screens; a missing one must not take the Settings tab down with it. */
private fun ActivityResultLauncher<Intent>.launchOrIgnore(intent: Intent) {
    try {
        launch(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("SettingsScreen", "no activity for ${intent.action}", e)
    }
}

@Composable
private fun SectionHeader(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = TGType.sectionHeader,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SIDE_MARGIN, end = SIDE_MARGIN, top = SECTION_TOP, bottom = SECTION_BOTTOM),
    )
}

/**
 * A paragraph of explanation between rows, aligned with them. Used where the consequence of a
 * setting is a sentence rather than a value — the widget cadence being the only such case so far.
 */
@Composable
private fun HintText(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = TGType.listSubtitle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SIDE_MARGIN, end = SIDE_MARGIN, bottom = HINT_BOTTOM),
    )
}

/** The project page. Outbound links are limited to this and Ko-fi; never an exchange or a referral. */
private const val SOURCE_URL = "https://github.com/NeatCode-Labs/TabGreater"

internal fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        Log.w("Settings", "no browser for $url", e)
    }
}

/** A 56 dp settings row: optional leading glyph, title over subtitle, optional trailing control. */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    subtitleMaxLines: Int = 1,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = SIDE_MARGIN, vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (enabled) TG.TextSecondary else TG.TextTertiary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = TGType.settingTitle,
                color = if (enabled) TG.TextPrimary else TG.TextTertiary,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = TGType.listSubtitle, maxLines = subtitleMaxLines)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** Turns a one-shot view-model event into its snackbar text. */
private fun Context.describe(event: SettingsEvent): String = when (event) {
    is SettingsEvent.Exported ->
        resources.getQuantityString(R.plurals.settings_exported, event.watchlists, event.watchlists)

    is SettingsEvent.ExportFailed -> event.reason
        ?.let { getString(R.string.settings_export_failed, it) }
        ?: getString(R.string.settings_export_failed_unknown)

    is SettingsEvent.Imported -> {
        // "merged into an existing list" is not "imported": report both counts separately.
        val summary = getString(
            R.string.settings_imported,
            event.result.watchlistsAdded,
            event.result.watchlistsMerged,
            event.result.itemsAdded,
            event.result.itemsSkipped,
        )
        if (event.result.watchlistsSkipped > 0) {
            getString(R.string.settings_imported_over_limit, summary, event.result.watchlistsSkipped)
        } else {
            summary
        }
    }

    SettingsEvent.NotABackup -> getString(R.string.settings_import_not_a_backup)

    is SettingsEvent.ImportFailed -> event.reason
        ?.let { getString(R.string.settings_import_failed, it) }
        ?: getString(R.string.settings_import_failed_unknown)
}

/** The Status row is two lines, three when the live layer has an error to report. */
private const val STATUS_MAX_LINES = 3

private val SIDE_MARGIN = 16.dp
private val ROW_HEIGHT = 56.dp
private val ROW_VERTICAL_PADDING = 8.dp
private val SECTION_TOP = 20.dp
private val SECTION_BOTTOM = 8.dp
private val HINT_BOTTOM = 12.dp
