#!/usr/bin/env bash
# Phase 16 QA — the Calendar rows' include (E3–E9, E17–E19, E22–E24 and the EDGE sub-steps C01–C17), sourced AFTER
# lib.sh and p16.sh. Everything here drives the real shell on the emulator through adb; nothing is simulated, no
# microphone, no host audio.
#
# Every helper of this file is prefixed `c` (ctap, cattr, …) or `tess_` so it cannot collide with people_lib.sh, which
# edge.sh sources after this file. The navigation technique (which node to tap, how the time picker is rolled) follows
# the builder's dev-cal/scripts/cal.sh; every assertion in the rows is cut from the spec's row text, not from there.
CPX=3   # 1080 px / 360 epx on this AVD
INSTANCES=content://com.android.calendar/instances/when
REMINDERS=content://com.android.calendar/reminders
ALERTS=content://com.android.calendar/calendar_alerts
PERSONAL_ACCT=qa.personal@example.com
WORK_ACCT=qa.work@example.com
CAL_PROVIDER=com.android.providers.calendar
AOSP_CAL=com.android.calendar

# ---------------------------------------------------------------- dumps
# One attribute of the first node with a resource-id.
cattr() { # dump.xml resource-id attr
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys, html
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if 'resource-id="%s"' % sys.argv[2] in s:
        m = re.search(r'\b%s="([^"]*)"' % re.escape(sys.argv[3]), s)
        print(html.unescape(m.group(1)) if m else "")
        break
PY
}
# The text of a node, XML entities decoded (node_text leaves &apos; as it is).
ctext() { cattr "$1" "$2" text; }
# The text a node shows, its own or — a tag on a box whose text is a child — its first descendant's.
cshown() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: print(""); sys.exit()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        if n.get("text"): print(n.get("text")); break
        for c in n.iter("node"):
            if c.get("text"): print(c.get("text")); break
        else: print("")
        break
PY
}
# Every text under a node (its own included), joined with " | ".
ctexts() { # dump.xml resource-id
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: print(""); sys.exit()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(" | ".join(c.get("text") for c in n.iter("node") if c.get("text"))); break
PY
}
# Every text on the screen, joined with " | " (a notice with no tag of its own, a dialog's wording).
call_texts() { python3 -c '
import sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: sys.exit()
print(" | ".join(n.get("text") for n in root.iter("node") if n.get("text")))' "$1"; }
ccount() { grep -o "resource-id=\"$2\"" "$1" | wc -l | tr -d ' '; }                 # nodes with exactly this id
ccount_prefix() { grep -o "resource-id=\"$2[^\"]*\"" "$1" | wc -l | tr -d ' '; }    # nodes whose id begins so
cids() { grep -o "resource-id=\"$2[^\"]*\"" "$1" | sed 's/resource-id="//; s/"$//' | sort -u | tr '\n' ' ' | sed 's/ $//'; }
# Is `inner` a descendant of `outer` in the dump's tree? yes / no. (A row "inside cal_pane", an event "inside" a band.)
cunder() { # dump.xml outer-id inner-id
  python3 - "$1" "$2" "$3" <<'PY'
import sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: print("no"); sys.exit()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print("yes" if any(c.get("resource-id") == sys.argv[3] for c in n.iter("node") if c is not n) else "no"); break
else: print("no")
PY
}
# Do `inner`'s bounds lie inside `outer`'s? "yes <outer> <inner>" / "no …".
cwithin() { # dump.xml outer-id inner-id
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
def b(rid):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(rid), xml)
    return [int(x) for x in m.groups()] if m else None
o, i = b(sys.argv[2]), b(sys.argv[3])
ok = bool(o and i and o[0] <= i[0] and o[1] <= i[1] and i[2] <= o[2] and i[3] <= o[3])
print("yes" if ok else "no", o, i)
PY
}
# In a list of account headers and rows, the header directly above a row (by top edge): the account the row sits under.
cheader_above() { # dump.xml row-id [header-prefix=cal_account:]
  python3 - "$1" "$2" "${3:-cal_account:}" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
tops = {}
for m in re.finditer(r'resource-id="([^"]+)"[^>]*bounds="\[(-?\d+),(-?\d+)\]', xml):
    tops.setdefault(m.group(1), int(m.group(3)))
row = tops.get(sys.argv[2])
if row is None: print(""); sys.exit()
heads = sorted((t, k) for k, t in tops.items() if k.startswith(sys.argv[3]) and t <= row)
print(heads[-1][1][len(sys.argv[3]):] if heads else "")
PY
}

