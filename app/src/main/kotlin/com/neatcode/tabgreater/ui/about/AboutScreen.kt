package com.neatcode.tabgreater.ui.about

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neatcode.tabgreater.BuildConfig
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.ui.components.TGIconButton
import com.neatcode.tabgreater.ui.components.TGTopBar
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * About · data sources & privacy · disclaimer · third-party software · licence texts.
 *
 * The licence texts are read from assets once, off the main thread: the GPL (the app's own
 * licence) and the font's OFL from `assets/licenses/`, and the Apache-2.0 text + NOTICE already
 * shipped with the chart bundle in `assets/chart/vendor/`. This screen is what satisfies the
 * "keep the notices" obligations of all three and tells the user where the numbers come from.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gpl by produceState<String?>(initialValue = null) { value = readAsset(context, GPL_ASSET) }
    val apache by produceState<String?>(initialValue = null) { value = readAsset(context, APACHE_ASSET) }
    val notice by produceState<String?>(initialValue = null) { value = readAsset(context, NOTICE_ASSET) }
    val ofl by produceState<String?>(initialValue = null) { value = readAsset(context, OFL_ASSET) }

    Column(
        modifier
            .fillMaxSize()
            .background(TG.Background),
    ) {
        TGTopBar {
            TGIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.about_title), style = TGType.appBarTitle, maxLines = 1)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SIDE_MARGIN),
        ) {
            Paragraph(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            Paragraph(stringResource(R.string.about_independent))

            Section(R.string.about_data_title)
            Paragraph(stringResource(R.string.about_data_body))

            Section(R.string.about_disclaimer_title)
            Paragraph(stringResource(R.string.about_disclaimer_body))

            Section(R.string.about_thirdparty_title)
            THIRD_PARTY.forEach { Paragraph("• $it") }
            Paragraph(stringResource(R.string.about_thirdparty_note))

            Section(R.string.about_licence_title)
            Mono(gpl)

            Section(R.string.about_notice_title)
            Mono(notice)

            Section(R.string.about_apache_title)
            Mono(apache)

            Section(R.string.about_ofl_title)
            Mono(ofl)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(titleRes: Int) {
    HorizontalDivider(
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        thickness = 1.dp,
        color = TG.Outline,
    )
    Text(
        text = stringResource(titleRes),
        style = TGType.sectionHeader,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = TGType.body,
        color = TG.TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

/** Licence text verbatim: monospace so the original line breaks survive. */
@Composable
private fun Mono(text: String?) {
    Text(
        text = text ?: stringResource(R.string.about_loading),
        style = TGType.body.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
        color = TG.TextTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

private suspend fun readAsset(context: Context, path: String): String = withContext(Dispatchers.IO) {
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
        .getOrElse { "(${it.message})" }
}

private const val GPL_ASSET = "licenses/GPL-3.0-or-later.txt"
private const val OFL_ASSET = "licenses/OFL-1.1-Righteous.txt"
private const val APACHE_ASSET = "chart/vendor/LICENSE"
private const val NOTICE_ASSET = "chart/vendor/NOTICE"
private val SIDE_MARGIN = 16.dp

/** Every third-party component that ends up in the APK. */
private val THIRD_PARTY = listOf(
    "KLineChart 10.0.2 — Apache-2.0 — github.com/klinecharts/KLineChart",
    "Righteous font by Astigmatic — SIL Open Font License 1.1 — fonts.google.com/specimen/Righteous",
    "Popular pairs list — Powered by CoinGecko — coingecko.com",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization — Apache-2.0 — kotlinlang.org",
    "AndroidX: Jetpack Compose, Material 3, Material Icons, Navigation, Room, DataStore, WorkManager, Glance, WebKit, Core SplashScreen — Apache-2.0 — developer.android.com/jetpack",
    "OkHttp — Apache-2.0 — square.github.io/okhttp",
    "Koin — Apache-2.0 — insert-koin.io",
)
