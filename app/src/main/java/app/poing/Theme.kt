package app.poing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Poing! theme — explicit dark scheme, app-wide.
 *
 * THE BUG THIS EXISTS FOR (Phase 1 device check, Robert 2026-11-11):
 * `themes.xml` sets `android:windowBackground` to black, and
 * MainActivity used a bare `MaterialTheme` — which defaults to the
 * LIGHT scheme, so every `Text` defaulted to near-black
 * `onBackground` — with no `Surface` of its own painting a
 * background. Result: black text on a black background. The home
 * screen showed only the Licences button and the Licences screen
 * only the Back button; all plain text was invisible (buttons
 * still showed because a Button draws its own purple container
 * with white text). The 17 KB notice text was loading fine — it
 * was purely a colour/background bug. Same bug class as MainMenu
 * 2026-11-07.
 *
 * The fix: an explicit dark scheme (background black, to match
 * the window background; text white) wrapped in a root `Surface`
 * that paints `scheme.background`, so the text colour and the
 * background it sits on can never drift apart again.
 *
 * PHASE 5 PALETTE — sampled from the supplied icon
 * (icon/poing-icon.jpg: silver chrome tile, deep purple octagonal
 * face, gold trim). The purple replaces the plain Material purple
 * (#6750A4) the buttons used before; gold is a thin accent only,
 * never a second primary; silver-grey is the chrome.
 *
 * GUARDRAIL (pinned by ThemeContrastTest): the purple is for
 * containers with white text, NEVER for text on black — purple on
 * black is 4.03:1, below the 4.5 AA floor. Gold and silver ARE
 * readable on black (8.68 and 12.73), so gold rules and silver
 * secondary text are fine.
 */
val PoingPurple = Color(0xFF7E57C2) // icon's deep purple — primary
val PoingPurpleDeep = Color(0xFF5E35B1) // Atomic panel base
val PoingSilver = Color(0xFFC9C9CE) // chrome text on black
val PoingChrome = Color(0xFF9A9AA0) // secondary chrome
val PoingGold = Color(0xFFC9A227) // gold accent only
val PoingPowerGreen = Color(0xFF66BB6A) // power light: playing
val PoingPowerAmber = Color(0xFFFFB300) // power light: paused

val PoingDarkColors = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = PoingSilver,
    primary = PoingPurple,
    onPrimary = Color.White,
    primaryContainer = PoingPurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = PoingSilver,
    onSecondary = Color.Black,
    tertiary = PoingGold,
    onTertiary = Color.Black,
)

@Composable
fun PoingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PoingDarkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}

/**
 * WCAG relative-luminance contrast ratio between two colours.
 * 1.0 = identical, 21.0 = black vs white. Plain body text needs
 * >= 4.5 (AA); this theme targets >= 7 (AAA) for its text pairs.
 * Used by [ThemeContrastTest], the regression guard for the
 * black-on-black bug above.
 */
fun contrastRatio(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble()
    val l2 = b.luminance().toDouble()
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}
