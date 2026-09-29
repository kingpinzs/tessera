# R4 — the on-phone helper spike: Jeremy's run sheet

One run on the S25 Ultra answers everything R4 owes (INDEX R4 row, PLAN.md R4). About 15 minutes, most of it hands-off.

## What it answers

| Proof | Waits on it |
|---|---|
| 1 A helper started with `app_process` runs as the shell uid on One UI 8 | phase 04 (the helper exists at all) |
| 2 It daemonises and hands the app a binder | phase 04 |
| 3 Each toggle flipped as the shell uid and read back — Wi-Fi, Bluetooth, mobile data, airplane (phase 04); battery saver, location, NFC, hotspot, automatic time (phase 19's five verbs) — directly, then through the helper | phases 04, 19 |
| 4 The helper survives the app being killed, Doze, the cable being pulled, and adbd going away (Wireless and USB debugging both off, cable out — the phone as it will be used) | phase 04 |
| Blur: can an overlay window blur what is behind it, normally and under power saving | phases 13, 04 |
| P5: can the action-center overlay (an accessibility overlay) cover the nav bar, with gestures and with buttons; an ordinary app overlay is measured beside it for contrast | phase 04 (interview item 8) |
| PQ2: what visual voicemail looks like from the shell (carrier config, the handling app, message counts). It does NOT log in to T-Mobile (no GBA / mstore call) — see "Not covered" | phase 06 |

Already done, not re-run: the licence of the on-device pairing client (adb-kt, Apache-2.0; phase 04's doc).

## Once, before the run (on the phone)

1. Developer options on: Settings > About phone > Software information > tap **Build number** 7 times.
2. Settings > Developer options > **USB debugging** on.
3. Settings > Security and privacy > **Auto Blocker** off (it blocks USB commands).
4. The phone on the **same Wi-Fi** as this PC (step 4d uses Wireless debugging).
5. A USB **data** cable into this PC; accept "Allow USB debugging?" on the phone (tick "Always allow").

## The run (on this PC, in a normal terminal — it asks you things)

```
cd ~/projects/metro-launcher
bash docs/plan/qa/r4/r4.sh
```

It lists the toggles it will flip and asks **Go ahead? [y/N]** — type `y`. Then it works alone, except for four prompts:

1. **Unplug the cable**, wait 30 s, plug it back in, press Enter.
2. **Wireless debugging on** → "Pair device with pairing code"; type the pairing IP:port and the 6-digit code, then the
   IP:port shown on the Wireless debugging page itself.
3. **Wireless debugging OFF and USB debugging OFF, cable out**, wait 60 s; then USB debugging back ON, cable in, accept the
   prompt, press Enter. (adbd only stops when both are off — that is the case the helper has to survive.)
4. **Switch the navigation type** (Settings > Display > Navigation bar: gestures ↔ buttons), press Enter; then switch it
   back when it asks.

Type `s` + Enter at any prompt to skip that step (it is recorded as SKIPPED).

## What happens on the phone

- An app called **R4 probe** is installed, and uninstalled at the end.
- Each toggle turns off and back on (or on and back off) — twice — but ONLY if its current state is one the kit
  recognises; one it cannot read is left alone and listed. The hotspot is only probed: nothing starts a hotspot. Wi-Fi,
  mobile data and airplane mode drop the connections for a few seconds each; the cable is unaffected. The battery is
  briefly *reported* unplugged (so battery saver can be tested while charging) and Doze is briefly forced, then both reset.
- The probe's accessibility service is switched on for the nav-bar step only (added to your existing list, which is put
  back exactly afterwards).
- A tinted overlay appears a few times (the blur and nav-bar probes); the script taps the screen for the nav-bar test.
- Every change is undone on ANY exit — the normal end, Ctrl-C, or an error: helpers stopped, Doze and the battery report
  reset, accessibility services and power saving put back, every recognised toggle set back to its start state. Anything
  that cannot be set back is a FAIL line telling you what to set by hand. It reminds you to turn Auto Blocker back on.

## What comes back

Everything lands in `docs/plan/qa/r4/runs/<date>-<model>/` on this PC; `SUMMARY.txt` has one line per proof (PASS / FAIL /
SKIPPED / INFO) and the file that shows it. Tell me "R4 done" — I read the folder; nothing needs copying or pasting.

The phone's run folder stays on this PC (gitignored: it holds the build fingerprint and carrier config, and the repo is
public); the findings go into the plan. Not recorded: the phone number, the IMEI, the serial number (the summary names the
model only; the Wireless-debugging pairing is recorded as "paired: yes/no"), and voicemail content (counts per app only).

## Not covered, by ruling

PQ2 asks whether the helper can reach T-Mobile's visual voicemail (mstore API + GBA SIM auth). Jeremy, 2026-09-28: "A" —
R4 only records what the shell can see; it never signs in to T-Mobile with the SIM. A sign-in probe becomes its own item
before phase 06's voicemail is built, if phase 06 needs it.

If anything fails, the summary says which file shows why; the run is safe to repeat.
