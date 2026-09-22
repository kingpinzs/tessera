package app.tileshell.cortana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.cortana.Card
import app.tileshell.cortana.CardAction
import app.tileshell.cortana.CardKind
import app.tileshell.cortana.PersonaState
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType

/**
 * A Cortana response card (R6 §3.4, R7 §3.8.1) — phase 03 build task 5's visible half.
 *
 * Measured: the reminder confirm card's geometry (R6 §3.4.2, MEDIUM, sub-pixel edges) and the
 * saved-reminder card's (R7 §3.8.1, MEDIUM). The text read-back's wording is MEDIUM and its layout is
 * a LOW candidate (H5). The call, calendar, delete, unlock and role-notice cards are approximations
 * patterned on those two, each with its own H row.
 */
object CardValues {
    /** R6 §3.4.2: the accent title's left edge is 16 epx from the screen's left. */
    const val TITLE_LEFT_EPX = 16f
    const val TITLE_SIZE_EPX = 20f

    /** R7 §3.8.1: the saved card's title is the 24-epx class with its cap top 94.6 epx from the top. */
    const val SAVED_TITLE_SIZE_EPX = 24f
    const val SAVED_TITLE_CAP_TOP_EPX = 94.6f
    const val SAVED_CAPTION_CAP_TOP_EPX = 133.9f
    const val SAVED_ROW_CAP_TOP_EPX = 169.2f
    const val SAVED_ROW_TEXT_LEFT_EPX = 62.9f
    const val SAVED_SUBLINE_CAP_TOP_EPX = 198.0f
    val SAVED_CAPTION_COLOUR = Color(0xFF8C918B)
    val SAVED_SUBLINE_COLOUR = Color(0xFF969B97)

    /** R6 §3.4.2: outlined fields 43 epx tall at a 53.6-epx pitch, field text cap 16 epx. */
    const val FIELD_HEIGHT_EPX = 43f
    const val FIELD_PITCH_EPX = 53.6f
    const val FIELD_TEXT_SIZE_EPX = 23f

    /** R6 §3.4.2: the recurrence dropdown is 32 epx tall. */
    const val COMBO_HEIGHT_EPX = 32f

    /** R6 §3.4.2: accent buttons 32.2 epx tall, 164.6 and 160.0 epx wide, 3.8 epx apart, 16.3 / 15.7 in. */
    const val BUTTON_HEIGHT_EPX = 32.2f
    const val BUTTON_LEFT_WIDTH_EPX = 164.6f
    const val BUTTON_RIGHT_WIDTH_EPX = 160.0f
    const val BUTTON_GAP_EPX = 3.8f
    const val BUTTON_LEFT_MARGIN_EPX = 16.3f
    const val BUTTON_RIGHT_MARGIN_EPX = 15.7f

    /** The small persona at the top of a card (R6 §3.2.2: centre 40 epx from the screen top). */
    const val PERSONA_CENTRE_Y_EPX = 40f

    val FIELD_BORDER = Color(0xFF374B77)
    val FIELD_FILL = Color(0xFF202520)
    const val CAPTION_SIZE_EPX = 12f
}

