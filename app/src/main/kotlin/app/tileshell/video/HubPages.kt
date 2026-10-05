package app.tileshell.video

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.tileshell.R
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.net.CredentialStore
import app.tileshell.settings.TwoLineItem
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Movies & TV's About page: what the catalogue's terms ask the app to say (BS-5). */
data object AboutSub : HubSub {
    override val title = "About"
}

/** The words TMDB's terms require, as written there (BS-5). */
object Attribution {
    const val TMDB = "This product uses the TMDB API but is not endorsed or certified by TMDB."
    const val JUSTWATCH = "Where to watch: data from JustWatch."
}

object HubPages {
    /** The pane's top group, in order (the Media server row is drawn only while a server is set up). */
    val PANE_TOP: List<HubPage> = listOf(HubPage.MY_VIDEOS, HubPage.BROWSE, HubPage.MEDIA_SERVER)
}

/** The page under the header: a pane page, or the page opened from one. */
@Composable
fun HubPages(nav: VideoNav, activity: ComponentActivity, browse: BrowseModel) {
    if (!nav.ready) return
    when (val sub = nav.sub) {
        null -> when (nav.page) {
            HubPage.SETTINGS -> HubSettingsPage(nav)
            HubPage.BROWSE -> BrowsePage(nav, browse)
            HubPage.MEDIA_SERVER -> MediaServerPage(nav, activity)
            HubPage.MY_VIDEOS -> MyVideosPage(nav, activity)
        }
        is ServerSub -> ServerSettingsPage(nav)
        is AboutSub -> AboutPage()
        is TmdbKeySub -> TmdbKeyPage()
        is TitleSub -> TitlePage(nav, browse, sub)
    }
}

/** Movies & TV's settings (the pane's bottom group): R3 C1's two-line rows. No row ever shows a saved credential. */
@Composable
fun HubSettingsPage(nav: VideoNav) {
    val context = LocalContext.current
    val keySaved by produceState<Boolean?>(null, nav.resumes) { value = withContext(Dispatchers.IO) { CredentialStore.of(context).has(CredentialStore.TMDB) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("hub_page:settings")) {
        TwoLineItem(
            Glyph.KEY, "TMDB key", when (keySaved) { true -> "A key is saved"; false -> "Not set — film search needs one"; null -> "" }, "hub_settings:tmdbkey",
        ) { nav.openSub(TmdbKeySub) }
        TwoLineItem(Glyph.SERVER, "Media server", if (nav.serverSetUp) "Set up — tap to see or remove it" else "Add a server", "hub_settings:server") { nav.openSub(ServerSub) }
        TwoLineItem(Glyph.INFO, "About", "${Brand.VIDEO_APP_NAME}, and where its film data comes from", "hub_settings:about") { nav.openSub(AboutSub) }
    }
}

/**
 * TMDB's attribution with its logo, and JustWatch's credit (BS-5). The logo is TMDB's own "blue short" file from
 * https://www.themoviedb.org/about/logos-attribution, drawn unmodified and smaller than the app's name.
 */
@Composable
fun AboutPage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 16.dp).testTag("hub_page:about")) {
        BasicText(Brand.VIDEO_APP_NAME, Modifier.testTag("hub_about_name"), style = ShellType.title.copy(color = Color.White))
        Image(painterResource(R.drawable.tmdb_logo_blue_short), contentDescription = "TMDB", modifier = Modifier.padding(top = 24.dp).width(120.dp).testTag("hub_about_logo"))
        BasicText(Attribution.TMDB, Modifier.padding(top = 12.dp).testTag("hub_about_tmdb"), style = ShellType.body.copy(color = Color.White))
        BasicText(Attribution.JUSTWATCH, Modifier.padding(top = 12.dp).testTag("hub_about_justwatch"), style = ShellType.body.copy(color = Color.White))
    }
}
