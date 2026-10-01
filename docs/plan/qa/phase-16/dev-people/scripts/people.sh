#!/usr/bin/env bash
# Phase 16 People builder: what every dev-people driver shares. Sourced AFTER lib.sh (the symlink beside this file).
# These are development proof, not the gate: the lead writes and runs E1–E28 and EDGE from the phase doc.
#
# lib.sh works its paths out from where it is sourced; this folder sits one level deeper than a phase's scripts folder,
# so the repo root and the APK are set here from git.
export ANDROID_SERIAL=emulator-5554
REPO="$(git -C "$HERE" rev-parse --show-toplevel)"
APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"
PEOPLE=app.tileshell/.people.PeopleActivity

S() { adb shell "$@" | tr -d '\r'; }

# The device is shared with another builder who installs a different build of this package: install ours only when the
# device holds another one. Never with -g; a permission a session needs is granted with pm grant and said so.
ensure_build() {
  if [ "$(apk_matches | cut -c1-3)" = "yes" ]; then
    echo "device already holds this build"
  else
    adb install -r "$APK" 2>&1 | tail -1
    sleep 5
  fi
}

top_activity() { S dumpsys activity activities | grep -m1 'topResumedActivity' | sed -E 's/.* u0 ([^ ]+) .*/\1/'; }
perm() { S dumpsys package app.tileshell | grep -m1 "android.permission.$1: granted" | sed -E 's/.*granted=([a-z]+).*/\1/'; }

# The phase 05 gesture driver's dump, for a window that never idles (p15.sh's gdump, copied: it cannot be sourced
# without p15's own state).
gdump() { # out.xml
  local out="$1" i name
  : > "$out.drv"
  for i in $(seq 1 20); do
    name="people_$(date +%s%N)_$i.xml"
    adb shell am instrument -r -w -e op dump -e out "/sdcard/Download/$name" "$DRV_RUNNER" >> "$out.drv" 2>&1
    adb shell cat "/sdcard/Download/$name" > "$out" 2>/dev/null
    adb shell rm -f "/sdcard/Download/$name" >/dev/null 2>&1
    if grep -q '<node' "$out"; then return 0; fi
    echo "(attempt $i read no nodes from $name)" >> "$out.drv"
    sleep 0.25
  done
  echo "(dump failed)" > "$out"
  return 1
}

# ---------------------------------------------------------------- contact fixtures
# Inserted as provision.sh inserts Mom (content insert on raw_contacts, then data), their raw-contact ids kept in
# $ROW_DIR/people-fixtures.ids and deleted BY ID — never by account, so provision's Mom (qa / qa) is never touched.

FIX_IDS=""
_fix_file() { echo "$ROW_DIR/people-fixtures.ids"; }

# A new raw contact; prints its _id. With no arguments it is phone-only (NULL account name and type).
fx_raw() { # [account_name account_type]
  local before after
  before="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)"
  if [ -n "${1:-}" ]; then
    adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_name:s:"$1" --bind account_type:s:"$2" >/dev/null 2>&1
  else
    adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_name:n: --bind account_type:n: >/dev/null 2>&1
  fi
  after="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)"
  if [ -z "$after" ] || [ "$after" = "${before:-0}" ]; then echo "fx_raw: no new raw contact" >&2; return 1; fi
  echo "$after" >> "$(_fix_file)"
  echo "$after"
}

fx_data() { # raw mimetype data1 [data2]
  local raw="$1" mime="$2" d1="$3" d2="${4:-}"
  if [ -n "$d2" ]; then
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$raw --bind mimetype:s:$mime --bind data1:s:'$d1' --bind data2:i:$d2" >/dev/null 2>&1
  else
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$raw --bind mimetype:s:$mime --bind data1:s:'$d1'" >/dev/null 2>&1
  fi
}

fx_name() { fx_data "$1" vnd.android.cursor.item/name "$2"; }
fx_phone() { fx_data "$1" vnd.android.cursor.item/phone_v2 "$2" "${3:-2}"; }   # type 2 = mobile
fx_email() { fx_data "$1" vnd.android.cursor.item/email_v2 "$2" "${3:-1}"; }   # type 1 = home ("Personal")

# The contact a raw contact belongs to, and that contact's lookup key.
contact_of() { S content query --uri content://com.android.contacts/raw_contacts --projection contact_id --where "_id=$1" | grep -oE 'contact_id=[0-9]+' | cut -d= -f2; }
lookup_of() { S content query --uri content://com.android.contacts/contacts --projection lookup --where "_id=$1" | sed -n 's/.*lookup=//p' | head -1; }

# Every fixture this row inserted (and anything a step added to the ids file), deleted by id through the sync-adapter
# URI, so a raw contact under an account with no sync adapter is removed and not left marked deleted.
people_fixtures_down() {
  local f id
  f="$(_fix_file)"
  [ -f "$f" ] || return 0
  for id in $(sort -un "$f"); do
    adb shell "content delete --uri 'content://com.android.contacts/raw_contacts/$id?caller_is_syncadapter=true'" >/dev/null 2>&1
  done
  mv "$f" "$f.done-$(date +%s)"
}

raw_count() { S content query --uri content://com.android.contacts/raw_contacts --projection _id | grep -c '_id='; }

# Open People by component and wait for it.
open_people() { adb shell am start -W -n "$PEOPLE" "$@" >/dev/null 2>&1; sleep 2; }

# Tap the centre of a node, read from a fresh dump.
tap() { # resource-id
  local d="$ROW_DIR/.tap.xml"
  dump_ui "$d" || true
  tap_node "$d" "$1"
}

# Type into the focused field through the shell's keyboard (adb input text; %s is a space).
type_text() { adb shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')"; sleep 1; }