@Composable
fun ResponseCardView(
    card: Card,
    persona: PersonaState,
    level: Float,
    accent: Color,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("cortana_card:${card.kind.name.lowercase()}")) {
        // R6 §3.2.2: the small persona's centre is 40 epx from the screen top, horizontally centred.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Spacer(Modifier.height((CardValues.PERSONA_CENTRE_Y_EPX - PersonaValues.AWAIT_HALO_MAX_EPX / 2f).dp))
            SmallPersona(persona, level, accent)
        }

        if (card.kind == CardKind.REMINDER_SAVED) {
            SavedReminderBody(card, accent)
            return@Column
        }

        Spacer(
            Modifier.height(
                CapMetrics.topPaddingForCapTop(
                    94.6f - CardValues.PERSONA_CENTRE_Y_EPX - PersonaValues.AWAIT_HALO_MAX_EPX / 2f,
                    CardValues.TITLE_SIZE_EPX,
                    CapMetrics.lineHeightFor(CardValues.TITLE_SIZE_EPX),
                ).dp
            )
        )
        BasicText(
            card.title,
            style = ShellType.subtitle.copy(color = accent),
            maxLines = 2,
            modifier = Modifier.padding(start = CardValues.TITLE_LEFT_EPX.dp).testTag("cortana_card_title"),
        )

        card.caption?.let {
            Spacer(Modifier.height(8.dp))
            BasicText(
                it,
                style = ShellType.caption.copy(color = CardValues.SAVED_CAPTION_COLOUR),
                modifier = Modifier.padding(start = CardValues.TITLE_LEFT_EPX.dp).testTag("cortana_card_caption"),
            )
        }

        card.contact?.let { chip ->
            Spacer(Modifier.height(10.dp))
            BasicText(
                "To",
                style = ShellType.caption.copy(color = CardValues.SAVED_CAPTION_COLOUR),
                modifier = Modifier.padding(start = CardValues.TITLE_LEFT_EPX.dp),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .padding(horizontal = CardValues.TITLE_LEFT_EPX.dp)
                    .fillMaxWidth()
                    .height(CardValues.FIELD_HEIGHT_EPX.dp)
                    .border(1.dp, CardValues.FIELD_BORDER)
                    .background(CardValues.FIELD_FILL)
                    .padding(horizontal = 8.dp)
                    .testTag("cortana_card_contact"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // R6 §3.4.1's round avatar. Contact photos are phase 06's People work; here it is the
                // person glyph on an accent disc (approximation inside H5's card layout).
                Box(Modifier.size(28.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    BasicText(Glyph.PERSON, style = iconStyle(Color.White, 16))
                }
                Spacer(Modifier.width(8.dp))
                BasicText(chip.name, style = ShellType.body.copy(color = Color.White))
                chip.numberLabel?.let {
                    Spacer(Modifier.width(6.dp))
                    BasicText(it, style = ShellType.caption.copy(color = CardValues.SAVED_SUBLINE_COLOUR))
                }
            }
        }

        card.message?.let { message ->
            Spacer(Modifier.height((CardValues.FIELD_PITCH_EPX - CardValues.FIELD_HEIGHT_EPX).dp))
            CardOutlinedField("cortana_card_message", message, "Enter your message.")
        }
        if (card.kind == CardKind.TEXT_READBACK && card.message == null) {
            Spacer(Modifier.height((CardValues.FIELD_PITCH_EPX - CardValues.FIELD_HEIGHT_EPX).dp))
            CardOutlinedField("cortana_card_message", "", "Enter your message.")
        }

        card.fields.forEach { field ->
            Spacer(Modifier.height((CardValues.FIELD_PITCH_EPX - CardValues.FIELD_HEIGHT_EPX).dp))
            CardOutlinedField("cortana_card_field:${field.tag}", field.value, field.placeholder)
        }

        card.recurrence?.let { recurrence ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .padding(horizontal = CardValues.TITLE_LEFT_EPX.dp)
                    .height(CardValues.COMBO_HEIGHT_EPX.dp)
                    .border(1.dp, CardValues.FIELD_BORDER)
                    .background(CardValues.FIELD_FILL)
                    .padding(horizontal = 8.dp)
                    .testTag("cortana_card_recurrence"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(recurrence.label, style = ShellType.body.copy(color = Color.White))
                Spacer(Modifier.width(6.dp))
                BasicText(Glyph.CHEVRON_DOWN, style = iconStyle(Color.White, 12))
            }
        }

        if (card.photoRow) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        onAction(CardAction.PICK_PHOTO)
                    }
                    .padding(start = CardValues.TITLE_LEFT_EPX.dp)
                    .testTag("cortana_card_add_photo"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(Glyph.CAMERA, style = iconStyle(Color.White, 20))
                Spacer(Modifier.width(10.dp))
                BasicText("Add a photo", style = ShellType.body.copy(color = Color.White))
            }
        }

        card.body.forEach { line ->
            Spacer(Modifier.height(6.dp))
            BasicText(
                line,
                style = ShellType.body.copy(color = Color.White),
                modifier = Modifier.padding(horizontal = CardValues.TITLE_LEFT_EPX.dp).testTag("cortana_card_body"),
            )
        }

        card.callout?.let {
            Spacer(Modifier.height(10.dp))
            BasicText(
                it,
                style = ShellType.caption.copy(color = CardValues.SAVED_SUBLINE_COLOUR),
                modifier = Modifier.padding(start = CardValues.TITLE_LEFT_EPX.dp).testTag("cortana_card_callout"),
            )
        }

        if (card.buttons.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            CardButtons(card, accent, onAction)
        }
    }
}

@Composable
private fun CardOutlinedField(tag: String, value: String, placeholder: String) {
    Box(
        Modifier
            .padding(horizontal = CardValues.TITLE_LEFT_EPX.dp)
            .fillMaxWidth()
            .height(CardValues.FIELD_HEIGHT_EPX.dp)
            .border(1.dp, CardValues.FIELD_BORDER)
            .background(CardValues.FIELD_FILL)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isBlank()) {
            BasicText(placeholder, style = ShellType.body.copy(color = CardValues.SAVED_SUBLINE_COLOUR))
        } else {
            BasicText(value, style = ShellType.body.copy(color = Color.White))
        }
    }
}

