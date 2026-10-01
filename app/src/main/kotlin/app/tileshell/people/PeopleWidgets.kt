package app.tileshell.people

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.calculator.InkText
import app.tileshell.clock.ClockMetrics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * People's measured geometry (r11/people.md; G1 / G2, 10586, HIGH on two devices unless a line says otherwise). R11
 * measured from the screen top under a 24.0-epx status bar (P0.2); every vertical value here is that value less 24, so
 * it is the distance below the DRAWN status bar's bottom edge, whatever height phase 01 draws it at (C-17).
 */
object PeopleMetrics {
    /** P1.3 / P4.4 / P4.7: the page's side margin. */
    val SIDE = 12.dp

    /** P1.1: the pivot header — 15-epx semibold class with a cap of 11.0 epx, cap top 43.5 − 24. */
    const val PIVOT_CAP_TOP = 19.5f
    const val PIVOT_SP = 11.0f / CapMetrics.CAP_RATIO
    /** P1.1: the gap between two pivot words (≈ 24.8 epx). */
    val PIVOT_GAP = 24.8.dp
    /** P1.2: the unselected pivot, (156,156,156). */
    val PIVOT_DIM = Color(156, 156, 156)

    /** P1.3: the search box, 72 → 108 from the screen top; a 2-epx (133,133,133) border; "Search" at x 25, grey (142). */
    val SEARCH_TOP = 48.dp
    val SEARCH_H = 36.dp
    val SEARCH_BORDER = Color(133, 133, 133)
    val SEARCH_TEXT_INSET = 13.dp
    const val SEARCH_SP = 13.5f / CapMetrics.CAP_RATIO
    val SEARCH_HINT = Color(142, 142, 142)
    /** P1.4: the filter caption's top, ≈ 128.9 − 24 (MEDIUM, one device). */
    val CAPTION_TOP = 104.9.dp
    val CAPTION_H = 32.4.dp

    /** The list begins where the search box ends. */
    val LIST_TOP = SEARCH_TOP + SEARCH_H
    /** P1.5 / P1.6 with G1's first header: its cap top is 28.5 below the search box, the same 29 a header keeps from the avatar above it. */
    val LIST_PAD = 8.5.dp

    /** P1.10: the row pitch; P1.7: a 32-epx avatar at x 12; P1.9: the name at x 57.75, cap 12.75 epx (≈ 18-epx class). */
    val ROW = 50.dp
    val AVATAR = 32.dp
    val AVATAR_TOP = 9.dp
    val NAME_X = 57.75.dp
    const val NAME_SP = 12.75f / CapMetrics.CAP_RATIO
    /** P1.8 (MEDIUM): the disc behind the initial of a contact without a photo; R11 gives no value for the grey. */
    val AVATAR_DISC = Color(85, 85, 85)

    /**
     * P1.5 / P1.6: a letter header — the letter alone in accent, cap 22.25 epx, left 14.75. Its cap top is 29 below the
     * avatar above it and 43.5 above the first avatar of its group, so with avatars 9 epx into their 50-epx rows the
     * header is 54.5 epx tall with the cap top 20 epx into it ("a letter group costs 54.6 epx").
     */
    val LETTER_H = 54.5.dp
    const val LETTER_CAP_TOP = 20f
    const val LETTER_SP = 22.25f / CapMetrics.CAP_RATIO
    val LETTER_X = 14.75.dp

    /**
     * P2.2 – P2.4: the jump grid — 72-epx cells, as many columns as fit (4 at 360 epx), the block about 3 epx left of
     * centre (G1's first column centre 68.5 → a left edge of 32.5 at 360), the first row's cap top 143.5 − 24, letters
     * cap 14.4 epx, (52,52,52) where no contact files.
     */
    val GRID_CELL = 72.dp
    const val GRID_LEFT_OF_CENTRE = 3.5f
    const val GRID_FIRST_CAP_TOP = 119.5f
    const val GRID_SP = 14.4f / CapMetrics.CAP_RATIO
    val GRID_DIM = Color(52, 52, 52)
    /** P2.7 / P2.8 (the 14393 app list's motion, U8's approximation for People): the grid's open and close. */
    const val GRID_OPEN_MS = 280
    const val GRID_OPEN_SCALE = 1.08f

