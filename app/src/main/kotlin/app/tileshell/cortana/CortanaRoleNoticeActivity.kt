package app.tileshell.cortana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.tileshell.bars.BarMetrics
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.bars.hideSystemBars
import app.tileshell.brand.Brand
import app.tileshell.cortana.ui.ResponseCardView
import app.tileshell.cortana.ui.TextBoxValues
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot

/**
 * What the Search key opens when another app holds the assistant role (review R2-m7, H30).
 *
 * Android will not show a voice-interaction session for an app that is not the assistant, so there is
 * nothing to fall back to inside the session — this page IS Cortana's answer. It says what is wrong and
 * offers the role request, and the checklist's assistant row is red until it is taken. Nothing else
 * opens: the key never silently hands the user to whatever assistant is installed.
 *
 * Its look is an approximation patterned on the "Unlock to continue" card (H30).
 */
class CortanaRoleNoticeActivity : ComponentActivity() {

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        Diagnostics.add("cortana", "role notice shown; holder=${CortanaChecklist.assistantRoleHolder(this)}")
        setContent {
            ShellRoot {
                var tick by remember { mutableIntStateOf(0) }
                val colors = LocalShellColors.current
                @Suppress("UNUSED_EXPRESSION") tick
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(TextBoxValues.PAGE_BACKGROUND)
                        .semantics { testTagsAsResourceId = true },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(top = BarMetrics.STATUS_EPX.dp)) {
                            ResponseCardView(
                                card = Card(
                                    kind = CardKind.ROLE_NOTICE,
                                    title = "${Brand.ASSISTANT_NAME} needs to be your default assistant",
                                    caption = CortanaChecklist.assistantRoleHolder(this@CortanaRoleNoticeActivity)
                                        ?.let { "Right now that's $it" },
                                    buttons = listOf(
                                        CardButton("Set as default", CardAction.SET_DEFAULT_ASSISTANT),
                                    ),
                                ),
                                persona = PersonaState.IDLE_AFTER_SPEAKING,
                                level = 0f,
                                accent = colors.accent,
                                onAction = { action ->
                                    if (action == CardAction.SET_DEFAULT_ASSISTANT) {
                                        CortanaPermissionActivity.requestAssistantRole(this@CortanaRoleNoticeActivity)
                                    }
                                },
                            )
                        }
                        W10mNavBar(
                            onBack = { finish() },
                            onWindows = {
                                startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_MAIN)
                                        .addCategory(android.content.Intent.CATEGORY_HOME)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                finish()
                            },
                            // The Search key on this very page must not loop back into the same notice.
                            onSearch = {},
                            onSearchHold = {},
                        )
                    }
                    W10mStatusBar(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        // Taking the role while this page is showing makes it obsolete: Cortana can open properly now.
        if (CortanaService.roleHeld(this)) {
            Diagnostics.add("cortana", "role taken; the notice closes and Cortana opens")
            finish()
            CortanaService.open(this, CortanaMode.HOME)
        }
    }
}
