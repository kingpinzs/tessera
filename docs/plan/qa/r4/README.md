# R4 — the on-phone helper spike: Jeremy's run sheet (phone only)

R4 runs on the S25 Ultra ALONE (PLAN.md R4 "phone-only, standalone"): no PC, no cable. The **R4 probe** app pairs with the
phone's own Wireless debugging, starts the helper itself, runs every check, puts everything back, and you paste the
report back. About 15 minutes.

## What it answers

| Proof | Waits on it |
|---|---|
| The probe reaches the phone's own adb over Wireless debugging (as the shell uid) | phase 04 (how the helper starts with no PC) |
| 1 A helper started with `app_process` runs as the shell uid on One UI 8 | phase 04 (the helper exists at all) |
| 2 It daemonises and hands the app a binder | phase 04 |
| 3 Each toggle flipped as the shell uid and read back — Wi-Fi, Bluetooth, mobile data, airplane (phase 04); battery saver, location, NFC, automatic time, and the hotspot (probed only) (phase 19's verbs) — directly over adb and through the helper | phases 04, 19 |
| 4 The helper survives the app being killed, Doze, and adbd going away (Wireless and USB debugging off) | phase 04 |
| Blur: can an overlay window blur what is behind it, normally and under Power saving | phases 13, 04 |
| P5: can the action-center overlay (an accessibility overlay) cover the nav bar and take taps there; an ordinary app overlay beside it for contrast | phase 04 (interview item 8) |
| PQ2: what visual voicemail looks like from the shell (carrier config, the handling app, message counts) — observe only, no sign-in (Jeremy 2026-09-28: "A") | phase 06 |

Already done, not re-run: the licence of the on-device pairing client (phase 04's doc; the probe uses Kadb, Apache-2.0).

## Once, before the run

1. Install **R4 probe** from the GitHub release page (the "latest" release; it sits next to the shell's APK). If an older R4
   probe is installed, install over it.
2. Developer options on: Settings > About phone > Software information > tap **Build number** 7 times.
3. The phone on **Wi-Fi** (Wireless debugging needs it). Power saving can stay as you have it; R4 puts it back exactly.

## The run

1. Open R4 probe, allow its notifications, tap **Run R4 on this phone**.
2. **Pairing** (only the first time): the app says what to do. Open Developer options > **Wireless debugging** > turn it on
   (allow this network; "always allow" saves a prompt later) > **Pair device with pairing code**. Keep that dialog open,
   pull down the notification shade: R4 probe's notification says "type the pairing code" — tap **Enter code**, type the 6
   digits. The app carries on by itself.
3. It shows what it read for each toggle and asks **Go ahead** — tap it (or Stop if a state looks wrong).
4. Then it works alone. It asks you only for these:
   - the app closes and reopens by itself once (that is part 4a, on purpose);
   - **blur**: a tinted panel appears at the bottom twice — say whether the text behind it is blurred;
   - **navigation type**: switch it (Settings > Display > Navigation bar), come back, tap Done; then switch it back;
   - **last step (4d)**: turn Wireless debugging OFF and USB debugging OFF, wait 30 s, come back, tap Done.
5. Tap **Copy the whole report** and paste it here.

## What happens on the phone

- Each toggle turns off and back on (or on and back off), twice, but only if its current state is one R4 recognises; one
  it cannot read is left alone and listed. The hotspot is only probed — nothing starts a hotspot. Wi-Fi and airplane mode
  are flipped only by the helper (they cut Wireless debugging for a moment; the helper turns it back on).
- The battery is briefly *reported* unplugged (so battery saver can be tested while charging) and Doze is briefly forced;
  both are reset. The probe's accessibility service is switched on for the nav-bar step only, then your list is put back.
- At the end every change is undone and checked against what it read at the start; anything it cannot set back is a FAIL
  line saying what to set by hand. If the app is stopped part-way, opening it again offers **Restore now**.

## After

- Paste the report. Then uninstall R4 probe.
- Wireless debugging keeps R4 probe in its **Paired devices** list: tap it there and **Forget** (the app cannot remove its
  own pairing). Turn Developer options off if you like; turn USB debugging back on if you use it.

Not recorded: the phone number, the IMEI, the serial number, voicemail content (counts per app only).

## Without the phone on hand

`docs/plan/qa/r4/r4.sh` runs the same checks from a PC with a phone on USB; it is how the kit was dry-run on the emulator.
It is not the way R4 runs on the S25 Ultra.
