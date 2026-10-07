#!/usr/bin/env bash
# The provider's OWN serving check, reached as the shell's uid (run-as): the framework lets the owner through, so the
# refusal here is FilesProvider's — the private path is refused with its line, a shared file reads.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg c-provider
PRIVATE=/data/data/app.tileshell/files/files-recent.json
rd() { adb shell "run-as app.tileshell content read --uri $1" 2>&1 | tr -d '\r'; }
M=$(ring_mark); rd "content://app.tileshell.files/root$PRIVATE" > "$ROW_DIR/10-sameuid-private.out"; sleep 1
head -c 400 "$ROW_DIR/10-sameuid-private.out"; echo
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]"
record "same uid, private path: output" "$(head -c 200 "$ROW_DIR/10-sameuid-private.out" | tr '\n' ' ')"
record "same uid, private path: refusal lines in the slice" "$(echo "$SL" | grep -c 'share refused: outside shared storage')"
M=$(ring_mark); rd "content://app.tileshell.files/root/storage/emulated/0/../../data/data/app.tileshell/files/files-recent.json" > "$ROW_DIR/11-sameuid-traversal.out"; sleep 1
record "same uid, traversal: output" "$(head -c 200 "$ROW_DIR/11-sameuid-traversal.out" | tr '\n' ' ')"
record "same uid, traversal: refusal lines in the slice" "$(ring_since $M | grep -c 'share refused: outside shared storage')"
rd "content://app.tileshell.files/root/storage/emulated/0/QA-Files/a.txt" > "$ROW_DIR/12-sameuid-shared.out"
record "same uid, a shared file: output" "$(head -c 200 "$ROW_DIR/12-sameuid-shared.out" | tr '\n' ' ')"
leg_end