/** R6 §3.4.2: two accent buttons side by side, at the measured widths and margins. */
@Composable
private fun CardButtons(card: Card, accent: Color, onAction: (CardAction) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = CardValues.BUTTON_LEFT_MARGIN_EPX.dp, end = CardValues.BUTTON_RIGHT_MARGIN_EPX.dp)
            .height(CardValues.BUTTON_HEIGHT_EPX.dp),
        horizontalArrangement = Arrangement.spacedBy(CardValues.BUTTON_GAP_EPX.dp),
    ) {
        card.buttons.forEachIndexed { index, button ->
            val width = if (index == 0) CardValues.BUTTON_LEFT_WIDTH_EPX else CardValues.BUTTON_RIGHT_WIDTH_EPX
            Box(
                Modifier
                    .then(if (card.buttons.size == 1) Modifier.weight(1f) else Modifier.width(width.dp))
                    .height(CardValues.BUTTON_HEIGHT_EPX.dp)
                    .background(if (button.enabled) accent else accent.copy(alpha = 0.35f))
                    .clickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                        enabled = button.enabled,
                    ) { onAction(button.action) }
                    .testTag("cortana_card_button:${button.action.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    button.label,
                    style = ShellType.body.copy(color = if (button.enabled) Color.White else Color(0xB3FFFFFF)),
                )
            }
        }
    }
}

/**
 * R7 §3.8.1 (MEDIUM, 14393): the saved-reminder card. The lightbulb shows for EVERY saved reminder,
 * timed ones included, while the Reminders list keeps the clock on timed rows — that is what §3.5.6 saw
 * and the two are deliberately different.
 */
@Composable
private fun SavedReminderBody(card: Card, accent: Color) {
    val titleLine = CapMetrics.lineHeightFor(CardValues.SAVED_TITLE_SIZE_EPX)
    val personaBottom = CardValues.PERSONA_CENTRE_Y_EPX + PersonaValues.AWAIT_HALO_MAX_EPX / 2f
    Spacer(
        Modifier.height(
            CapMetrics.topPaddingForCapTop(
                CardValues.SAVED_TITLE_CAP_TOP_EPX - personaBottom,
                CardValues.SAVED_TITLE_SIZE_EPX, titleLine,
            ).dp
        )
    )
    BasicText(
        card.title,
        style = ShellType.title.copy(color = accent),
        modifier = Modifier.padding(start = CardValues.TITLE_LEFT_EPX.dp).testTag("cortana_card_title"),
    )
    Spacer(
        Modifier.height(
            (CardValues.SAVED_CAPTION_CAP_TOP_EPX - CardValues.SAVED_TITLE_CAP_TOP_EPX -
                CapMetrics.capHeight(CardValues.SAVED_TITLE_SIZE_EPX)).dp
        )
    )
    BasicText(
        card.caption.orEmpty(),
        style = ShellType.caption.copy(color = CardValues.SAVED_CAPTION_COLOUR),
        modifier = Modifier.padding(start = CardValues.TITLE_LEFT_EPX.dp).testTag("cortana_card_caption"),
    )
    Spacer(
        Modifier.height(
            (CardValues.SAVED_ROW_CAP_TOP_EPX - CardValues.SAVED_CAPTION_CAP_TOP_EPX -
                CapMetrics.capHeight(CardValues.CAPTION_SIZE_EPX)).dp
        )
    )
    Row(Modifier.fillMaxWidth().testTag("cortana_card_saved_row"), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.width(CardValues.SAVED_ROW_TEXT_LEFT_EPX.dp).padding(start = 15.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            BasicText(Glyph.LIGHTBULB, style = iconStyle(Color.White, 24))
        }
        BasicText(
            card.savedText.orEmpty(),
            style = ShellType.subtitle.copy(color = Color.White),
            modifier = Modifier.testTag("cortana_card_saved_text"),
        )
    }
    card.savedSubline?.let { subline ->
        Spacer(
            Modifier.height(
                (CardValues.SAVED_SUBLINE_CAP_TOP_EPX - CardValues.SAVED_ROW_CAP_TOP_EPX -
                    CapMetrics.capHeight(20f)).dp
            )
        )
        BasicText(
            subline,
            style = ShellType.caption.copy(color = CardValues.SAVED_SUBLINE_COLOUR),
            modifier = Modifier
                .padding(start = CardValues.SAVED_ROW_TEXT_LEFT_EPX.dp)
                .testTag("cortana_card_saved_subline"),
        )
    }
}
