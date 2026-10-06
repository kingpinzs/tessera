#!/usr/bin/env bash
# Phase 18 E20, the Edge-cases bullets executed (r3 V17): one function edge_<ID> per AVD sub-step, each its own row
# (folder EDGE_<ID>, its own baseline, its own fixtures and volume, its own restore), runnable alone:
#
#   edge_files.sh <ID> [<ID>…]     the named sub-steps
#   edge_files.sh --list           the sub-steps' names
#   edge_files.sh                  every sub-step, in ALL_IDS's order
#
# THIS IS THE SKELETON (build task 12, written before the Files UI exists). Every sub-step's set-up and tear-down are
# real and wired — the baseline restore, files_up with the pieces the bullet needs, pubvol_up, and their restores,
# whose own assertions run — and every BODY is one FAIL: "NOT WRITTEN: needs the Files UI". Nothing here passes a
# bullet: a sub-step's row ends "… 1 failed" until its body is written. scripts/edge_index.tsv maps each bullet of
# "Edge cases" to its sub-step here (or to the row, P row or JVM test that covers it).
#
#   TENK          10,000 files in one folder: the first page < 2 s, a sort by Date inside 5 s, first = newest, last =
#                 oldest, no ANR                                              files_up tenk
#   NAMES         unicode, emoji, a 255-byte name; the hidden-files setting (.hidden.txt); a case-only rename on FAT
#                                                                             files_up + pubvol_up
#   GONE          a folder deleted under an open listing; a file deleted between list and tap     files_up
#   BACK          Back after a breadcrumb jump, ↑, selection mode, the picker (Y3, r3 D12)        files_up
#   FULL_VOLUME   copy big.bin onto a full volume: failed, "not enough space", no temp, md5 kept  files_up big
#   UNMOUNT       the volume pulled mid-copy and mid-extract: "storage removed", no partial file, the sweep on remount
#                                                                             files_up paced big zips + pubvol_up
#   REVOKE        the grant revoked during a big.bin copy: the ungranted state after a relaunch, never a crash
#                                                                             files_up paced big
#   XMOVE         a move from /sdcard to the public volume: md5 equal, the source gone             files_up + pubvol_up
#   NOMEDIA       hidden/ listed in Files, absent from the images and audio collections            files_up
#   STALE_PHOTO   a file opened in Photos, then deleted in Files: the stale row's placeholder      files_up media
#   INTERRUPT     screen off, an incoming call and a reboot during a copy: finishes or fails cleanly, the sweep, the bin
#                 untouched                                                   files_up paced big
#   TILE_COLD     the pinned Files tile, cold: Recent with the pane open                           (baseline only)
#   PROPS         Properties' values for b.bin and sub, read from the fixture table                files_up
#   BIN           the Recycle Bin's index cases (missing, stale record, folder-now-a-file, same names, no record)
#                                                                             files_up
#   BIN_PUBVOL    delete and Restore on the public volume (E20's list)                             files_up + pubvol_up
#   KEEP_BOTH     a conflict answered "keep both" leaves `b (2).bin` (E20's list; r3 D6)           files_up
#   ZIP           zip64 (70,000 entries inside 5 s), CP437 names, a zip in the bin                 files_up zips
#   LIVENESS      a reboot and a force-stop leave the grant, the checklist row, the bins' indexes, Recent and an empty
#                 journal intact (N-01)                                       files_up
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
ALL_IDS="TENK NAMES GONE BACK FULL_VOLUME UNMOUNT REVOKE XMOVE NOMEDIA STALE_PHOTO INTERRUPT TILE_COLD PROPS BIN BIN_PUBVOL KEEP_BOTH ZIP LIVENESS"
FAILED_ROWS=""
NOT_WRITTEN="NOT WRITTEN: needs the Files UI"