    /** P3.2: the card's name — caps, semibold, 15-epx class, cap top 50 − 24, left 13.25. */
    const val CARD_NAME_CAP_TOP = 26f
    val CARD_NAME_X = 13.25.dp
    /** P3.3: "Profile", 24-epx class, ascender top 80 − 24, left 14.3. */
    val CARD_PROFILE_TOP = 56.dp
    val CARD_PROFILE_X = 14.3.dp
    /** P3.4: the photo, a 124-epx circle at x 12, top 120 − 24. */
    val CARD_PHOTO = 124.dp
    val CARD_PHOTO_TOP = 96.dp
    /** P3.5 (MEDIUM): the account caption under the photo, ≈ 274 − 24. */
    val CARD_ACCOUNT_TOP = 250.dp
    /** P3.6 / P3.7: action rows — the first label's cap top 310.75 − 24, left 13; one line 48 epx, two lines 65.5. */
    const val CARD_FIRST_ACTION_CAP_TOP = 286.75f
    const val ACTION_CAP_TOP = 14f
    val ACTION_X = 13.dp
    val ACTION_ONE_LINE = 48.dp
    val ACTION_TWO_LINES = 65.5.dp
    const val ACTION_TITLE_SP = 13.5f / CapMetrics.CAP_RATIO
    const val ACTION_DETAIL_SP = 9.3f / CapMetrics.CAP_RATIO
    /** P3.6: the second line's cap top 22.6 below the label's (381.5 − 358.75, 446.75 − 424.25). */
    const val ACTION_DETAIL_DROP = 22.6f

    /** P4.1: the editor's header — caps, cap 11.0, cap top 45.5 − 24, left 13. */
    const val EDITOR_HEADER_CAP_TOP = 21.5f
    val EDITOR_HEADER_X = 13.dp
    /** P4.2 (MEDIUM): the editor's photo, the 124-epx circle at x 12, top 84 − 24. */
    val EDITOR_PHOTO_TOP = 60.dp
    /** P4.5: label cap top → box top. */
    const val LABEL_TO_BOX = 22.75f
    /** P4.6: a "+ field" row, a 16-epx "+" and a 15-epx label at x 12, 44-epx pitch. */
    val ADD_ROW = 44.dp
    /** P4.7: 1-epx (103,103,103) rules, x 12 → W − 12, 22–23 epx clear above and below. */
    val RULE = Color(103, 103, 103)
    const val RULE_CLEAR = 22.25f

    /** R7 §3.6.2 (ClockMetrics): the notice band's fill, the flyout's. */
    val NOTICE_FILL = ClockMetrics.FLYOUT_FILL
}

/** What a page has to say. It stays until the page changes — a node, never a toast. */
data class PeopleNotice(val text: String, val action: NoticeAction? = null)

/** What a notice offers in place. */
enum class NoticeAction(val label: String) {
    GRANT_CONTACTS("allow access"),
    GRANT_CALLS("allow calls"),
    CAN_EDIT("open Can edit"),
}

/**
 * Every People page's frame (phase 01's bar rule): the drawn status bar, the page with its app bar and its notice
 * line, and the drawn nav bar. [tag] is the page's own tag, `selected` while it shows (`people_page:<name>`).
 */
@Composable
fun PeopleScaffold(
    tag: String,
    host: PeopleHost,
    notice: PeopleNotice?,
    onNoticeAction: (NoticeAction) -> Unit,
    pageFill: Color? = null,
    barHeight: Dp = ClockMetrics.APP_BAR,
    bar: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalShellColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        W10mStatusBar()
        Box(
            Modifier.fillMaxWidth().weight(1f).background(pageFill ?: colors.background)
                // The page on show reads selected="true" in a dump: Android reports a selected node that way only for a tab.
                .testTag(tag).semantics { role = Role.Tab; selected = true },
        ) {
            content()
            if (notice != null) NoticeBand(notice, if (bar != null) barHeight else 0.dp, onNoticeAction)
            bar?.invoke(this)
        }
        W10mNavBar(onBack = host.onBack, onWindows = host.onWindows)
    }
}

