#!/usr/bin/env bash
# Phase 18 E18: FileOps on the JVM (r3 D7) — `--tests '*FileOps*'` through the floor's gate (gradle.rc = 0 AND the
# TEST-*FileOps*.xml reports with 0 failures / errors / skipped), the reports naming at least the doc's list. A JVM-only
# row: no device command is run (rowsb.sh jvm_row_begin), so it can run while another driver has the emulator.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"

keep_earlier_run E18
jvm_row_begin E18 "FileOps on the JVM (r3 D7): the write layer's cases through the floor's gate"

jvm_gate '*FileOps*' '*FileOps*' \
  "temp-and-rename - a copy is written to the journalled part file and renamed when it is whole" \
  "cancel leaving no temp - a cancelled copy leaves no file and no part file in the destination" \
  "conflict replace - the copy takes the place of the file that was there" \
  "conflict keep both - the copy is name (2) ext, then (3)" \
  "conflict skip - nothing is written and the file that was there stays" \
  "a cross-volume move failing after the copy leaves both files, never neither" \
  "the same-millisecond bin pair - two bin files, neither overwritten, each restoring to its own path" \
  "the 255-byte name - the bin name fits 255 bytes, keeps the extension, and restore gives the full name back" \
  "an index that cannot be written - the bin path is a FILE, so the delete fails and the file stays" \
  "an index that cannot be written - the index itself cannot be replaced, so the delete fails and the file stays" \
  "index missing - the bin lists its files by bin name, says rebuilt, and restores to Download Restored" \
  "index unreadable - the bin lists its files by bin name and says rebuilt, and the next delete still works" \
  "a record whose file is gone is dropped on the next read" \
  "the journal names the running operation's temp and its volume, and is empty when the operation ends" \
  "the sweep removes only journalled temps - a part file the journal does not name stays" \
  "the sweep never removes a bin path - not the bin, not a file in it, however the journal names them" \
  "zip guard - dot-dot and absolute names are refused while the other entry extracts" \
  "zip guard - the bomb is stopped at its declared total plus 1 MB and leaves no temp folder" \
  "zip guard - the huge zip is refused before any write when its declared size does not fit" \
  "zip guard - the encrypted bit is read and nothing is written" \
  "zip guard - a symlink entry is written as a regular file holding the link text"

# The five report files the pattern names, each present (a class that ran no test would otherwise hide in the total).
# (FileOpsReplaceTest, FileOpsShellDirTest, FileOpsBinRecordTest and FileOpsGuardsTest came with the review's fixes.)
for c in FileOpsTest FileOpsBinTest FileOpsJournalTest FileOpsRunTest FileOpsZipTest FileOpsReplaceTest FileOpsShellDirTest FileOpsBinRecordTest FileOpsGuardsTest; do
  f="$ROW_DIR/test-reports/TEST-app.tileshell.files.$c.xml"
  assert_eq "report TEST-app.tileshell.files.$c.xml: present, failures / errors / skipped" "0 0 0" \
    "$(python3 -c "import sys, xml.etree.ElementTree as ET; s = ET.parse(sys.argv[1]).getroot(); print(s.get('failures'), s.get('errors'), s.get('skipped'))" "$f" 2>/dev/null)"
  record "$c: tests in its report" "$(python3 -c "import sys, xml.etree.ElementTree as ET; print(ET.parse(sys.argv[1]).getroot().get('tests'))" "$f" 2>/dev/null)"
done
# The zip guards ran on make_zips.py's fixtures: the class runs the script itself and compares its md5 lines.
assert_contains "FileOpsZipTest makes its fixtures with scripts/make_zips.py (the class's own source)" "make_zips.py" \
  "$(grep -c 'docs/plan/qa/phase-18/scripts/make_zips.py' "$REPO/app/src/test/kotlin/app/tileshell/files/FileOpsZipTest.kt") make_zips.py reference(s)"

jvm_row_end