# A sub-step's frame. edge_begin: the earlier run's folder kept, the row begun, the baseline restored (C-3), then the
# fixtures and the volume the bullet needs — a set-up that fails ends the sub-step there (its FAIL is in the log).
# edge_end: the volume and the fixtures given back (their snapshot assertions), every ring saved, Start, the verdict.
EDGE_PUBVOL=""
edge_begin() { # ID description pubvol|nopubvol|nofiles [files_up arguments…]
  local id="$1" what="$2" vol="$3"
  shift 3
  keep_earlier_run "EDGE_$id"
  row_begin "EDGE_$id" "edge: $what"
  D="$ROW_DIR"
  EDGE_PUBVOL=""; EDGE_FILES=""
  baseline_start
  if [ "$vol" != nofiles ]; then
    files_up "$@" || { _verdict FAIL "EDGE_$id set-up: files_up $*" "failed (above)"; return 1; }
    EDGE_FILES=1
  fi
  if [ "$vol" = pubvol ]; then
    EDGE_PUBVOL=1
    pubvol_up >/dev/null || { _verdict FAIL "EDGE_$id set-up: pubvol_up" "failed (above)"; return 1; }
  fi
  ensure_start
}
edge_end() {
  [ -n "$EDGE_PUBVOL" ] && pubvol_down
  [ -n "${FX_SNAPPED:-}" ] && files_down
  rings_save
  ensure_start
  row_end || FAILED_ROWS="$FAILED_ROWS $ROW"
}
# The whole of a sub-step whose body is not written: the frame, one FAIL, the frame's end.
edge_skeleton() { # ID description pubvol|nopubvol|nofiles [files_up arguments…]
  if edge_begin "$@"; then
    _verdict FAIL "EDGE_$1 body" "$NOT_WRITTEN"
  fi
  edge_end
}

edge_TENK()        { edge_skeleton TENK "10,000 files in one folder — the first page, the sort by Date, no ANR" nopubvol tenk; }
edge_NAMES()       { edge_skeleton NAMES "names — unicode, emoji, 255 bytes, leading dots and the hidden-files setting, a case-only rename on FAT" pubvol; }
edge_GONE()        { edge_skeleton GONE "a folder deleted under an open listing; a file deleted between list and tap" nopubvol; }
edge_BACK()        { edge_skeleton BACK "Back after a breadcrumb jump, ↑, selection mode, and Back in the picker" nopubvol; }
edge_FULL_VOLUME() { edge_skeleton FULL_VOLUME "copy big.bin onto a full volume (fill_volume 1048576 … unfill_volume)" nopubvol big; }
edge_UNMOUNT()     { edge_skeleton UNMOUNT "the volume unmounted mid-copy and mid-extract; the sweep when it mounts again" pubvol paced big zips; }
edge_REVOKE()      { edge_skeleton REVOKE "the grant revoked during a big.bin copy (appops … default; restored to allow)" nopubvol paced big; }
edge_XMOVE()       { edge_skeleton XMOVE "a move from /sdcard to the sub-step's own public volume" pubvol; }
edge_NOMEDIA()     { edge_skeleton NOMEDIA ".nomedia folders — listed in Files, absent from the images and audio collections" nopubvol; }
edge_STALE_PHOTO() { edge_skeleton STALE_PHOTO "a file opened in Photos, then deleted in Files" nopubvol media; }
edge_INTERRUPT()   { edge_skeleton INTERRUPT "screen off, an incoming call and a reboot during a copy" nopubvol paced big; }
edge_TILE_COLD()   { edge_skeleton TILE_COLD "the pinned Files tile on a cold start: Recent with the pane open" nofiles; }
edge_PROPS()       { edge_skeleton PROPS "Properties' values for b.bin and sub, from the fixture table" nopubvol; }
edge_BIN()         { edge_skeleton BIN "the Recycle Bin's index cases" nopubvol; }
edge_BIN_PUBVOL()  { edge_skeleton BIN_PUBVOL "delete and Restore on the public volume" pubvol; }
edge_KEEP_BOTH()   { edge_skeleton KEEP_BOTH "a conflict answered keep both leaves b (2).bin" nopubvol; }
edge_ZIP()         { edge_skeleton ZIP "zip64 (70,000 entries), CP437 names, a zip in the bin" nopubvol zips; }
edge_LIVENESS()    { edge_skeleton LIVENESS "liveness: a reboot and a force-stop (N-01)" nopubvol; }

if [ "${1:-}" = --list ]; then printf '%s\n' $ALL_IDS; exit 0; fi
IDS="${*:-$ALL_IDS}"
for id in $IDS; do
  case " $ALL_IDS " in
    *" $id "*) "edge_$id" ;;
    *) echo "edge_files.sh: no sub-step named [$id] (edge_files.sh --list)" >&2; exit 2 ;;
  esac
done
if [ -n "$FAILED_ROWS" ]; then echo "edge_files.sh: failed:$FAILED_ROWS"; exit 1; fi
echo "edge_files.sh: every named sub-step passed"
