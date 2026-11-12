package app.poing

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Poing! Phase 3 — the library is the home screen. The Phase 2
 * "Play a game" picker flow is gone as the entry point (import now
 * lives in the library); the Licences screen stays, and the player
 * is reached by tapping a game. The core is only ever RUN on game
 * start — but since Phase 6 (item 8) the native libraries are
 * LOADED at launch, so the RetroAchievements client can re-log in
 * from its stored token before any game starts (see onCreate).
 * A failed load at launch (no .so under the Robolectric startup
 * gate) just means RA is unavailable — the app launches without
 * it, and the startup gate still guards the launch path.
 *
 * The content sits in [PoingTheme], which paints an explicit dark
 * scheme on a root Surface. A bare MaterialTheme (default light
 * scheme) on the black window background made all plain text
 * invisible on Robert's phone (black-on-black). Never go back to a
 * bare MaterialTheme here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 6 (item 8) — RetroAchievements: initialise the
        // client at launch, so a stored login re-logs in before
        // any game starts. This loads the native libraries (the
        // same .so files the player loads); when there is no .so
        // to load — the Robolectric startup gate — init fails
        // gracefully and the app launches without RA.
        RaClient.init(RaSessionStore(filesDir))
        setContent {
            PoingTheme {
                PoingHome()
            }
        }
    }

    // Phase 4 — gamepad input. The pad only ever drives the game:
    // GamepadInput consumes mapped buttons/stick moves only while
    // the player screen has enabled it, so keys on the library
    // screen (and Back everywhere) still behave normally.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GamepadInput.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (GamepadInput.handleMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
}

private sealed interface Screen {
    data object Library : Screen
    data object Licences : Screen
    data object Settings : Screen
    data object CoreOptions : Screen
    // Phase 6 (item 8c) — the RetroAchievements login screen,
    // reached from Settings.
    data object RaLogin : Screen
    data class Player(val game: LibraryGame, val loadState: Int? = null, val loadAuto: Boolean = false) : Screen
    data class SaveManager(val game: LibraryGame) : Screen
}

@Composable
fun PoingHome() {
    // Phase 5 — the dot-matrix boot: a brief "Poing!" on a dark
    // screen at launch, auto-dismissed after BOOT_SPLASH_MS. All
    // state is remembered before the early return so the remember
    // calls run unconditionally on every composition.
    var booted by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val store = remember { LibraryStore(context.filesDir) }
    val optionsStore = remember { CoreOptionsStore(context.filesDir) }
    val settingsStore = remember { AppSettingsStore(context.filesDir) }
    LaunchedEffect(Unit) {
        delay(BOOT_SPLASH_MS)
        booted = true
    }
    if (!booted) {
        BootSplash()
        return
    }

    when (val s = screen) {
        is Screen.Player -> GameScreen(
            gamePath = store.gameFile(s.game).absolutePath,
            gameName = s.game.title,
            isGba = s.game.system == GameSystem.GBA,
            // Phase 8 — the header-detected system (GB vs GBC
            // matters for the display chain).
            system = s.game.system,
            optionsStore = optionsStore,
            appSettings = settingsStore,
            loadStateOnStart = s.loadState,
            loadAutoOnStart = s.loadAuto,
            onQuit = { screen = Screen.Library },
            onError = { message ->
                error = message
                screen = Screen.Library
            },
        )
        is Screen.SaveManager -> SaveManagerScreen(
            game = s.game,
            onLoadState = { slot ->
                error = null
                screen = Screen.Player(s.game, loadState = slot)
            },
            onLoadAuto = {
                error = null
                screen = Screen.Player(s.game, loadAuto = true)
            },
            onBack = { screen = Screen.Library },
        )
        is Screen.Licences -> LicencesScreen(onBack = { screen = Screen.Library })
        is Screen.Settings -> AppSettingsScreen(
            store = settingsStore,
            // Phase 8 — the Jade palette picker lives in the
            // Display section and writes the core's palette
            // option, so the screen needs the core options
            // store too.
            optionsStore = optionsStore,
            onCoreOptions = { screen = Screen.CoreOptions },
            onRaLogin = { screen = Screen.RaLogin },
            onBack = { screen = Screen.Library },
        )
        is Screen.RaLogin -> RaLoginScreen(
            session = remember { RaSessionStore(context.filesDir) },
            onBack = { screen = Screen.Settings },
        )
        is Screen.CoreOptions -> CoreOptionsScreen(
            store = optionsStore,
            onBack = { screen = Screen.Settings },
        )
        is Screen.Library -> LibraryScreen(
            notice = error,
            onPlay = { game ->
                error = null
                screen = Screen.Player(game)
            },
            onLicences = { screen = Screen.Licences },
            onSettings = { screen = Screen.Settings },
            onSaveManager = { game -> screen = Screen.SaveManager(game) },
        )
    }
}