# Dump, then tap a node. A node that is not there is a FAIL verdict: a missed tap can never pass silently.
ctap() { # resource-id [settle]
  local d="$ROW_DIR/.tap.xml"
  dump_ui "$d" || true
  if [ "$(has_node "$d" "$1")" != yes ]; then
    _verdict FAIL "tap $1" "no such node on screen (cal ids: $(cids "$d" cal_ | cut -c1-300))"
    return 1
  fi
  tap_node "$d" "$1"; sleep "${2:-1.2}"
}
# Dump until a node shows (or the tries run out); the dump is left in out.xml either way.
cwait() { # out.xml resource-id [tries=8] [sleep=0.5]
  local i
  for i in $(seq 1 "${3:-8}"); do
    dump_ui "$1" || true
    [ "$(has_node "$1" "$2")" = yes ] && return 0
    sleep "${4:-0.5}"
  done
  return 1
}
cback() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-1.2}"; }
# The last slice line that holds a needle, without its stamp.
cline() { printf '%s\n' "$1" | grep -F -- "$2" | tail -1 | sed 's/^.*wall=[0-9]* //'; }
# How many slice lines hold a needle.
clines() { printf '%s\n' "$1" | grep -cF -- "$2"; }
# The ring since a MARK across restarts: the row's saved slice plus the live ring, each line once. (The ring is in
# memory: a pm revoke, a force-stop or a c6 empties it; c6 and ring_save keep what it held.)
csince() { # mark
  ring_save
  python3 - "$1" "$ROW_DIR/ring-launcher.txt" <<'PY'
import re, sys
mark = int(sys.argv[1]); seen = set()
try: lines = open(sys.argv[2], encoding='utf-8', errors='replace').read().splitlines()
except FileNotFoundError: lines = []
for line in lines:
    m = re.search(r'\bwall=(\d+)', line)
    if m and int(m.group(1)) >= mark and line not in seen:
        seen.add(line); print(line)
PY
}
ccrashes() { adb shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' | grep -c '^Process: app.tileshell$'; }

# ---------------------------------------------------------------- the Calendar app
copen() { # [am args…]  (the launcher start when none)
  if [ "$#" -eq 0 ]; then set -- -a android.intent.action.MAIN; fi
  adb shell am start -W -n "$CALENDAR_ACTIVITY" "$@" < /dev/null >/dev/null 2>&1
  sleep 2.5
}
# The Day view of the day holding an instant (the exported VIEW on a time URI).
copen_day() { # epoch-ms
  adb shell am start -W -n "$CALENDAR_ACTIVITY" -a android.intent.action.VIEW -d "content://com.android.calendar/time/$1" < /dev/null >/dev/null 2>&1
  sleep 2.5
}
# An event's page (the exported VIEW on an event), optionally at one occurrence.
copen_event() { # id [begin end]
  if [ -n "${2:-}" ]; then
    adb shell am start -W -n "$CALENDAR_ACTIVITY" -a android.intent.action.VIEW -d "content://com.android.calendar/events/$1" --el beginTime "$2" --el endTime "$3" < /dev/null >/dev/null 2>&1
  else
    adb shell am start -W -n "$CALENDAR_ACTIVITY" -a android.intent.action.VIEW -d "content://com.android.calendar/events/$1" < /dev/null >/dev/null 2>&1
  fi
  sleep 2.5
}
cview() { ctap cal_bar:view 1.2 && ctap "cal_view_pick:$1" "${2:-2}"; }   # agenda | day | week
copen_can_sync() { ctap cal_bar:more 1.2 && ctap cal_more:settings 1.5 && ctap cal_settings_open_can_sync 1.5; }
# Tick (or un-tick) a "Can sync to" row to the state asked for, whatever it reads now. On the Can sync to page.
cset_can_sync() { # calendar-id true|false
  local d="$ROW_DIR/.can_sync.xml"; dump_ui "$d"
  [ "$(cattr "$d" "cal_settings_can_sync:$1" checked)" = "$2" ] || { tap_node "$d" "cal_settings_can_sync:$1"; sleep 1.2; }
}

# Roll a loop spinner to a value (phase 15's spin_to): swipes inside its own bounds, re-reading its content-desc.
cspin() { # tag value values-csv
  local tag="$1" target="$2" csv="$3" d="$ROW_DIR/.spin.xml" i cur delta b cx cy dy dur steps
  for i in $(seq 0 24); do
    dump_ui "$d" || return 1
    cur="$(cattr "$d" "$tag" content-desc)"
    if [ "$cur" = "$target" ]; then note "spin $tag -> [$cur] after $i swipe(s)"; return 0; fi
    delta="$(python3 -c '
import sys
vals = sys.argv[1].split(","); n = len(vals)
try: ci, ti = vals.index(sys.argv[2]), vals.index(sys.argv[3])
except ValueError: print(0); sys.exit()
d = (ti - ci) % n
if d > n // 2: d -= n
print(max(-2, min(2, d)))' "$csv" "$cur" "$target")"
    [ "$delta" != 0 ] || { note "spin $tag: [$cur] and [$target] not both in the values"; return 1; }
    b="$(bounds "$d" "$tag")"
    # shellcheck disable=SC2086
    set -- $b
    cx=$(( ($1 + $3) / 2 )); cy=$(( ($2 + $4) / 2 ))
    steps=${delta#-}
    dy=$(( -delta * 32 * CPX ))
    dur=$(( 400 * steps ))
    adb shell input swipe "$cx" "$cy" "$cx" $(( cy + dy )) "$dur"
    sleep 0.9
  done
  note "spin $tag: gave up at [$cur], wanted [$target]"
  return 1
}
# Type into an editor field: tap it, type, Enter (Done gives the focus up and the keyboard goes).
ctype_field() { # field text
  ctap "cal_editor_field:$1" 1.2 || return 1
  adb shell input text "$(printf '%s' "$2" | sed 's/ /%s/g')"; sleep 1
  adb shell input keyevent KEYCODE_ENTER; sleep 1.2
}
# Clear an editor text field (select all, delete), then type.
cretype_field() { # field text
  ctap "cal_editor_field:$1" 1.2 || return 1
  adb shell input keycombination 113 29 2>/dev/null || adb shell input keyevent --longpress KEYCODE_A; sleep 0.4   # CTRL+A
  adb shell input keyevent KEYCODE_DEL; sleep 0.4
  adb shell input text "$(printf '%s' "$2" | sed 's/ /%s/g')"; sleep 1
  adb shell input keyevent KEYCODE_ENTER; sleep 1.2
}
# Set a time field through the picker's loop spinners. field hour(1-12, or 00-23 in 24-hour) minute(00-59) AM|PM
cset_time() {
  ctap "cal_editor_field:$1" 1.5 || return 1
  local hours="1,2,3,4,5,6,7,8,9,10,11,12" mins; mins="$(seq -w 0 59 | paste -sd,)"
  if [ "$(adb shell settings get system time_12_24 | tr -d '\r')" = 24 ]; then
    cspin cal_time_spinner:hour "$2" "$(seq -w 0 23 | paste -sd,)"
  else
    cspin cal_time_spinner:ampm "$4" "AM,PM"
    cspin cal_time_spinner:hour "$2" "$hours"
  fi
  cspin cal_time_spinner:minute "$3" "$mins"
  ctap cal_time_ok 1.2
}
# The text an editor field shows (a pick box carries its text on a child node).
cfield() { cshown "$1" "cal_editor_field:$2"; }   # dump.xml field

# ---------------------------------------------------------------- the provider
# The device's local midnight (or HH:MM) today, N days on, in epoch ms.
cday_ms() { # [days-from-today] [HH:MM]
  adb shell "date -d \"\$(date +%Y-%m-%d) ${2:-00:00}\" +%s" < /dev/null | tr -d '\r' | awk -v d="${1:-0}" '{printf "%d\n", ($1 + d * 86400) * 1000}'
}
# The device's local date N days on, at HH:MM, through the device's own zone rules (a day is not always 86,400 s).
clocal_ms() { # yyyy-mm-dd HH:MM
  adb shell "date -d '$1 $2' +%s" < /dev/null | tr -d '\r' | awk '{printf "%d\n", $1 * 1000}'
}
# UTC midnight of the device's local date, N days on: an all-day event's dtstart.
cutc_day_ms() { # [days-from-today]
  adb shell "date -u -d \"\$(date +%Y-%m-%d) 00:00\" +%s" < /dev/null | tr -d '\r' | awk -v d="${1:-0}" '{printf "%d\n", ($1 + d * 86400) * 1000}'
}
cdate() { # [days-from-today] -> yyyy-mm-dd on the device
  python3 -c 'import sys, datetime; print(datetime.date.fromisoformat(sys.argv[1]) + datetime.timedelta(days=int(sys.argv[2])))' "$(adb shell date +%Y-%m-%d | tr -d '\r')" "${1:-0}"
}
ctz() { adb shell getprop persist.sys.timezone | tr -d '\r'; }
# A driver insert that returns the NEW row's id from the provider's own answer is not available through `content`, so
# the id is read back by title + calendar (the newest live row). calendar title dtstart dtend [extra binds as words]
cmkevent() {
  local cal="$1" title="$2" start="$3" end="$4"; shift 4
  q "content insert --uri $EVENTS --bind calendar_id:i:$cal --bind title:s:'$title' --bind dtstart:l:$start --bind dtend:l:$end --bind eventTimezone:s:$(ctz) $*" >/dev/null
  event_id "$title" "$cal"
}
# A repeating event: RRULE + DURATION, no DTEND (the provider's rule). calendar title dtstart rrule duration [binds]
cmkseries() {
  local cal="$1" title="$2" start="$3" rrule="$4" duration="$5"; shift 5
  q "content insert --uri $EVENTS --bind calendar_id:i:$cal --bind title:s:'$title' --bind dtstart:l:$start --bind duration:s:$duration --bind rrule:s:'$rrule' --bind eventTimezone:s:$(ctz) $*" >/dev/null
  event_id "$title" "$cal"
}
cmkreminder() { q "content insert --uri $REMINDERS --bind event_id:i:$1 --bind minutes:i:${2:-10} --bind method:i:1" >/dev/null; }   # event [minutes]
cevents() { q "content query --uri $EVENTS --projection $1 --where \"$2\""; }                    # projection where
cevent_count() { q "content query --uri $EVENTS --projection _id --where \"$1\"" | grep -c '_id='; }   # where
cevent_ids() { q "content query --uri $EVENTS --projection _id --where \"$1\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | tr '\n' ' ' | sed 's/ $//'; }
# The live titles a calendar holds, sorted, joined with "|". (cal_lists first, by the caller — r3 V11.)
ctitles() { q "content query --uri $EVENTS --projection title --where \"calendar_id=$1 AND deleted=0\"" | sed -n 's/^Row: [0-9]* title=//p' | sort | paste -sd'|'; }
# Tessera's rows removed for good, as the LOCAL account's own sync adapter would (a normal delete of a row that carries
# a _sync_id only marks it deleted, and an exception row is cancelled, not removed — the builder's Change Log C1).
cpurge() { q "content delete --uri '$EVENTS?$SA&account_name=Tessera&account_type=LOCAL' --where \"$1\"" >/dev/null; }   # where
# Tessera's live event count (the restore clause "the count of Tessera's events equals the count before the row").
ctessera_count() { local t; t="$(tessera_id)"; if [ -n "$t" ]; then cevent_count "calendar_id=$t AND deleted=0"; else echo "(no Tessera)"; fi; }
# As an account's own sync adapter would write (the "other side").
cother_side() { # account verb(update|delete) event-id [binds…]
  local acct="$1" verb="$2" id="$3"; shift 3
  q "content $verb --uri '$EVENTS/$id?$SA&account_name=$acct&account_type=com.google' $*" >/dev/null
}
crmcal() { q "content delete --uri '$CAL?$SA&account_name=$1&account_type=$2' --where \"account_name='$1'\"" >/dev/null; }   # account type
# The provider's instances of an event inside [from, to]: the count, or "begin end" lines.
cinstances() { q "content query --uri $INSTANCES/$1/$2 --projection event_id:begin:end --where \"event_id=$3\"" | grep -c 'event_id='; }
cinstance_times() { q "content query --uri $INSTANCES/$1/$2 --projection event_id:begin:end --where \"event_id=$3\" --sort 'begin ASC'" | sed -n 's/.*begin=\([0-9]*\), end=\([0-9]*\).*/\1 \2/p'; }
# Every instance the provider expands inside [from, to], whatever its event.
call_instances() { q "content query --uri $INSTANCES/$1/$2 --projection event_id:begin" | grep -c 'event_id='; }
csync_json() { adb shell "run-as app.tileshell cat files/calendar_sync.json" < /dev/null 2>/dev/null | tr -d '\r'; }
csync_get() { csync_json | python3 -c '
import json, sys
try: d = json.load(sys.stdin)
except Exception: print("(no file)"); sys.exit()
print(json.dumps(d.get(sys.argv[1]), separators=(",", ":")))' "$1"; }
# Birthdays: one birthday data row on a raw contact.
cbirthday() { q "content insert --uri $DATA --bind raw_contact_id:i:$1 --bind mimetype:s:vnd.android.cursor.item/contact_event --bind data2:i:3 --bind data1:s:'$2'" >/dev/null; }   # raw value
cbirthdays_on_phone() { q "content query --uri $DATA --projection raw_contact_id:data1 --where \"mimetype='vnd.android.cursor.item/contact_event' AND data2=3\"" | grep -c 'data1='; }
cbirthdays_cal() { cals | grep -F 'account_name=Tessera Birthdays,' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }

# The Sync rows' two account calendars (E22–E24), named as the rows name them — "Personal" (qa.personal@example.com)
# and "Work" (qa.work@example.com, isPrimary 1): Personal FIRST, then Work (r3 V15), and "Offsite" in Work.
csync_fixtures_up() { # [offsite days ahead=5]
  mkcal "$PERSONAL_ACCT" Personal 0 >/dev/null
  mkcal "$WORK_ACCT" Work 1 >/dev/null
  PERSONAL="$(cal_id "$PERSONAL_ACCT" Personal)"; WORK="$(cal_id "$WORK_ACCT" Work)"
  local d="${1:-5}"
  OFFSITE="$(cmkevent "$WORK" Offsite "$(cday_ms "$d" 10:00)" "$(cday_ms "$d" 11:00)")"
}
# The sentinel (r3 V11): Work still lists, and holds exactly "Offsite", its row unchanged.
cwork_offsite() { # label
  cal_lists "$WORK" "Work, $1"
  assert_eq "$1: Work holds exactly Offsite" "Offsite" "$(ctitles "$WORK")"
}

# ---------------------------------------------------------------- notifications
# The shell's notifications on the calendar channel: one line each, "title=[…] text=[…] vis=… channel=…".
cnotes() { adb shell dumpsys notification --noredact < /dev/null | tr -d '\r' | python3 -c '
import re, sys
text = sys.stdin.read()
for block in re.split(r"(?=\n\s*NotificationRecord\()", text):
    if "pkg=app.tileshell" not in block or "calendar_reminders" not in block: continue
    if "ranker_group" in block or "GROUP_SUMMARY" in block: continue   # the system auto-group summary, not a reminder
    title = re.search(r"android\.title=\S+ \((.*?)\)\n", block)
    body = re.search(r"android\.text=\S+ \((.*?)\)\n", block)
    vis = re.search(r"\bvis=(\w+)", block)
    chan = re.search(r"channel=([\w.\-]+)", block) or re.search(r"mChannelId=([\w.\-]+)", block)
    print("title=[%s] text=[%s] vis=%s channel=%s" % (title.group(1) if title else "", body.group(1) if body else "", vis.group(1) if vis else "?", chan.group(1) if chan else "?"))'; }
# Another package's notifications (the AOSP Calendar's own reminder — E6 (b)'s recorded double).
cnotes_of() { adb shell dumpsys notification --noredact < /dev/null | tr -d '\r' | python3 -c '
import re, sys
text = sys.stdin.read(); pkg = sys.argv[1]
for block in re.split(r"(?=\n\s*NotificationRecord\()", text):
    if ("pkg=%s " % pkg) not in block and ("pkg=%s\n" % pkg) not in block: continue
    title = re.search(r"android\.title=\S+ \((.*?)\)\n", block)
    print("title=[%s]" % (title.group(1) if title else ""))' "$1"; }
calerts() { q "content query --uri $ALERTS --projection _id:event_id:state:minutes:alarmTime --where \"event_id=$1\"" | sed 's/^Row: [0-9]* //' | tr '\n' ';'; }

# ---------------------------------------------------------------- Tess (typed; never the microphone — V20)
# Open Tess and type a request. TMARK is the device clock just before the request is typed.
tess_ask() { # request [settle=1]
  ensure_start; cortana_assist; sleep 4
  TMARK="$(ring_mark)"
  type_request "$1" "${2:-1}"
}
# Poll for the card and its confirm button; the dump is left in out.xml. Tapped the moment it is up, as j6.sh does.
tess_card() { # out.xml  — the card slides in: its button is tapped only once two dumps running read it at one place
  local i prev="" b
  for i in $(seq 1 14); do
    dump_ui "$1" || true
    b="$(bounds "$1" cortana_card_button:confirm)"
    if [ -n "$b" ] && [ "$b" = "$prev" ]; then return 0; fi
    prev="$b"; sleep 0.3
  done
  [ -n "$prev" ]
}
tess_confirm() { # card.xml -> taps confirm; CMARK is the device clock just before the tap
  CMARK="$(ring_mark)"
  tap_node "$1" cortana_card_button:confirm; sleep "${2:-3.5}"
}
# Every reply since a MARK, oldest first, one per line (reply_since gives the first only).
tess_replies() { ring_since "$1" launcher | grep -F '[speech]' | grep -oE 'text="[^"]*"' | sed 's/^text="//; s/"$//'; }
# Tess's window sits over every app until it is closed: Back, then Start alone.
tess_close() { cortana_close; adb shell input keyevent KEYCODE_BACK; sleep 1; ensure_start; }

# ---------------------------------------------------------------- the pod bay, the tile
# Phase 14's pod bay, its Agenda pod scrolled into view; the dump is left in out.xml.
cpod_bay() { # out.xml
  ensure_start; swipe_right
  dump_ui "$1"
  scroll_to_node "$1" pod:agenda 6 >/dev/null 2>&1 || true
  dump_ui "$1"
}
# The texts a Start tile shows (its own node's and its descendants'), joined with " | ". Start is dumped with gdump.
ctile_texts() { # dump.xml tile-key (slot:CALENDAR, app:<component>:0)
  python3 - "$1" "tile:$2" <<'PY'
import sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: print(""); sys.exit()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(" | ".join(c.get("text") for c in n.iter("node") if c.get("text"))); break
PY
}
# The feed's last refresh line in a slice: "faces=<f> agenda=<a>".
cfeed() { cline "$1" '[calendar] refresh (' | grep -oE 'faces=[0-9]+ agenda=[0-9]+'; }

# pm clear → provision.sh → Start (C-4). The provider's account calendars survive it (only the shell is cleared).
cprovision() { # label -> prints provision.sh's rc; its output is kept in the row's folder
  adb shell pm clear app.tileshell >/dev/null
  ( bash "$P03S/provision.sh" > "$ROW_DIR/provision-$1.out" 2>&1; echo $? > "$ROW_DIR/provision-$1.rc" )
  cat "$ROW_DIR/provision-$1.rc"
}
cboot_poll() { adb wait-for-device; local i; for i in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 2; done; sleep 15; }

# ---------------------------------------------------------------- the Agenda, walked
# The Agenda is a lazy list of day groups (a cal_day:<date> heading, then that day's cal_event:<id> rows); a dump
# holds only what is laid out. This scrolls it from where it stands and writes every (date, event id, title) it saw,
# once each, to out.tsv — an event row is attributed to the heading above it in the same dump, and a row whose heading
# has scrolled off is left to the dump that showed both.
cagenda_dump() { # dump.xml -> "date<TAB>id<TAB>title" lines
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
try: root = ET.parse(sys.argv[1]).getroot()
except Exception: sys.exit()
agenda = None
for n in root.iter("node"):
    if n.get("resource-id") == "cal_agenda": agenda = n; break
if agenda is None: sys.exit()
day = None; rows = []; titles = {}
for n in agenda.iter("node"):
    rid = n.get("resource-id") or ""
    if rid.startswith("cal_day:"): day = rid[len("cal_day:"):]
    elif rid.startswith("cal_event:") and day: rows.append([day, rid[len("cal_event:"):], ""])
    elif rid.startswith("cal_event_title:") and rows and rows[-1][1] == rid[len("cal_event_title:"):]: rows[-1][2] = n.get("text") or ""
for r in rows: print("\t".join(r))
PY
}
cagenda_walk() { # out.tsv [max-swipes=30]
  local out="$1" max="${2:-30}" i cur prev="" same=0 d="$ROW_DIR/.agenda.xml"
  : > "$out.dumps"
  for i in $(seq 0 "$max"); do
    dump_ui "$d" || true
    cur="$(cagenda_dump "$d")"
    printf '%s\n' "$cur" | sed "s/^/$i\t/" >> "$out.dumps"
    # The end of the list: a swipe that changes nothing on screen, twice running.
    if [ "$cur" = "$prev" ]; then same=$((same + 1)); else same=0; fi
    [ "$same" -ge 2 ] && break
    prev="$cur"
    adb shell input swipe 540 1700 540 1150 1500; sleep 1.0   # slow: no fling past a day group
  done
  # One line per (date, event id); a row clipped at the list's edge has no title node, so the titled sighting wins.
  awk -F'\t' 'NF >= 3 { k = $2 "\t" $3; if (!(k in t) || $4 != "") t[k] = $4 } END { for (k in t) print k "\t" t[k] }' "$out.dumps" | sort > "$out"
  note "cagenda_walk: $(wc -l < "$out" | tr -d ' ') (date, event) rows after $i swipe(s)"
}
# The device-local date of an instant.
cdate_of() { adb shell "date -d @$(( $1 / 1000 )) +%Y-%m-%d" < /dev/null | tr -d '\r'; }   # epoch-ms

# EDGE sub-steps (edge_C01 … edge_C17) follow below.

# ================================================================================================ EDGE sub-steps
# Each is a function `edge_<ID>` run by scripts/edge.sh (EDGE_ONLY=C01,C02 … for these alone): its own MARK, its own
# fixtures, its own restore; every assertion's name starts with its id. scripts/edge_index.tsv maps the phase doc's
# Edge-case bullets to them.

csync_line() { cline "$1" "[calendar] sync event=" | sed 's/^\[calendar\] //'; }
# Wait up to N s for the shell to post a calendar notification with a title; prints the seconds it took, or "none".
cwait_note() { # title seconds
  local i
  for i in $(seq 1 "$2"); do
    if cnotes | grep -qF "title=[$1]"; then echo "$i"; return 0; fi
    sleep 1
  done
  echo none
}
# The Sync fixtures of an EDGE sub-step: Personal then Work, Offsite in Work, Personal allowed on "Can sync to".
cedge_sync_up() { # id
  c6; ensure_start
  cal_fixtures_down
  csync_fixtures_up 5
  copen
  TESS="$(tessera_id)"
  copen_can_sync; cset_can_sync "$PERSONAL" true; dump_ui "$ROW_DIR/$1-allowed.xml"
  assert_eq "$1: fixtures — Personal allowed on \"Can sync to\", Work not" "true false" "$(cattr "$ROW_DIR/$1-allowed.xml" "cal_settings_can_sync:$PERSONAL" checked) $(cattr "$ROW_DIR/$1-allowed.xml" "cal_settings_can_sync:$WORK" checked)"
  cback; cback
}
# A first Sync of a local event to Personal (the picker, then its one target); prints the copy's id.
cedge_sync_first() { # event-id title
  copen_event "$1"
  ctap cal_event_action:sync 2 && ctap "cal_sync_target:$PERSONAL" 3
  cevent_ids "calendar_id=$PERSONAL AND deleted=0 AND title='$2'"
}
cedge_sync_down() { # id title-like
  c6
  cal_fixtures_down
  cpurge "title LIKE '$2'"
  assert_eq "$1: restore — the sub-step's events are deleted" "0" "$(cevent_count "title LIKE '$2'")"
  ensure_start
}
# Sync on an already-synced event: prints the sync line; the page's dump and the slice are kept.
cedge_resync() { # id event-id label
  copen_event "$2"
  local m; m="$(ring_mark)"
  ctap cal_event_action:sync 3 || return 1
  dump_ui "$ROW_DIR/$1-$3.xml"
  ring_since "$m" > "$ROW_DIR/$1-$3-slice.txt"
  csync_line "$(cat "$ROW_DIR/$1-$3-slice.txt")"
}

# ---- C01 (B01.1, B01.2): no calendar and the calendar provider's package disabled
edge_C01() {
  local m slice notice pid faces i reply
  c6; ensure_start
  cal_fixtures_down; tessera_down
  assert_eq "C01: fixtures — no calendar at all" "No result found." "$(q "content query --uri $CAL --projection _id")"
  ring_save
  adb shell pm disable-user --user 0 "$CAL_PROVIDER" >/dev/null 2>&1; sleep 2
  assert_eq "C01: the calendar provider's package is disabled" "0" "$(adb shell pm list packages -e "$CAL_PROVIDER" < /dev/null | tr -d '\r' | grep -c "^package:$CAL_PROVIDER$")"
  m="$(ring_mark)"
  copen; dump_ui "$ROW_DIR/C01-off.xml"; screencap "$ROW_DIR/C01-off.png"
  notice="$(ctext "$ROW_DIR/C01-off.xml" cal_notice)"; record "C01: the provider-off notice's wording (H20)" "$notice"
  assert_eq "C01: the app says the calendar provider is off (cal_notice)" "yes" "$(printf '%s' "$notice" | grep -Eiq 'turned off|is off|disabled' && echo yes || echo no)"
  slice="$(csince "$m")"; printf '%s\n' "$slice" > "$ROW_DIR/C01-slice.txt"
  log "$(cline "$slice" '[calendar] calendars:')"
  assert_eq "C01: [calendar] calendars: none is logged with the reason" "yes" "$(printf '%s\n' "$slice" | grep -Eq '\[calendar\] calendars: none \(.+\)' && echo yes || echo no)"
  absent_in "C01: it creates nothing (no local calendar created line)" "local calendar created" "$slice"
  ensure_start
  : > "$ROW_DIR/C01-tile.txt"
  for i in 1 2 3 4 5 6 7 8; do
    gdump "$ROW_DIR/C01-start.xml"; ctile_texts "$ROW_DIR/C01-start.xml" slot:CALENDAR >> "$ROW_DIR/C01-tile.txt"; sleep 1
  done
  faces="$(grep -v '^$' "$ROW_DIR/C01-tile.txt" | sort -u | paste -sd';')"; note "the CALENDAR tile's texts over 8 dumps: $faces"
  assert_contains "C01: the tile's face is the day (the day name and number)" "$(adb shell date +%A | tr -d '\r') | $(adb shell date +%-d | tr -d '\r')" "$faces"
  assert_eq "C01: … the day only (one face, no event text)" "1" "$(grep -v '^$' "$ROW_DIR/C01-tile.txt" | grep -F "$(adb shell date +%A | tr -d '\r')" | sort -u | wc -l | tr -d ' ')"
  tess_ask "add a meeting called standup to my calendar at ten AM"
  tess_card "$ROW_DIR/C01-card.xml"
  if [ "$(has_node "$ROW_DIR/C01-card.xml" cortana_card_button:confirm)" = yes ]; then tess_confirm "$ROW_DIR/C01-card.xml"; reply="$(reply_since "$CMARK")"; else reply="$(reply_since "$TMARK")"; fi
  assert_eq "C01: Tess's \"add\" answers \"I don't have a calendar to add that to.\"" "I don't have a calendar to add that to." "$(printf '%s' "$reply" | sed "s/&apos;/'/g")"
  tess_close
  ring_save
  adb shell pm enable "$CAL_PROVIDER" >/dev/null 2>&1; sleep 3
  assert_eq "C01: restore — the calendar provider is enabled again" "1" "$(adb shell pm list packages -e "$CAL_PROVIDER" < /dev/null | tr -d '\r' | grep -c "^package:$CAL_PROVIDER$")"
  assert_eq "C01: … and Tess wrote nowhere else: no standup event, and no calendar was made while it was off" "0 No result found." "$(cevent_count "title='standup'") $(q "content query --uri $CAL --projection _id")"
  c6; copen; sleep 1
  assert_eq "C01: restore — the next start makes the shell's own calendar again (one Tessera)" "1" "$(cals | grep -cF 'account_name=Tessera,')"
  c6; ensure_start
}

# ---- C02 (B02.1–B02.3): Tessera deleted by another app while the app is open
edge_C02() {
  local ev copy start pid m slice qa t2 t3
  c6; ensure_start; cal_fixtures_down
  mkcal_qa >/dev/null; qa="$(cal_id qa)"
  csync_fixtures_up 5
  copen; TESS="$(tessera_id)"
  copen_can_sync; cset_can_sync "$PERSONAL" true; cback; cback
  start=$(( ($(device_ms) / 60000 + 120) * 60000 ))
  ev="$(cmkevent "$TESS" 'Edge C02' "$start" $(( start + 3600000 )))"
  copy="$(cedge_sync_first "$ev" 'Edge C02')"
  assert_contains "C02: fixtures — a synced local event (its mapping is in calendar_sync.json), a QA calendar present" "\"local\":$ev," "$(csync_json)"
  assert_ne "C02: fixtures — the QA calendar" "" "$qa"
  copen_day "$start"; dump_ui "$ROW_DIR/C02-before.xml"
  assert_eq "C02: the event shows in the open Day view" "yes" "$(has_node "$ROW_DIR/C02-before.xml" "cal_event:$ev")"
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"
  q "content delete --uri '$CAL/$TESS?$SA&account_name=Tessera&account_type=LOCAL'" >/dev/null
  sleep 3; dump_ui "$ROW_DIR/C02-after.xml"
  assert_eq "C02: Tessera is deleted by another app (the bullet's command)" "0" "$(cals | grep -cF 'account_name=Tessera,')"
  assert_eq "C02: its events vanish from the views through the observer (no cal_event node, the view still up)" "no yes" "$(has_node "$ROW_DIR/C02-after.xml" "cal_event:$ev") $(has_node "$ROW_DIR/C02-after.xml" cal_view)"
  assert_eq "C02: … with no restart (same pid)" "$pid" "$(adb shell pidof app.tileshell | tr -d '\r')"
  # the next WRITE recreates it, a QA calendar present
  ctap cal_bar:new 2; ctype_field title "Edge C02 new"
  m="$(ring_mark)"
  ctap cal_editor_save 3
  slice="$(ring_since "$m")"; printf '%s\n' "$slice" > "$ROW_DIR/C02-write-slice.txt"
  t2="$(tessera_id)"
  assert_ne "C02: the next write recreates Tessera (LocalCalendar.id), whatever else remains" "" "$t2"
  assert_contains "C02: … [calendar] local calendar created: …" "[calendar] local calendar created: content://com.android.calendar/calendars/$t2" "$slice"
  assert_eq "C02: … and the written event is in it" "1" "$(cevent_count "title='Edge C02 new' AND calendar_id=${t2:-0} AND deleted=0")"
  assert_ne "C02: … the QA calendar is still there and was not taken for it" "" "$(cal_id qa)"
  # the next START recreates it, a QA calendar present
  c6
  q "content delete --uri '$CAL/$t2?$SA&account_name=Tessera&account_type=LOCAL'" >/dev/null
  assert_eq "C02: Tessera deleted again, the app closed" "0" "$(cals | grep -cF 'account_name=Tessera,')"
  m="$(ring_mark)"
  copen
  slice="$(ring_since "$m")"; printf '%s\n' "$slice" > "$ROW_DIR/C02-start-slice.txt"
  t3="$(tessera_id)"
  assert_contains "C02: the next start recreates it: calendars: n (local created id=<id>)" "(local created id=$t3)" "$(cline "$slice" '[calendar] calendars:')"
  assert_contains "C02: … [calendar] local calendar created: …" "[calendar] local calendar created: content://com.android.calendar/calendars/$t3" "$slice"
  assert_absent "C02: the synced marker whose local event went with Tessera is dropped from calendar_sync.json" "\"local\":$ev," "$(csync_json)"
  record "C02: the dropped-mapping line" "$(cline "$(csince "$ROW_MARK")" 'sync mappings dropped')"
  c6
  cal_fixtures_down
  cpurge "title LIKE 'Edge C02%'"
  assert_eq "C02: restore — the sub-step's events are deleted, one Tessera left" "0 1" "$(cevent_count "title LIKE 'Edge C02%'") $(cals | grep -cF 'account_name=Tessera,')"
  ensure_start
}

# ---- C03 (B03.2): an account calendar removed while one of its events is open read-only
edge_C03() {
  local pid crash notice
  c6; ensure_start; cal_fixtures_down
  csync_fixtures_up 1
  copen
  copen_event "$OFFSITE"; dump_ui "$ROW_DIR/C03-open.xml"
  assert_eq "C03: fixtures — Work's event is open read-only (its page, no actions)" "yes 0" "$(has_node "$ROW_DIR/C03-open.xml" "cal_event_page:$OFFSITE") $(ccount_prefix "$ROW_DIR/C03-open.xml" cal_event_action:)"
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"; crash="$(ccrashes)"
  crmcal "$WORK_ACCT" com.google
  sleep 3; dump_ui "$ROW_DIR/C03-after.xml"; screencap "$ROW_DIR/C03-after.png"
  assert_eq "C03: the account calendar is removed" "" "$(cal_id "$WORK_ACCT")"
  assert_eq "C03: that page closes" "no" "$(has_node "$ROW_DIR/C03-after.xml" "cal_event_page:$OFFSITE")"
  notice="$(ctext "$ROW_DIR/C03-after.xml" cal_notice)"; record "C03: the notice's wording" "$notice"
  assert_ne "C03: … with a notice (cal_notice)" "" "$notice"
  assert_eq "C03: the view refreshes: the views are up and the event is not in them" "yes no" "$(has_node "$ROW_DIR/C03-after.xml" cal_view) $(has_node "$ROW_DIR/C03-after.xml" "cal_event:$OFFSITE")"
  assert_eq "C03: no crash, the same process" "$pid $crash" "$(adb shell pidof app.tileshell | tr -d '\r') $(ccrashes)"
  c6; cal_fixtures_down; ensure_start
}

# ---- C04 (B04.2): the copy's calendar_id no longer matches the mapping's target
edge_C04() {
  local ev copy start l
  cedge_sync_up C04
  start="$(clocal_ms "$(cdate 1)" 15:00)"
  ev="$(cmkevent "$TESS" 'Edge C04' "$start" $(( start + 3600000 )))"
  copy="$(cedge_sync_first "$ev" 'Edge C04')"
  assert_ne "C04: fixtures — a synced event, its copy in Personal" "" "$copy"
  cother_side "$PERSONAL_ACCT" update "$copy" "--bind calendar_id:i:$WORK"
  assert_contains "C04: the copy is moved on the other side (its calendar_id is Work's now)" "calendar_id=$WORK" "$(cevents calendar_id:title "_id=$copy")"
  q "content update --uri $EVENTS/$ev --bind title:s:'Edge C04 b'" >/dev/null; sleep 1
  l="$(cedge_resync C04 "$ev" stale-sync)"; log "sync: $l"
  assert_contains "C04: the next Sync refuses: failed mapping stale" "sync event=$ev -> calendar $PERSONAL: failed mapping stale" "$l"
  assert_contains "C04: … and writes nothing: the moved copy keeps its title" "title=Edge C04" "$(cevents calendar_id:title "_id=$copy" | grep -v 'Edge C04 b')"
  record "C04: the notice after the refused Sync" "$(ctext "$ROW_DIR/C04-stale-sync.xml" cal_notice)"
  copen_event "$ev"; ctap cal_event_action:delete 1.5; dump_ui "$ROW_DIR/C04-delete.xml"
  record "C04: the delete choices offered for a stale mapping" "$(cids "$ROW_DIR/C04-delete.xml" cal_delete_choice:)"
  if [ "$(has_node "$ROW_DIR/C04-delete.xml" cal_delete_choice:both)" = yes ]; then
    local m; m="$(ring_mark)"
    ctap cal_delete_choice:both 3
    ring_since "$m" > "$ROW_DIR/C04-delete-slice.txt"
    assert_contains "C04: delete-\"both\" refuses: failed mapping stale" "failed mapping stale" "$(cat "$ROW_DIR/C04-delete-slice.txt")"
  else
    _verdict FAIL "C04: delete-\"both\" refuses: failed mapping stale" "the delete prompt offered no \"both\" to tap ($(cids "$ROW_DIR/C04-delete.xml" cal_delete))"
    cback
  fi
  assert_eq "C04: … and writes nothing: the local event and the moved copy are both still there" "1 1" "$(cevent_count "_id=$ev AND deleted=0") $(cevent_count "_id=$copy AND deleted=0")"
  cedge_sync_down C04 'Edge C04%'
}

# ---- C05 (B04.3): a Sync tapped twice quickly
edge_C05() {
  local ev start b x y m lines
  cedge_sync_up C05
  start="$(clocal_ms "$(cdate 1)" 16:00)"
  ev="$(cmkevent "$TESS" 'Edge C05' "$start" $(( start + 3600000 )))"
  # a first Sync: the picker's target tapped twice at once
  copen_event "$ev"; ctap cal_event_action:sync 2; dump_ui "$ROW_DIR/C05-picker.xml"
  b="$(bounds "$ROW_DIR/C05-picker.xml" "cal_sync_target:$PERSONAL")"
  # shellcheck disable=SC2086
  set -- $b; x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
  m="$(ring_mark)"
  adb shell "input tap $x $y & input tap $x $y & wait" < /dev/null; sleep 4
  ring_since "$m" | grep -F '[calendar] sync event=' > "$ROW_DIR/C05-first-lines.txt"
  record "C05: the sync lines of a first Sync whose target was tapped twice at once" "$(sed 's/^.*\[calendar\] //' "$ROW_DIR/C05-first-lines.txt" | tr '\n' ';')"
  cal_lists "$PERSONAL" Personal
  assert_eq "C05: a first Sync tapped twice: one copy" "Edge C05" "$(ctitles "$PERSONAL")"
  # an already-synced event with something to push: the Sync action tapped twice at once
  q "content update --uri $EVENTS/$ev --bind title:s:'Edge C05 b'" >/dev/null; sleep 1
  copen_event "$ev"; dump_ui "$ROW_DIR/C05-page.xml"
  b="$(bounds "$ROW_DIR/C05-page.xml" cal_event_action:sync)"
  # shellcheck disable=SC2086
  set -- $b; x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
  m="$(ring_mark)"
  adb shell "input tap $x $y & input tap $x $y & wait" < /dev/null; sleep 5
  ring_since "$m" | grep -F '[calendar] sync event=' > "$ROW_DIR/C05-second-lines.txt"
  lines="$(sed 's/^.*\[calendar\] //' "$ROW_DIR/C05-second-lines.txt" | tr '\n' ';')"; log "the two taps' sync lines: $lines"
  assert_eq "C05: a Sync tapped twice quickly gives two sync lines" "2" "$(grep -c . "$ROW_DIR/C05-second-lines.txt")"
  assert_contains "C05: the first pushes the change (updated)" "-> calendar $PERSONAL: updated" "$(sed -n 1p "$ROW_DIR/C05-second-lines.txt")"
  assert_contains "C05: the second compares the copy with the local event and writes nothing (ok)" "-> calendar $PERSONAL: ok" "$(sed -n 2p "$ROW_DIR/C05-second-lines.txt")"
  cal_lists "$PERSONAL" Personal
  assert_eq "C05: … one copy, carrying the pushed title" "Edge C05 b" "$(ctitles "$PERSONAL")"
  cwork_offsite "C05"
  cedge_sync_down C05 'Edge C05%'
}

# ---- C06 (B04.5): calendar_sync.json lost
edge_C06() {
  local ev copy start n newcopy
  cedge_sync_up C06
  start=$(( ($(device_ms) / 60000 + 180) * 60000 ))
  ev="$(cmkevent "$TESS" 'Edge C06' "$start" $(( start + 3600000 )))"
  copy="$(cedge_sync_first "$ev" 'Edge C06')"
  copen_event "$ev"; dump_ui "$ROW_DIR/C06-marked.xml"
  assert_eq "C06: fixtures — a synced event with its marker" "yes" "$(has_node "$ROW_DIR/C06-marked.xml" "cal_synced_marker:$ev")"
  cback
  adb shell "run-as app.tileshell rm files/calendar_sync.json" < /dev/null; sleep 2
  assert_absent "C06: calendar_sync.json is lost (run-as … rm): no mapping in it" "\"local\":$ev," "$(csync_json)"
  copen_event "$ev"; dump_ui "$ROW_DIR/C06-lost.xml"
  assert_eq "C06: markers vanish (the event's page is up, no cal_synced_marker)" "yes no" "$(has_node "$ROW_DIR/C06-lost.xml" "cal_event_page:$ev") $(has_node "$ROW_DIR/C06-lost.xml" "cal_synced_marker:$ev")"
  copen_day "$start"; dump_ui "$ROW_DIR/C06-day.xml"
  record "C06: the old copy, no longer mapped, shows in the shell beside the original (cal_event:<original> / cal_event:<old copy> in the Day view)" "$(has_node "$ROW_DIR/C06-day.xml" "cal_event:$ev") / $(has_node "$ROW_DIR/C06-day.xml" "cal_event:$copy")"
  copen_event "$ev"; ctap cal_event_action:sync 2; dump_ui "$ROW_DIR/C06-sync.xml"
  record "C06: the next Sync, with the allowed list lost too, opens" "$( [ "$(has_node "$ROW_DIR/C06-sync.xml" cal_can_sync)" = yes ] && echo 'Can sync to' || echo "$(cids "$ROW_DIR/C06-sync.xml" cal_ | cut -c1-120)")"
  if [ "$(has_node "$ROW_DIR/C06-sync.xml" cal_can_sync)" = yes ]; then cset_can_sync "$PERSONAL" true; cback 1.8; fi
  ctap "cal_sync_target:$PERSONAL" 3
  cal_lists "$PERSONAL" Personal
  n="$(cevent_count "calendar_id=$PERSONAL AND deleted=0 AND title='Edge C06'")"
  assert_eq "C06: the next Sync makes a new copy — the old one stays on the other side (two in Personal)" "2" "$n"
  newcopy="$(csync_json | python3 -c '
import json, sys
try: print(next((m["copy"] for m in json.load(sys.stdin).get("mappings", []) if m["local"] == int(sys.argv[1])), ""))
except Exception: print("")' "$ev")"
  assert_eq "C06: … and the mapping names the new copy, not the old one" "yes" "$([ -n "$newcopy" ] && [ "$newcopy" != "$copy" ] && echo yes || echo "no ($newcopy)")"
  cwork_offsite "C06"
  cedge_sync_down C06 'Edge C06%'
}

# ---- C07 (B04.6): the copy's calendar became read-only
edge_C07() {
  local ev copy start l
  cedge_sync_up C07
  start="$(clocal_ms "$(cdate 1)" 17:00)"
  ev="$(cmkevent "$TESS" 'Edge C07' "$start" $(( start + 3600000 )))"
  copy="$(cedge_sync_first "$ev" 'Edge C07')"
  assert_ne "C07: fixtures — a first Sync made the copy" "" "$copy"
  q "content update --uri '$CAL/$PERSONAL?$SA&account_name=$PERSONAL_ACCT&account_type=com.google' --bind calendar_access_level:i:200" >/dev/null
  assert_contains "C07: the target's calendar_access_level is lowered to 200 through the sync-adapter URI" "calendar_access_level=200" "$(cals | grep "_id=$PERSONAL,")"
  q "content update --uri $EVENTS/$ev --bind title:s:'Edge C07 b'" >/dev/null; sleep 1
  l="$(cedge_resync C07 "$ev" readonly)"; log "sync: $l"
  assert_contains "C07: the write layer's own re-read refuses: failed calendar read-only" "sync event=$ev -> calendar $PERSONAL: failed calendar read-only" "$l"
  assert_eq "C07: \"That calendar is read-only now\" (cal_notice)" "That calendar is read-only now" "$(ctext "$ROW_DIR/C07-readonly.xml" cal_notice)"
  assert_eq "C07: nothing written: the copy keeps its old title" "Edge C07" "$(ctitles "$PERSONAL")"
  assert_eq "C07: the marker is kept, with its warning glyph" "yes yes" "$(has_node "$ROW_DIR/C07-readonly.xml" "cal_synced_marker:$ev") $(has_node "$ROW_DIR/C07-readonly.xml" cal_synced_warning)"
  cwork_offsite "C07"
  cedge_sync_down C07 'Edge C07%'
}

# ---- C08 (B05.1–B05.3): one event, one reminder — the copy edited elsewhere, the original deleted, Tessera hidden
edge_C08() {
  local a b ca cb start l i seen reply
  cedge_sync_up C08
  start=$(( ($(device_ms) / 60000 + 150) * 60000 ))
  a="$(cmkevent "$TESS" 'Edge C08 a' "$start" $(( start + 3600000 )))"
  ca="$(cedge_sync_first "$a" 'Edge C08 a')"
  assert_ne "C08: fixtures — a synced event" "" "$ca"
  # B05.1: the copy moved to another time on the other side
  cother_side "$PERSONAL_ACCT" update "$ca" "--bind dtstart:l:$(( start + 7200000 )) --bind dtend:l:$(( start + 10800000 ))"
  copen_day "$start"; dump_ui "$ROW_DIR/C08-moved.xml"
  assert_eq "C08: the copy edited on the other side to another time — the shell still shows only the original" "yes no" "$(has_node "$ROW_DIR/C08-moved.xml" "cal_event:$a") $(has_node "$ROW_DIR/C08-moved.xml" "cal_event:$ca")"
  assert_contains "C08: … at the local time" "$(TZ="$(ctz)" date -d "@$(( start / 1000 ))" '+%-I:%M %p')" "$(ctext "$ROW_DIR/C08-moved.xml" "cal_event_time:$a")"
  l="$(cedge_resync C08 "$a" overwrite)"; log "sync: $l"
  assert_contains "C08: … until the next Sync overwrites the copy (updated)" "-> calendar $PERSONAL: updated" "$l"
  assert_eq "C08: … the copy is at the local time again" "$(cevents dtstart:dtend "_id=$a" | sed 's/^Row: 0 //')" "$(cevents dtstart:dtend "_id=$ca" | sed 's/^Row: 0 //')"
  # B05.2: the original deleted by another app
  cpurge "_id=$a"; sleep 2
  copen_day "$start"; dump_ui "$ROW_DIR/C08-orphan.xml"
  assert_eq "C08: the original deleted by another app — the copy shows as the account's event" "yes" "$(has_node "$ROW_DIR/C08-orphan.xml" "cal_event:$ca")"
  assert_absent "C08: … and the mapping is dropped at the next read" "\"local\":$a," "$(csync_json)"
  cother_side "$PERSONAL_ACCT" delete "$ca"
  # B05.3: the ≡ pane hiding Tessera
  b="$(cmkevent "$TESS" 'Edge C08 b' "$start" $(( start + 3600000 )))"
  cb="$(cedge_sync_first "$b" 'Edge C08 b')"
  copen_day "$start"; dump_ui "$ROW_DIR/C08-shown.xml"
  assert_eq "C08: fixtures — a second synced event shows once, the original" "yes no" "$(has_node "$ROW_DIR/C08-shown.xml" "cal_event:$b") $(has_node "$ROW_DIR/C08-shown.xml" "cal_event:$cb")"
  ctap cal_menu 1.5; ctap "cal_calendar_row:$TESS" 1.2; dump_ui "$ROW_DIR/C08-pane.xml"
  assert_eq "C08: Tessera is hidden in the ≡ pane" "false" "$(cattr "$ROW_DIR/C08-pane.xml" "cal_calendar_row:$TESS" checked)"
  cback
  copen_day "$start"; dump_ui "$ROW_DIR/C08-hidden.xml"
  assert_eq "C08: the original leaves the Calendar app's views" "no" "$(has_node "$ROW_DIR/C08-hidden.xml" "cal_event:$b")"
  assert_eq "C08: … and its copy stays hidden (the mapping, not the pane, decides)" "no" "$(has_node "$ROW_DIR/C08-hidden.xml" "cal_event:$cb")"
  adb shell input keyevent KEYCODE_HOME; sleep 1; ensure_start
  seen=0
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    gdump "$ROW_DIR/C08-start.xml"
    case "$(ctile_texts "$ROW_DIR/C08-start.xml" slot:CALENDAR)" in *"Edge C08 b"*) seen=$((seen + 1));; esac
    sleep 1
  done
  assert_ne "C08: the tile still shows the original (dumps of 12 in which it reads Edge C08 b)" "0" "$seen"
  cpod_bay "$ROW_DIR/C08-bay.xml"
  assert_contains "C08: the Agenda pod still shows it" "Edge C08 b" "$(for i in 0 1 2 3 4 5; do ctexts "$ROW_DIR/C08-bay.xml" "pod_row:agenda:$i"; done | paste -sd';')"
  tess_ask "what is on my calendar" 5
  reply="$(reply_since "$TMARK")"
  assert_eq "C08: Tess still names it, once" "1" "$(printf '%s' "$reply" | grep -o 'Edge C08 b' | wc -l | tr -d ' ')"
  tess_close
  copen; ctap cal_menu 1.5; ctap "cal_calendar_row:$TESS" 1.2; dump_ui "$ROW_DIR/C08-pane2.xml"
  assert_eq "C08: restore — Tessera shown again in the pane" "true" "$(cattr "$ROW_DIR/C08-pane2.xml" "cal_calendar_row:$TESS" checked)"
  cback
  cedge_sync_down C08 'Edge C08%'
}

# ---- C09 (B07.1): the Birthdays calendar deleted by another app
edge_C09() {
  local r1 r2 b md tess0 i
  c6; ensure_start; cal_fixtures_down
  assert_eq "C09: fixtures — no contact carries a birthday yet" "0" "$(cbirthdays_on_phone)"
  : > "$ROW_DIR/people-fixtures.ids"; RAW_BEFORE="$(raw_count)"
  tess0="$(cals | grep -F 'account_name=Tessera,')"
  md="$(adb shell date +%m-%d | tr -d '\r')"
  r1="$(people_add 'Edge Bday One')"; cbirthday "$r1" "1990-$md"
  for i in 1 2 3 4 5 6; do sleep 1; [ -n "$(cbirthdays_cal)" ] && break; done
  b="$(cbirthdays_cal)"
  assert_ne "C09: fixtures — a Birthdays calendar exists" "" "$b"
  q "content delete --uri '$CAL?$SA&account_name=Tessera%20Birthdays&account_type=LOCAL' --where \"account_name='Tessera Birthdays'\"" >/dev/null
  sleep 2
  record "C09: Birthdays right after another app deleted it, before any contacts change or start" "[$(cbirthdays_cal)]"
  r2="$(people_add 'Edge Bday Two')"; cbirthday "$r2" "1991-$md"
  for i in 1 2 3 4 5 6 7 8; do sleep 1; [ -n "$(cbirthdays_cal)" ] && break; done
  b="$(cbirthdays_cal)"
  assert_ne "C09: recreated at the next contacts change" "" "$b"
  assert_contains "C09: … under Tessera Birthdays, access 200" "account_name=Tessera Birthdays, account_type=LOCAL, calendar_displayName=Birthdays, calendar_access_level=200" "$(cals | grep "_id=${b:-x},")"
  sleep 2
  assert_eq "C09: … holding both birthdays" "2" "$(cevent_count "calendar_id=${b:-0} AND deleted=0")"
  ring_save
  adb shell am force-stop app.tileshell; sleep 1
  q "content delete --uri '$CAL?$SA&account_name=Tessera%20Birthdays&account_type=LOCAL' --where \"account_name='Tessera Birthdays'\"" >/dev/null
  assert_eq "C09: deleted again, the shell stopped" "" "$(cbirthdays_cal)"
  adb shell input keyevent KEYCODE_HOME
  for i in 1 2 3 4 5 6 7 8 9 10; do sleep 1; [ -n "$(cbirthdays_cal)" ] && break; done
  assert_ne "C09: recreated at the next start" "" "$(cbirthdays_cal)"
  assert_eq "C09: Tessera is untouched" "$tess0" "$(cals | grep -F 'account_name=Tessera,')"
  q "content delete --uri $DATA --where \"raw_contact_id IN ($r1,$r2) AND mimetype='vnd.android.cursor.item/contact_event'\"" >/dev/null
  sleep 2
  cal_fixtures_down
  people_fixtures_down
  assert_eq "C09: restore — no birthday row is left" "0" "$(cbirthdays_on_phone)"
  ensure_start
}

# ---- C10 (B08.1): rows changing under the observer — 50 inserts over about 10 s
edge_C10() {
  local n0 n1 pid crash i t0 t1 today tz
  c6; ensure_start
  copen; TESS="$(tessera_id)"
  ctap cal_bar:today 1.2; cview day 2.5
  sleep 1
  n0="$(ring_since "$ROW_MARK" | grep -F '[calendar] view day ' | tail -1 | sed -n 's/.*: \([0-9]*\) instances.*/\1/p')"
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"; crash="$(ccrashes)"
  today="$(cdate 0)"; tz="$(ctz)"
  rm -f "$ROW_DIR"/C10-w*.sh
  for i in $(seq 1 50); do
    local s; s="$(( $(clocal_ms "$today" 06:00) + i * 600000 ))"
    echo "content insert --uri $EVENTS --bind calendar_id:i:$TESS --bind title:s:'Edge C10 $i' --bind dtstart:l:$s --bind dtend:l:$(( s + 300000 )) --bind eventTimezone:s:$tz" >> "$ROW_DIR/C10-w$(( i % 2 )).sh"
  done
  adb shell rm -rf /data/local/tmp/c10; adb shell mkdir -p /data/local/tmp/c10
  adb push "$ROW_DIR"/C10-w*.sh /data/local/tmp/c10/ >/dev/null
  t0="$(date +%s)"
  adb shell 'for f in /data/local/tmp/c10/C10-w*.sh; do sh $f > /dev/null 2>&1 & done; wait' < /dev/null
  t1="$(date +%s)"
  adb shell rm -rf /data/local/tmp/c10
  record "C10: the insert loop of 50 events ran for" "$(( t1 - t0 )) s"
  sleep 3; dump_ui "$ROW_DIR/C10-after.xml"; screencap "$ROW_DIR/C10-after.png"
  assert_eq "C10: fixtures — 50 events were inserted under the open view" "50" "$(cevent_count "title LIKE 'Edge C10 %' AND deleted=0")"
  n1="$(ring_since "$ROW_MARK" | grep -F '[calendar] view day ' | tail -1 | sed -n 's/.*: \([0-9]*\) instances.*/\1/p')"
  assert_eq "C10: the views refresh: the open Day view's last view line counts the 50 more" "$(( ${n0:-0} + 50 ))" "$n1"
  assert_eq "C10: … the view is up and shows them (cal_view, some of their nodes)" "yes yes" "$(has_node "$ROW_DIR/C10-after.xml" cal_view) $([ "$(ccount_prefix "$ROW_DIR/C10-after.xml" cal_event:)" -gt 0 ] && echo yes || echo no)"
  assert_eq "C10: without a crash (the same process, no new crash entry)" "$pid $crash" "$(adb shell pidof app.tileshell | tr -d '\r') $(ccrashes)"
  c6
  cpurge "title LIKE 'Edge C10 %'"
  assert_eq "C10: restore — the 50 events are deleted" "0" "$(cevent_count "title LIKE 'Edge C10 %'")"
  ensure_start
}

# ---- C13 (B10.1–B10.3): a past event's reminder, a 0-minute reminder, two reminders on one event
edge_C13() {
  local aosp now p z w st m slice n
  aosp="$(adb shell pm list packages -e "$AOSP_CAL" < /dev/null | tr -d '\r' | grep -c "^package:$AOSP_CAL$")"
  adb shell pm disable-user --user 0 "$AOSP_CAL" >/dev/null 2>&1
  c6; ensure_start
  TESS="$(tessera_id)"
  cpurge "title LIKE 'Edge C13%'"
  assert_eq "C13: fixtures — no calendar notification of the shell's is up, the AOSP Calendar disabled" "" "$(cnotes)"
  # a reminder on an event already past
  now="$(device_ms)"
  p="$(cmkevent "$TESS" 'Edge C13 past' $(( now - 7200000 )) $(( now - 3600000 )))"
  m="$(ring_mark)"
  cmkreminder "$p" 10; sleep 4
  record "C13: the past event's calendar_alerts rows" "[$(calerts "$p")]"
  assert_eq "C13: a reminder on an event already past — calendar_alerts has no scheduled row for it" "0" "$(q "content query --uri $ALERTS --projection event_id:state --where \"event_id=$p AND state=0\"" | grep -c 'event_id=')"
  adb shell am broadcast -a android.intent.action.EVENT_REMINDER -d content://com.android.calendar/1 -n app.tileshell/.calendar.CalendarReminderReceiver >/dev/null 2>&1
  sleep 3; copen; adb shell input keyevent KEYCODE_HOME; sleep 1
  slice="$(csince "$m")"
  assert_eq "C13: … it never notifies (no notification, even after a poke)" "0" "$(cnotes | grep -cF 'title=[Edge C13 past]')"
  absent_in "C13: … and no notified line for it" "reminder event=$p " "$slice"
  # a reminder 0 minutes before
  now="$(device_ms)"; st=$(( (now / 60000 + 30) * 60000 ))
  z="$(cmkevent "$TESS" 'Edge C13 zero' "$st" $(( st + 3600000 )))"
  cmkreminder "$z" 0; sleep 2.5
  jump_clock $(( st - 5000 )) >/dev/null
  m="$(ring_mark)"
  n="$(cwait_note 'Edge C13 zero' 12)"; sleep 3
  slice="$(csince "$m")"
  assert_ne "C13: a reminder 0 minutes before notifies at the event's start" "none" "$n"
  assert_eq "C13: … once (minutes=0: notified)" "1" "$(clines "$slice" "[calendar] reminder event=$z minutes=0: notified")"
  # two reminders on one event
  now="$(device_ms)"; st=$(( (now / 60000 + 30) * 60000 ))
  w="$(cmkevent "$TESS" 'Edge C13 two' "$st" $(( st + 3600000 )))"
  cmkreminder "$w" 10; cmkreminder "$w" 5; sleep 2.5
  jump_clock $(( st - 600000 - 5000 )) >/dev/null
  m="$(ring_mark)"
  n="$(cwait_note 'Edge C13 two' 12)"; sleep 3
  assert_eq "C13: two reminders on one event — the first notifies at T−10" "1" "$(cnotes | grep -cF 'title=[Edge C13 two]')"
  jump_clock $(( st - 300000 - 5000 )) >/dev/null
  for n in 1 2 3 4 5 6 7 8 9 10 11 12; do [ "$(cnotes | grep -cF 'title=[Edge C13 two]')" -ge 2 ] && break; sleep 1; done
  sleep 2
  slice="$(csince "$m")"; printf '%s\n' "$slice" > "$ROW_DIR/C13-two-slice.txt"
  assert_eq "C13: … and the second at T−5: two notifications" "2" "$(cnotes | grep -cF 'title=[Edge C13 two]')"
  assert_eq "C13: … one notified line for each (minutes=10, minutes=5)" "1 1" "$(clines "$slice" "[calendar] reminder event=$w minutes=10: notified") $(clines "$slice" "[calendar] reminder event=$w minutes=5: notified")"
  ring_save
  cpurge "title LIKE 'Edge C13%'"
  [ "$aosp" = 1 ] && adb shell pm enable "$AOSP_CAL" >/dev/null 2>&1
  assert_eq "C13: restore — the AOSP Calendar as it was, the events deleted" "$aosp 0" "$(adb shell pm list packages -e "$AOSP_CAL" < /dev/null | tr -d '\r' | grep -c "^package:$AOSP_CAL$") $(cevent_count "title LIKE 'Edge C13%'")"
  clock_restore
  assert_eq "C13: restore — no calendar notification of the shell's is left" "" "$(cnotes)"
  ensure_start
}

# ---- C16 (B12.3): the clock jumped backwards a year — the one backwards jump of the phase (r3 V4)
edge_C16() {
  local now ev start n0 back m d title want_title day
  c6; ensure_start
  copen; TESS="$(tessera_id)"; adb shell input keyevent KEYCODE_HOME; sleep 1
  now="$(device_ms)"
  start="$(clocal_ms "$(cdate 0)" 12:00)"
  ev="$(cmkevent "$TESS" 'Edge C16' "$start" $(( start + 3600000 )))"
  n0="$(ctessera_count)"
  assert_ne "C16: fixtures — an event today" "" "$ev"
  back=$(( now - 365 * 86400000 ))
  ring_save
  ALLOW_BACKWARDS=1 jump_clock "$back" > "$ROW_DIR/C16-jump.txt"
  adb shell am force-stop app.tileshell
  ensure_start
  m="$(ring_mark)"
  assert_within "C16: the clock is a year back (the device's ms against the target)" "$back" "$(device_ms)" 60000
  day="$(cdate 0)"; want_title="$(TZ="$(ctz)" date -d "@$(( back / 1000 ))" '+%B %Y' | tr 'a-z' 'A-Z')"
  copen; ctap cal_bar:today 1.2; dump_ui "$ROW_DIR/C16-back.xml"; screencap "$ROW_DIR/C16-back.png"
  d="$ROW_DIR/C16-back.xml"
  title="$(ctext "$d" cal_month_title)"
  assert_eq "C16: the views follow: the header reads the jumped month and year" "$want_title" "$title"
  assert_eq "C16: … today (the strip's selected day) is the jumped date" "true" "$(cattr "$d" "cal_strip_day:$day" selected)"
  assert_eq "C16: no event is lost: Tessera holds what it held" "$n0" "$(ctessera_count)"
  copen_day "$start"; dump_ui "$ROW_DIR/C16-event-day.xml"
  assert_eq "C16: … and the event still shows on its own day (a year ahead of the clock now)" "yes" "$(has_node "$ROW_DIR/C16-event-day.xml" "cal_event:$ev")"
  record "C16: the reminders-count line after a clock set back (the Q-16-4 build's; empty on a build without it)" "$(cline "$(csince "$m")" '[calendar] reminders count from')"
  ring_save
  cpurge "title='Edge C16'"
  clock_restore
  assert_eq "C16: restore — the event deleted, the clock back on the host's" "0" "$(cevent_count "title='Edge C16'")"
  ensure_start
}

# ---- C17 (B13.1, B13.3, B13.5): the tile's tap lands on today; VIEW on an event; a malformed URI
edge_C17() {
  local ev start today pid crash u
  c6; ensure_start
  copen; TESS="$(tessera_id)"
  today="$(cdate 0)"
  start="$(clocal_ms "$(cdate 3)" 11:00)"
  ev="$(cmkevent "$TESS" 'Edge C17' "$start" $(( start + 3600000 )))"
  # the app left on another day, then Home (no force-stop), then the tile
  ctap cal_bar:today 1.2; cview agenda 2
  ctap "cal_strip_day:$(cdate 3)" 1.5; dump_ui "$ROW_DIR/C17-away.xml"
  assert_eq "C17: fixtures — the app is left on another day ($(cdate 3))" "true" "$(cattr "$ROW_DIR/C17-away.xml" "cal_strip_day:$(cdate 3)" selected)"
  ensure_start
  gdump "$ROW_DIR/C17-start.xml"
  tap_node "$ROW_DIR/C17-start.xml" tile:slot:CALENDAR; sleep 3.5
  dump_ui "$ROW_DIR/C17-tile-tap.xml"
  assert_eq "C17: the Calendar tile's tap resumes the shell's Calendar" "$CALENDAR_ACTIVITY" "$(top_activity)"
  assert_eq "C17: … and lands on today (the strip's selected day is today)" "true" "$(cattr "$ROW_DIR/C17-tile-tap.xml" "cal_strip_day:$today" selected)"
  # VIEW on an event
  adb shell am start -W -n "$CALENDAR_ACTIVITY" -a android.intent.action.VIEW -d "content://com.android.calendar/events/$ev" < /dev/null >/dev/null 2>&1; sleep 2.5
  dump_ui "$ROW_DIR/C17-view-event.xml"
  assert_eq "C17: a content://com.android.calendar/events/<id> VIEW opens that event" "yes" "$(has_node "$ROW_DIR/C17-view-event.xml" "cal_event_page:$ev")"
  cback
  # malformed URIs
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"; crash="$(ccrashes)"
  for u in "content://com.android.calendar/events/notanumber" "content://com.android.calendar/time/xyz" "content://com.android.calendar/nonsense/1/2/3"; do
    ctap "cal_strip_day:$(cdate 3)" 1.2 >/dev/null 2>&1 || true
    adb shell am start -W -n "$CALENDAR_ACTIVITY" -a android.intent.action.VIEW -d "$u" < /dev/null > "$ROW_DIR/C17-malformed.out" 2>&1; sleep 2.5
    dump_ui "$ROW_DIR/C17-malformed.xml"
    assert_eq "C17: a malformed URI ($u) opens today: the views, no event page" "yes 0" "$(has_node "$ROW_DIR/C17-malformed.xml" cal_view) $(ccount_prefix "$ROW_DIR/C17-malformed.xml" cal_event_page:)"
    assert_eq "C17: … on today (cal_day:$today is showing)" "yes" "$(has_node "$ROW_DIR/C17-malformed.xml" "cal_day:$today")"
    assert_eq "C17: … with no crash (the same process, no new crash entry)" "$pid $crash" "$(adb shell pidof app.tileshell | tr -d '\r') $(ccrashes)"
  done
  c6
  cpurge "title='Edge C17'"
  assert_eq "C17: restore — the event deleted" "0" "$(cevent_count "title='Edge C17'")"
  ensure_start
}

# ---- C11 (B09.1): an event whose dtend < dtstart is refused by the editor; and the EDGE row's producer of
#      `[calendar] write update event=<id>: failed <err>` (the event deleted by the driver while its editor is open)
edge_C11() {
  local ev m slice notice start
  c6; ensure_start
  copen; TESS="$(tessera_id)"
  cpurge "title LIKE 'Edge C11%'"
  ctap cal_bar:today 1.2
  ctap cal_bar:new 2
  ctype_field title "Edge C11"
  cset_time start_time 3 00 PM
  cset_time end_time 2 00 PM
  dump_ui "$ROW_DIR/C11-editor.xml"
  note "C11: the editor before Save: start [$(cfield "$ROW_DIR/C11-editor.xml" start_date) $(cfield "$ROW_DIR/C11-editor.xml" start_time)] end [$(cfield "$ROW_DIR/C11-editor.xml" end_date) $(cfield "$ROW_DIR/C11-editor.xml" end_time)]"
  assert_eq "C11: fixtures — the editor holds an end before its start (3:00 PM → 2:00 PM, one date)" "3:00 PM 2:00 PM yes" "$(cfield "$ROW_DIR/C11-editor.xml" start_time) $(cfield "$ROW_DIR/C11-editor.xml" end_time) $([ "$(cfield "$ROW_DIR/C11-editor.xml" start_date)" = "$(cfield "$ROW_DIR/C11-editor.xml" end_date)" ] && echo yes || echo no)"
  ctap cal_editor_save 2.5; dump_ui "$ROW_DIR/C11-refused.xml"; screencap "$ROW_DIR/C11-refused.png"
  notice="$(ctext "$ROW_DIR/C11-refused.xml" cal_notice)"; record "C11: the refusal's wording" "$notice"
  assert_eq "C11: an event whose end is before its start is refused by the editor (cal_notice, the editor still open)" "yes yes" "$([ -n "$notice" ] && echo yes || echo no) $(has_node "$ROW_DIR/C11-refused.xml" cal_editor)"
  assert_eq "C11: … the notice says why" "yes" "$(printf '%s' "$notice" | grep -Eiq 'end.*before|before.*start' && echo yes || echo no)"
  assert_eq "C11: … and nothing is written" "0" "$(cevent_count "title='Edge C11'")"
  ctap cal_editor_cancel 1.5
  # the event deleted by the driver while its editor is open, then Save
  start="$(clocal_ms "$(cdate 1)" 11:00)"
  ev="$(cmkevent "$TESS" 'Edge C11 gone' "$start" $(( start + 3600000 )))"
  copen_event "$ev"; ctap cal_event_action:edit 2; ctype_field title "X"
  cpurge "_id=$ev"; sleep 1
  m="$(ring_mark)"
  ctap cal_editor_save 2.5; dump_ui "$ROW_DIR/C11-gone.xml"
  slice="$(ring_since "$m")"; printf '%s\n' "$slice" > "$ROW_DIR/C11-gone-slice.txt"
  log "$(cline "$slice" '[calendar] write ')"
  assert_contains "C11: Save of an event the driver deleted under its open editor: [calendar] write update event=<id>: failed <err>" "[calendar] write update event=$ev: failed " "$slice"
  assert_ne "C11: … with a notice on the editor" "" "$(ctext "$ROW_DIR/C11-gone.xml" cal_notice)"
  assert_eq "C11: … and nothing written" "0" "$(cevent_count "title LIKE 'Edge C11 gone%'")"
  ctap cal_editor_cancel 1.5
  c6
  cpurge "title LIKE 'Edge C11%'"
  assert_eq "C11: restore — no event of the sub-step is left" "0" "$(cevent_count "title LIKE 'Edge C11%'")"
  ensure_start
}

# ---- C12 (B09.2–B09.4): a 10-year daily series; BYDAY=2TU; an exception that moves an occurrence to another day
edge_C12() {
  local today start until d m slice t w b2 e2 d2 newday ex line n k tu first want got
  c6; ensure_start
  copen; TESS="$(tessera_id)"
  cpurge "title LIKE 'Edge C12%'"
  today="$(cdate 0)"
  # a 10-year daily series with no COUNT
  start="$(clocal_ms "$today" 07:00)"
  until="$(date -u -d "$today + 10 years" +%Y%m%dT000000Z)"
  d="$(cmkseries "$TESS" 'Edge C12 daily' "$start" "FREQ=DAILY;UNTIL=$until" PT30M)"
  assert_contains "C12: fixtures — a 10-year daily series with no COUNT" "rrule=FREQ=DAILY;UNTIL=$until" "$(cevents rrule "_id=${d:-0}")"
  ctap cal_bar:today 1.2; cview week 2.5
  m="$(ring_mark)"
  cview agenda 3; dump_ui "$ROW_DIR/C12-agenda.xml"
  slice="$(ring_since "$m")"
  line="$(cline "$slice" '[calendar] view agenda ')"; log "$line"
  assert_eq "C12: the Agenda still renders with the series in it" "yes" "$(has_node "$ROW_DIR/C12-agenda.xml" "cal_event:$d")"
  n="$(printf '%s' "$line" | sed -n 's/.* in \([0-9]*\) ms.*/\1/p')"
  assert_eq "C12: … its view agenda line: in <ms> ms ≤ 3000" "yes" "$([ -n "$n" ] && [ "$n" -le 3000 ] && echo yes || echo "no ($n)")"
  m="$(ring_mark)"
  cview week 3; dump_ui "$ROW_DIR/C12-week.xml"
  slice="$(ring_since "$m")"
  line="$(cline "$slice" '[calendar] view week ')"; log "$line"
  assert_eq "C12: the Week view still renders with the series in it" "yes" "$([ "$(ccount "$ROW_DIR/C12-week.xml" "cal_event:$d")" -gt 0 ] && echo yes || echo no)"
  n="$(printf '%s' "$line" | sed -n 's/.* in \([0-9]*\) ms.*/\1/p')"
  assert_eq "C12: … its view week line: in <ms> ms ≤ 3000" "yes" "$([ -n "$n" ] && [ "$n" -le 3000 ] && echo yes || echo "no ($n)")"
  cpurge "_id=$d"
  # BYDAY=2TU: the second Tuesday of four months, from the next month on
  want="$(python3 - "$today" <<'PY'
import sys, datetime
t = datetime.date.fromisoformat(sys.argv[1]); out = []
y, m = t.year, t.month
for _ in range(4):
    m += 1
    if m > 12: y, m = y + 1, 1
    d = datetime.date(y, m, 1)
    d += datetime.timedelta(days=(1 - d.weekday()) % 7 + 7)   # the first Tuesday, then a week on
    out.append(d.isoformat())
print(" ".join(out))
PY
)"
  first="${want%% *}"
  t="$(cmkseries "$TESS" 'Edge C12 second tuesday' "$(clocal_ms "$first" 09:00)" 'FREQ=MONTHLY;BYDAY=2TU;COUNT=4' PT1H)"
  got="$(cinstance_times "$(clocal_ms "$today" 00:00)" "$(clocal_ms "$(date -d "$today + 6 months" +%Y-%m-%d)" 00:00)" "$t" | while read -r b e; do cdate_of "$b"; done | tr '\n' ' ' | sed 's/ $//')"
  assert_eq "C12: fixtures — an RRULE with BYDAY=2TU expands (the provider) on the second Tuesdays" "$want" "$got"
  k=0
  for tu in $want; do
    copen_day "$(clocal_ms "$tu" 12:00)"; dump_ui "$ROW_DIR/C12-2tu-$tu.xml"
    [ "$(has_node "$ROW_DIR/C12-2tu-$tu.xml" "cal_event:$t")" = yes ] && k=$((k + 1))
  done
  assert_eq "C12: BYDAY=2TU shows on the right days (the Day view of each of the four second Tuesdays)" "4" "$k"
  copen_day "$(clocal_ms "$(date -d "$first - 7 days" +%Y-%m-%d)" 12:00)"; dump_ui "$ROW_DIR/C12-2tu-first-tuesday.xml"
  copen_day "$(clocal_ms "$(date -d "$first + 7 days" +%Y-%m-%d)" 12:00)"; dump_ui "$ROW_DIR/C12-2tu-third-tuesday.xml"
  assert_eq "C12: … and not on the first or the third Tuesday" "no no" "$(has_node "$ROW_DIR/C12-2tu-first-tuesday.xml" "cal_event:$t") $(has_node "$ROW_DIR/C12-2tu-third-tuesday.xml" "cal_event:$t")"
  cpurge "_id=$t"
  # an exception that moves an occurrence to another day
  start="$(clocal_ms "$(cdate 2)" 10:00)"
  w="$(cmkseries "$TESS" 'Edge C12 weekly' "$start" 'FREQ=WEEKLY;COUNT=3' PT1H)"
  cinstance_times "$start" $(( start + 4 * 7 * 86400000 )) "$w" > "$ROW_DIR/C12-weekly-instances.txt"
  read -r b2 e2 <<< "$(sed -n 2p "$ROW_DIR/C12-weekly-instances.txt")"
  d2="$(cdate_of "$b2")"
  newday="$(python3 -c '
import sys, datetime
d = datetime.date.fromisoformat(sys.argv[1]); n = d + datetime.timedelta(days=1)
print(n if n.month == d.month else d - datetime.timedelta(days=1))' "$d2")"
  copen_event "$w" "$b2" "$e2"
  ctap cal_event_action:edit 1.5; ctap cal_occurrence:this 2
  ctap cal_editor_field:start_date 1.5
  ctap "calc_date_pick:day:$(echo "$newday" | cut -c9-10 | sed 's/^0//')" 1.2
  ctap calc_date_pick_ok 1.5
  dump_ui "$ROW_DIR/C12-moved-editor.xml"
  note "C12: the editor after the date pick: start [$(cfield "$ROW_DIR/C12-moved-editor.xml" start_date) $(cfield "$ROW_DIR/C12-moved-editor.xml" start_time)] end [$(cfield "$ROW_DIR/C12-moved-editor.xml" end_date) $(cfield "$ROW_DIR/C12-moved-editor.xml" end_time)]"
  ctap cal_editor_save 3
  ex="$(cevent_ids "original_id=$w")"
  assert_ne "C12: fixtures — the second occurrence is moved to another day in the app (an exception row)" "" "$ex"
  assert_eq "C12: … to $newday" "$newday" "$(cdate_of "$(cevents dtstart "_id=${ex:-0}" | sed -n 's/.*dtstart=\([0-9]*\).*/\1/p')")"
  copen_day "$(clocal_ms "$newday" 12:00)"; dump_ui "$ROW_DIR/C12-moved-new.xml"
  copen_day "$(clocal_ms "$d2" 12:00)"; dump_ui "$ROW_DIR/C12-moved-old.xml"
  assert_eq "C12: an exception that moves an occurrence to another day shows on the new day" "yes" "$(has_node "$ROW_DIR/C12-moved-new.xml" "cal_event:$ex")"
  assert_eq "C12: … only: the old day shows neither it nor the series' own occurrence" "no no" "$(has_node "$ROW_DIR/C12-moved-old.xml" "cal_event:$ex") $(has_node "$ROW_DIR/C12-moved-old.xml" "cal_event:$w")"
  read -r b2 e2 <<< "$(sed -n 1p "$ROW_DIR/C12-weekly-instances.txt")"
  copen_day "$b2"; dump_ui "$ROW_DIR/C12-moved-first.xml"
  read -r b2 e2 <<< "$(sed -n 3p "$ROW_DIR/C12-weekly-instances.txt")"
  copen_day "$b2"; dump_ui "$ROW_DIR/C12-moved-third.xml"
  assert_eq "C12: … and the series' other occurrences are still shown (the first, the third)" "yes yes" "$(has_node "$ROW_DIR/C12-moved-first.xml" "cal_event:$w") $(has_node "$ROW_DIR/C12-moved-third.xml" "cal_event:$w")"
  c6
  cpurge "title LIKE 'Edge C12%'"
  assert_eq "C12: restore — no event of the sub-step is left" "0" "$(cevent_count "title LIKE 'Edge C12%'")"
  ensure_start
}

# ---- C14 (B11.1–B11.3): a null colour, two calendars with one colour, an empty display name
edge_C14() {
  local qa ev start empty acc sel bar b pxpy
  pxpy="$(dirname "${BASH_SOURCE[0]}")/cal_px.py"
  c6; ensure_start; cal_fixtures_down
  mkcal_qa >/dev/null; qa="$(cal_id qa)"
  csync_fixtures_up 5
  q "content insert --uri '$CAL?$SA&account_name=qa.empty@example.com&account_type=com.google' --bind account_name:s:qa.empty@example.com --bind account_type:s:com.google --bind name:s:empty --bind calendar_displayName:s: --bind calendar_access_level:i:700 --bind ownerAccount:s:qa.empty@example.com --bind visible:i:1 --bind sync_events:i:1 --bind calendar_color:i:-65536" >/dev/null
  empty="$(cal_id qa.empty@example.com)"
  assert_contains "C14: fixtures — a calendar with a NULL colour (the QA calendar)" "calendar_color=NULL" "$(q "content query --uri $CAL --projection _id:calendar_color --where \"_id=${qa:-0}\"")"
  assert_eq "C14: fixtures — two calendars with one colour (Personal, Work)" "$(q "content query --uri $CAL --projection calendar_color --where \"_id=$PERSONAL\"" | sed 's/^Row: 0 //')" "$(q "content query --uri $CAL --projection calendar_color --where \"_id=$WORK\"" | sed 's/^Row: 0 //')"
  assert_contains "C14: fixtures — a calendar whose display name is empty" "calendar_displayName=," "$(q "content query --uri $CAL --projection _id:calendar_displayName:account_name --where \"_id=${empty:-0}\"")"
  start="$(clocal_ms "$(cdate 1)" 10:00)"
  ev="$(cmkevent "$qa" 'Edge C14 null' "$start" $(( start + 3600000 )))"
  copen; ctap cal_bar:today 1.2; cview agenda 2
  ctap "cal_strip_day:$(cdate 1)" 1.5
  dump_ui "$ROW_DIR/C14-agenda.xml"; screencap "$ROW_DIR/C14-agenda.png"
  # the accent, as this screen draws it: the selected day's square
  b="$(bounds "$ROW_DIR/C14-agenda.xml" cal_strip_selected)"
  # shellcheck disable=SC2086
  set -- $b; acc="$(python3 "$pxpy" pixel "$ROW_DIR/C14-agenda.png" $(( $1 + 6 )) $(( $2 + 6 )))"
  b="$(bounds "$ROW_DIR/C14-agenda.xml" "cal_event_bar:$ev")"
  # shellcheck disable=SC2086
  set -- $b; bar="$(python3 "$pxpy" pixel "$ROW_DIR/C14-agenda.png" $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )))"
  note "C14: the accent on this screen (the selected day's square) [$acc]; the null-colour calendar's event bar [$bar]"
  assert_ne "C14: the accent is read from the screen" "" "$acc"
  assert_eq "C14: a calendar with a null colour — the accent is drawn (its event's bar is the accent's colour)" "$acc" "$bar"
  ctap cal_menu 1.5; dump_ui "$ROW_DIR/C14-pane.xml"; screencap "$ROW_DIR/C14-pane.png"
  assert_eq "C14: two calendars with one colour — both listed, by name (Personal)" "Personal" "$(ctexts "$ROW_DIR/C14-pane.xml" "cal_calendar_row:$PERSONAL")"
  assert_eq "C14: … (Work)" "Work" "$(ctexts "$ROW_DIR/C14-pane.xml" "cal_calendar_row:$WORK")"
  scroll_to_node "$ROW_DIR/C14-pane2.xml" "cal_calendar_row:$empty" 4 >/dev/null 2>&1 || true
  dump_ui "$ROW_DIR/C14-pane2.xml"
  assert_eq "C14: a calendar whose display name is empty — its account name is shown" "qa.empty@example.com" "$(ctexts "$ROW_DIR/C14-pane2.xml" "cal_calendar_row:$empty")"
  cback
  c6
  crmcal qa.empty@example.com com.google
  cal_fixtures_down
  assert_eq "C14: restore — the empty-named calendar is gone too" "" "$(cal_id qa.empty@example.com)"
  ensure_start
}

