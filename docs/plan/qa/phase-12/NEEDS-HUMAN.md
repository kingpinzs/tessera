# Phase 12 — what only Jeremy can judge

`done` needs your sign-off on every H row (Hard Rule 15). Every H row is "accept this design" unless marked
[fidelity] — W10M's out-of-box setup was never filmed, so there is nothing to measure it against.

Everything on the phone is done ON THE PHONE: install the APK, use it, then paste back what the shell recorded
(Start settings > Diagnostics > **Copy everything**). No PC, no cable, no adb.

**Before you paste:** the diagnostics log also holds what you typed with the Tessera keyboard, what you said to Tess, your
reminder texts and contact names. Only the lines that start `[wizard]` and `[theme]` matter here; if you would rather
not paste the rest, keep just those lines.

## Getting the build onto the phone

The phone build comes from CI once phase 12 is pushed (every push to main publishes the "latest" release APK). Until
you say **push**, nothing reaches the phone. After the push: download the latest `tessera-…-release.apk` on the phone
and install it over the current one. That install is itself P2's "update install" check, see below.

## Phone rows

**P1: the wizard on your phone.** Your phone has never had the wizard, so it has no "finished" mark yet. The first time
you press Home after installing this build, the wizard shows if ANY of its permissions is missing (Setup checklist rows,
or Tess's). **Do not clear Tessera's data for this**: that would also wipe your Start layout, Tess's reminders and every
setting. If the wizard does not show (everything already granted), turn a few permissions off first, in phone Settings >
Apps > Tessera > Permissions (for example Location and Microphone), then press Home.

0. (Optional, and only BEFORE step 1: once the wizard is finished it never shows again.) The Default Home step, the way a
   new user meets it. Phone Settings > Apps > Tessera > **Set as default** > **Clear defaults**, then press Home. One UI
   asks which Home app to use: tap Tessera, then **Just once**. The wizard's first step should be **Default Home**. Tap
   **Set as default** and write down what Samsung's sheet looks like. In the sheet, choose **One UI Home** first and note
   what comes to the front. Then get back to Tessera (Settings > Apps > Choose default apps > Home app > Tessera, or the
   Home key if One UI asks again) and note whether the wizard is still on **Default Home** or has moved on. If it still
   shows Default Home, tap **Set as default** again and choose Tessera this time. (If anything goes wrong, Settings > Apps
   > Choose default apps > Home app > Tessera puts it back.)
1. Note whether Location is on (quick panel), then press Home. The wizard shows its first missing step.
2. Tap each step's button once and do what Android's page or dialog asks. For each step, write down the title of the page
   or dialog that opened (for example "Notification access", "Usage data access", "Allow Tessera to access photos"). Also
   note any step where that page looked wrong or empty, or where the button changed to **Open Android settings**.
   On ONE permission dialog (Photos is a good one), first tap the dimmed area outside the dialog and note whether the
   dialog closes. If it does, the step should still be there; tap its button again to carry on.
3. On **Choose a theme**, tap a theme, then **Done**. Look at Start, then open Tess (the Search key), then open any text
   field so the keyboard shows. Phone screenshots of those three are welcome for H6 / H7 if you can share them; a
   description is fine too.
4. Start settings > Diagnostics > **Copy everything**, and paste it here with your notes from steps 0-3.

What I read from the paste: every `[wizard] step … granted / partial / blocked / action failed` line, and your page
titles beside them (the phone rule rules out the doc's `dumpsys` read of the page, so your titles stand in for it). The
Default Home step shows only in step 0, because Tessera is already your Home app otherwise.

Two differences from the doc's P1, both because of the phone rule and your data: the doc starts P1 "from a fresh
sideload", and it reads each page from `dumpsys activity activities`. Here P1 starts from your installed phone (no
clearing), step 0 stands in for the fresh install's Home route, and your page titles stand in for `dumpsys`.

**P2: the marker holds.** After P1 is done:
1. Restart the phone, then press Home: no wizard.
2. Device care > **Optimise now**, then Home: no wizard.
3. Install the next CI build over this one (the update install), then Home: no wizard.
4. Phone Settings > Notifications > (Advanced) > Device & app notifications > Tessera: **turn notification access off**.
   Press Home: no wizard. Start settings > Setup checklist: the "Notification access" row is red. Turn it back on.
5. Diagnostics > Copy everything, and paste it here (I read the `[wizard] not shown: finished` lines).

## NEEDS-HUMAN (accept / change)

| Row | What to look at | Where |
|---|---|---|
| H1 | The step page: the progress caption, the title, the "why" line under it, the state line with its mark, the blue button's wording ("Open settings", "Allow", "Set as default", "Turn on", "Choose", "Allow all the time"), "Not now", "Skip setup" (first step only), and the page-to-page motion (on the emulator each page change starts about 50 ms late, then runs smoothly; say whether you see a hitch at the start on the phone) | the phone (P1); emulator screens in E2/*.png |
| H2 | The walking order: the Setup rows, then Tess's, then the themes page | P1 |
| H3 | The themes page last: six themes at the top, then every item below them, "Done" at the bottom | P1; E2/presets.png |
| H4 | Once you finish or skip the wizard it never comes back on that install. A permission you turn off later shows up red on the Setup checklist instead | P2 |
| H5 | Samsung's own pages and dialogs inside the flow do not break the feel | P1 |
| H6 | Each theme's look: Default, Windows 10 Mobile (original), HAL (red accent beside the HAL eye), Soft (light), Lumia, Midnight (the dimmed eye, solid tiles, no transparency). And **Custom**: after trying themes, it brings back your own set, your own picture included | the phone: Start settings > Start + theme, tap each |
| H7 | Tile labels readable over each theme's picture | the phone |
| H8 | The four pictures themselves (hal-a, lumia-b, midnight-b, soft-c): an original design, no text, logo, face or watermark. Lumia's is scaled about 2.4× up from 941 px and may look soft | the phone |
| H9 | [fidelity] "Windows 10 Mobile (original)" against R12's screenshots (docs/plan/r12/): Cobalt blue as the 49th accent (last row, first swatch), the two Start pictures, and the two small picture chips that switch between them | the phone beside docs/plan/r12/ |

Sign off per row ("H1 ok", "H6: Soft's tiles too pale", …).