/** The page's notice line (`people_notice`), above the app bar, with what it offers (`people_notice_action`). */
@Composable
private fun BoxScope.NoticeBand(notice: PeopleNotice, bottom: Dp, onAction: (NoticeAction) -> Unit) {
    val colors = LocalShellColors.current
    Column(
        Modifier.align(Alignment.BottomStart).offset(y = -bottom).fillMaxWidth().background(PeopleMetrics.NOTICE_FILL)
            .padding(horizontal = PeopleMetrics.SIDE, vertical = 10.dp),
    ) {
        BasicText(notice.text, Modifier.testTag("people_notice"), style = ShellType.body.copy(color = Color.White))
        notice.action?.let { action ->
            BasicText(
                action.label,
                Modifier.padding(top = 6.dp).testTag("people_notice_action")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAction(action) },
                style = ShellType.body.copy(color = colors.accent),
            )
        }
    }
}

/**
 * A line of text placed by its INK, as r11 measured it: the first glyph's ink edge [inkLeft] epx from the parent's left,
 * a capital's top [capTop] epx below the parent's top (the Calculator's and the Clock's lesson — a text BOX placed at
 * an ink value sits a side bearing off, qa/phase-15/E10-run7/DEFECT.md).
 */
@Composable
fun InkLine(text: String, style: TextStyle, inkLeft: Float, capTop: Float, modifier: Modifier = Modifier) {
    InkText(text, style, reference = "H", modifier = modifier, inkLeftEpx = inkLeft, inkTopEpx = capTop)
}

/** A page's caps header in the pivot / editor class (P1.1, P4.1): semibold, cap 11.0, its cap top [capTop] below the page top. */
@Composable
fun CapsHeader(text: String, capTop: Float, inkLeft: Float, tag: String, color: Color? = null) {
    val colors = LocalShellColors.current
    InkLine(text.uppercase(), ShellType.base.copy(fontSize = PeopleMetrics.PIVOT_SP.sp, color = color ?: colors.text), inkLeft, capTop, Modifier.testTag(tag))
}

private val avatarCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** Dropped when the provider changes: a photo may have been replaced under the same thumbnail URI. */
fun clearAvatarCache() = avatarCache.evictAll()

/**
 * A contact's circular avatar (P1.7): the provider's thumbnail cropped to the circle, or a grey disc with the initial
 * (P1.8) while there is no photo.
 */
@Composable
fun ContactAvatar(name: String, thumbUri: String?, size: Dp, modifier: Modifier = Modifier, discColor: Color = PeopleMetrics.AVATAR_DISC) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = thumbUri?.let { avatarCache.get(it) }, thumbUri) {
        if (thumbUri != null && value == null) {
            value = withContext(Dispatchers.IO) { PeopleData.thumbnail(context, thumbUri) }?.also { avatarCache.put(thumbUri, it) }
        }
    }
    AvatarCircle(name, bitmap, size, modifier, discColor)
}

