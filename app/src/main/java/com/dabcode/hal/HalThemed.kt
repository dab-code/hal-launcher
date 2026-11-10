package com.dabcode.hal

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dabcode.settings.SettingsViewModel
import com.dabcode.settings.SettingsViewModelFactory
import com.dabcode.settings.UiStyle

/** Get the SettingsViewModel of this Activity. The theme starts from the values in the HALApplication cache. */
fun ComponentActivity.settingsViewModels(): Lazy<SettingsViewModel> = viewModels {
    val app = application as HALApplication
    SettingsViewModelFactory(app.settingsRepository, app.cachedIsDarkMode, app.cachedUiStyle, app.cachedFontSizeScale)
}

/** Apply the user theme from [settingsViewModel] to [content]. All screens get the theme here. */
@Composable
fun HalThemed(settingsViewModel: SettingsViewModel, content: @Composable () -> Unit) {
    val isDarkMode by settingsViewModel.isDarkMode.collectAsStateWithLifecycle()
    val uiStyle by settingsViewModel.uiStyle.collectAsStateWithLifecycle()
    val fontSizeScale by settingsViewModel.fontSizeScale.collectAsStateWithLifecycle()

    HalTheme(isDarkMode = isDarkMode, uiStyle = uiStyle, fontSizeScale = fontSizeScale, content = content)
}

/** The five colors of the theme. All styles use the same colors. There is no accent color. */
data class HalPalette(val bg: Color, val ink: Color, val muted: Color, val line: Color, val empty: Color)

val LightPalette = HalPalette(
    bg = Color(0xFFF3EFE6), ink = Color(0xFF1E1D1B), muted = Color(0xFF66615A),
    line = Color(0xFFB9B2A4), empty = Color(0xFFDDD6C8)
)
val DarkPalette = HalPalette(
    bg = Color(0xFF1B1A18), ink = Color(0xFFDCD6CA), muted = Color(0xFF9A948A),
    line = Color(0xFF4A4741), empty = Color(0xFF34322E)
)

/** The background color before Compose starts. It must be equal to [HalPalette.bg]. */
fun windowBackgroundArgb(isDark: Boolean): Int = (if (isDark) DarkPalette else LightPalette).bg.toArgb()

val LocalUiStyle = staticCompositionLocalOf { UiStyle.PAPER }
val LocalPalette = staticCompositionLocalOf { LightPalette }

// The fonts are in res/font (SIL Open Font License, see assets/licenses). They are variable fonts.
// Android 8 (API 26) and later use the weight axis. Android 7 shows the default weight (Regular).
// Before, the fonts came from the Google Fonts provider. On a test phone they did not load (Roboto showed).
private val FONT_WEIGHTS = listOf(
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold
)

@OptIn(ExperimentalTextApi::class)
private fun variableFonts(resId: Int, style: FontStyle): List<Font> = FONT_WEIGHTS.map { weight ->
    Font(
        resId = resId,
        weight = weight,
        style = style,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
    )
}

val ChivoMonoFontFamily = FontFamily(variableFonts(R.font.chivo_mono, FontStyle.Normal))

val LiterataFontFamily = FontFamily(
    variableFonts(R.font.literata, FontStyle.Normal) + variableFonts(R.font.literata_italic, FontStyle.Italic)
)

fun fontSizeMultiplier(scale: String): Float = when (scale) {
    "small" -> 0.85f
    "large" -> 1.15f
    else -> 1.0f
}

@Composable
fun HalTheme(
    isDarkMode: Boolean,
    uiStyle: UiStyle,
    fontSizeScale: String,
    content: @Composable () -> Unit
) {
    val p = if (isDarkMode) DarkPalette else LightPalette
    val multiplier = fontSizeMultiplier(fontSizeScale)
    val fontFamily = if (uiStyle == UiStyle.MONO) ChivoMonoFontFamily else LiterataFontFamily

    // Map the palette to the Material roles, so that the Material components use the palette.
    // ink = primary, tertiary and onBackground. muted = secondary. line = outline. empty = surfaceVariant.
    // The base scheme gives the error colors. All other roles come from the palette.
    val colorScheme = (if (isDarkMode) darkColorScheme() else lightColorScheme()).copy(
        primary = p.ink, onPrimary = p.bg, primaryContainer = p.empty, onPrimaryContainer = p.ink,
        secondary = p.muted, onSecondary = p.bg, secondaryContainer = p.empty, onSecondaryContainer = p.ink,
        tertiary = p.ink, onTertiary = p.bg, tertiaryContainer = p.empty, onTertiaryContainer = p.ink,
        background = p.bg, onBackground = p.ink,
        surface = p.bg, onSurface = p.ink, surfaceVariant = p.empty, onSurfaceVariant = p.ink,
        surfaceTint = p.ink, inverseSurface = p.ink, inverseOnSurface = p.bg, inversePrimary = p.bg,
        outline = p.line, outlineVariant = p.line,
        surfaceBright = p.bg, surfaceDim = p.bg,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.bg, surfaceContainer = p.bg,
        surfaceContainerHigh = p.bg, surfaceContainerHighest = p.empty
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        // The bars are transparent. Set the bar icons for the theme, also when the user changes the dark mode.
        SideEffect {
            (view.context as? ComponentActivity)?.enableHalEdgeToEdge(isDarkMode)
        }
    }

    // The text styles have no color. The text gets the content color of its container
    // (ink on bg, bg on an ink button). A color in the style hides the text on a filled button.
    fun style(size: Int, weight: FontWeight) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = (size * multiplier).sp
    )

    // Set all styles, so that no text uses the Material default font.
    val typography = Typography(
        displayLarge = style(57, FontWeight.Normal),
        displayMedium = style(45, FontWeight.Normal),
        displaySmall = style(36, FontWeight.Normal),
        headlineLarge = style(36, FontWeight.ExtraBold),
        headlineMedium = style(28, FontWeight.SemiBold),
        headlineSmall = style(24, FontWeight.SemiBold),
        titleLarge = style(22, FontWeight.SemiBold),
        titleMedium = style(18, FontWeight.SemiBold),
        titleSmall = style(14, FontWeight.Medium),
        bodyLarge = style(20, FontWeight.Normal),
        bodyMedium = style(16, FontWeight.Normal),
        bodySmall = style(14, FontWeight.Normal),
        labelLarge = style(16, FontWeight.Medium),
        labelMedium = style(13, FontWeight.Medium),
        labelSmall = style(11, FontWeight.Medium)
    )

    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        // Text outside a Surface (for example the home screen) gets ink from LocalContentColor.
        CompositionLocalProvider(
            LocalUiStyle provides uiStyle,
            LocalPalette provides p,
            LocalContentColor provides p.ink,
            content = content
        )
    }
}