# ---- C15 (B12.1, B12.2): the 24-hour setting; a locale change with no restart
edge_C15() {
  local t0 ev start pid d all f first0 first1 title0 title1 loc0
  t0="$(adb shell settings get system time_12_24 | tr -d '\r')"
  c6; ensure_start
  copen; TESS="$(tessera_id)"
  cpurge "title='Edge C15'"
  start="$(clocal_ms "$(cdate 0)" 13:00)"
  ev="$(cmkevent "$TESS" 'Edge C15' "$start" $(( start + 3600000 )))"
  adb shell settings put system time_12_24 24; sleep 2
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"
  ctap cal_bar:today 1.2; cview agenda 2.5; dump_ui "$ROW_DIR/C15-24-agenda.xml"
  assert_eq "C15: with time_12_24 = 24 the Agenda's time label reads H:mm (13:00 over 14:00)" "13:00 14:00" "$(ctext "$ROW_DIR/C15-24-agenda.xml" "cal_event_time:$ev" | tr '\n' ' ' | sed 's/ $//')"
  cview day 2.5; dump_ui "$ROW_DIR/C15-24-day.xml"
  assert_eq "C15: … the Day view's block" "13:00 – 14:00" "$(ctext "$ROW_DIR/C15-24-day.xml" "cal_event_time:$ev")"
  copen_event "$ev"; dump_ui "$ROW_DIR/C15-24-page.xml"
  assert_contains "C15: … the event's page" "13:00 – 14:00" "$(ctext "$ROW_DIR/C15-24-page.xml" "cal_event_time:$ev")"
  ctap cal_event_action:edit 2; dump_ui "$ROW_DIR/C15-24-editor.xml"
  assert_eq "C15: … the editor's time fields" "13:00 14:00" "$(cfield "$ROW_DIR/C15-24-editor.xml" start_time) $(cfield "$ROW_DIR/C15-24-editor.xml" end_time)"
  ctap cal_editor_cancel 1.5
  all=""
  for f in agenda day page editor; do all="$all | $(call_texts "$ROW_DIR/C15-24-$f.xml")"; done
  assert_eq "C15: every time reads H:mm — no AM or PM on any of the four screens" "0" "$(printf '%s' "$all" | grep -oE '(^|[^A-Za-z])(AM|PM)([^A-Za-z]|$)' | wc -l | tr -d ' ')"
  assert_eq "C15: … with no restart of the shell" "$pid" "$(adb shell pidof app.tileshell | tr -d '\r')"
  if [ "$t0" = null ]; then adb shell settings delete system time_12_24 >/dev/null; else adb shell settings put system time_12_24 "$t0"; fi
  assert_eq "C15: restore — time_12_24 as it was" "$t0" "$(adb shell settings get system time_12_24 | tr -d '\r')"
  # a locale change (the app's locale, set from the shell user: `cmd locale set-app-locales`)
  cback; copen; ctap cal_bar:today 1.2; cview agenda 2.5; dump_ui "$ROW_DIR/C15-locale-before.xml"
  title0="$(ctext "$ROW_DIR/C15-locale-before.xml" cal_month_title)"
  first0="$(grep -o 'resource-id="cal_strip_day:[0-9-]*"' "$ROW_DIR/C15-locale-before.xml" | head -1 | sed 's/.*day://; s/"//')"
  loc0="$(adb shell cmd locale get-app-locales app.tileshell --user 0 2>&1 | tr -d '\r' | tail -1)"
  note "C15: before — the header [$title0], the strip's first day $first0 ($(date -d "$first0" +%A)); app locales: $loc0"
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"
  adb shell cmd locale set-app-locales app.tileshell --user 0 --locales de-DE > "$ROW_DIR/C15-set-locale.out" 2>&1
  sleep 4; dump_ui "$ROW_DIR/C15-locale-de.xml"; screencap "$ROW_DIR/C15-locale-de.png"
  title1="$(ctext "$ROW_DIR/C15-locale-de.xml" cal_month_title)"
  first1="$(grep -o 'resource-id="cal_strip_day:[0-9-]*"' "$ROW_DIR/C15-locale-de.xml" | head -1 | sed 's/.*day://; s/"//')"
  note "C15: after de-DE — the header [$title1], the strip's first day $first1 ($( [ -n "$first1" ] && date -d "$first1" +%A)); set-app-locales said [$(cat "$ROW_DIR/C15-set-locale.out")]"
  assert_eq "C15: a locale change re-labels months (the header in German)" "$(LC_ALL=C python3 -c '
import sys
m = ["JANUAR","FEBRUAR","MÄRZ","APRIL","MAI","JUNI","JULI","AUGUST","SEPTEMBER","OKTOBER","NOVEMBER","DEZEMBER"]
y, mo = sys.argv[1].split("-")[:2]; print(m[int(mo) - 1], y)' "$(cdate 0)")" "$title1"
  assert_eq "C15: … and the first day of the week (Sunday in en-US, Monday in de-DE)" "Sunday Monday" "$(date -d "$first0" +%A) $( [ -n "$first1" ] && date -d "$first1" +%A)"
  assert_eq "C15: … without a restart (the shell's process is the same)" "$pid" "$(adb shell pidof app.tileshell | tr -d '\r')"
  adb shell cmd locale set-app-locales app.tileshell --user 0 --locales "" > /dev/null 2>&1
  sleep 4; dump_ui "$ROW_DIR/C15-locale-back.xml"
  assert_eq "C15: restore — the app's locale back: the header as before" "$title0" "$(ctext "$ROW_DIR/C15-locale-back.xml" cal_month_title)"
  c6
  cpurge "title='Edge C15'"
  assert_eq "C15: restore — the event deleted" "0" "$(cevent_count "title='Edge C15'")"
  ensure_start
}