/** The circle itself, for a caller that already holds the picture (the card and the editor decode the full photo). */
@Composable
fun AvatarCircle(name: String, bitmap: Bitmap?, size: Dp, modifier: Modifier = Modifier, discColor: Color = PeopleMetrics.AVATAR_DISC) {
    Box(modifier.size(size).clip(CircleShape).background(discColor), contentAlignment = Alignment.Center) {
        val picture = bitmap
        if (picture != null) {
            Image(picture.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BasicText(
                PeopleBuckets.initial(name),
                style = ShellType.body.copy(fontSize = (size.value * 0.47f).sp, lineHeight = (size.value * 0.6f).sp, color = Color.White, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/**
 * One row of a contact list (P1.7 – P1.10): 50 epx, the 32-epx avatar at x 12, the name at x 57.75 on the avatar's
 * centre line, lightening while pressed (X19). [tag] is the row's (`people_row:<lookup>`); the name's node carries
 * `people_name:<lookup>`. [trailing] draws at the row's right edge (the briefcase of a work-profile match, a tick).
 */
@Composable
fun ContactRowView(row: ContactRow, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable BoxScope.() -> Unit)? = null) {
    val colors = LocalShellColors.current
    PressRow(onClick, modifier.fillMaxWidth().height(PeopleMetrics.ROW).testTag(tag)) {
        ContactAvatar(row.name, row.photoThumb, PeopleMetrics.AVATAR, Modifier.offset(x = PeopleMetrics.SIDE, y = PeopleMetrics.AVATAR_TOP).testTag("people_avatar:${row.lookup}"))
        // P1.9: the name's ink at x 57.75; its cap centre a little under the avatar's centre line (G2 "Batman": 279.4 against 278.3).
        val capTop = PeopleMetrics.AVATAR_TOP.value + PeopleMetrics.AVATAR.value / 2f + 1.1f - CapMetrics.capHeight(PeopleMetrics.NAME_SP) / 2f
        InkLine(
            row.name,
            ShellType.body.copy(fontSize = PeopleMetrics.NAME_SP.sp, lineHeight = 24.sp, color = colors.text),
            PeopleMetrics.NAME_X.value, capTop, Modifier.testTag("people_name:${row.lookup}"),
        )
        trailing?.invoke(this)
    }
}

/** P1.3: the search box — 36 epx, x 12 → W − 12, a 2-epx (133,133,133) border on black, "Search" in grey at x 25. */
@Composable
fun SearchBox(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = ShellType.body.copy(fontSize = PeopleMetrics.SEARCH_SP.sp, lineHeight = 24.sp, color = Color.White)
    Box(
        modifier.padding(horizontal = PeopleMetrics.SIDE).fillMaxWidth().height(PeopleMetrics.SEARCH_H)
            .background(Color.Black).border(2.dp, PeopleMetrics.SEARCH_BORDER).testTag("people_search_box"),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            // "Search" with its ink 13 epx inside the box's edge (x 25.0), its cap centred on the box.
            Box(Modifier.fillMaxSize()) {
                val capTop = PeopleMetrics.SEARCH_H.value / 2f - CapMetrics.capHeight(PeopleMetrics.SEARCH_SP) / 2f
                InkLine("Search", style.copy(color = PeopleMetrics.SEARCH_HINT), PeopleMetrics.SEARCH_TEXT_INSET.value, capTop, Modifier.testTag("people_search_hint"))
            }
        }
        BasicTextField(
            value = query,
            onValueChange = { onQuery(it.replace("\n", "").take(100)) },
            modifier = Modifier.fillMaxSize().padding(horizontal = PeopleMetrics.SEARCH_TEXT_INSET).testTag("people_search"),
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
        )
    }
}

/** A glyph of the shell's icon font. */
@Composable
fun PeopleGlyph(glyph: String, size: Float, color: Color, modifier: Modifier = Modifier) {
    BasicText(glyph, modifier, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = size.sp, lineHeight = size.sp, color = color, fontWeight = FontWeight.Normal))
}

/** The briefcase a work-profile match carries (phase 01 Q5's glyph, extended to People; H10). */
@Composable
fun BoxScope.BriefcaseMark(tag: String) {
    PeopleGlyph(Glyph.BRIEFCASE, 16f, LocalShellColors.current.text, Modifier.align(Alignment.CenterEnd).padding(end = PeopleMetrics.SIDE).testTag(tag))
}

/** P4.7: a 1-epx rule between field groups, x 12 → W − 12. */
@Composable
fun GroupRule(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = PeopleMetrics.SIDE).fillMaxWidth().height(1.dp).background(PeopleMetrics.RULE).testTag("people_rule"))
}
