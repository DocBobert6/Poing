package app.poing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One RetroAchievements badge (hub + badges, 2026-11-14): the
 * cached image when there is one, else a dark placeholder box
 * that keeps the row aligned. The fetch runs on IO when the row
 * first shows the badge, behind the same network gate as cover
 * art (unmetered Wi-Fi, or mobile data when the cover-art
 * mobile-data toggle is on) — see [isCoverArtNetworkAvailable].
 * A landed badge redraws only its own row: the bitmap arrives
 * through the store's state map, which the row reads.
 */
@Composable
fun RaBadgeImage(
    store: RaBadgeStore,
    url: String?,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = url?.let { store.badgeBitmaps[it] }
    LaunchedEffect(url) {
        if (url.isNullOrEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            // The setting is read fresh on every fetch, so a
            // toggle change takes effect on the next entry.
            val allowMobileData =
                AppSettingsStore(context.filesDir).coverArtMobileDataAllowed()
            val networkAvailable = isCoverArtNetworkAvailable(context, allowMobileData)
            store.badgeOrFetch(url, networkAvailable)?.let { store.decodeIntoCache(url) }
        }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A)),
        )
    }
}

/** The badge store for the files dir, remembered per screen — the same pattern as the cover-art store. */
@Composable
fun rememberRaBadgeStore(): RaBadgeStore {
    val context = LocalContext.current
    return remember { RaBadgeStore(context.filesDir) }
}
