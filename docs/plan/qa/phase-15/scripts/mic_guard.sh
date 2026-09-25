#!/usr/bin/env bash
# The owner's no-microphone rule (2026-09-25): no row may open audio capture. The AVDs run with -allow-host-audio, so a
# capture in the guest opens one on the host. Sourced after lib.sh by the rows run under that rule:
#   mic_guard_begin   right after row_begin: snapshots dumpsys audio's "recording activity" events log (every
#                     AudioRecord start / update / stop / release of ANY package on the device);
#   mic_guard_end     just before row_end: ASSERTS the log gained no event during the row, so any capture on the device
#                     while the row ran fails the row.
# The log is a bounded ring and a reboot empties it, so the check is "no line in the end snapshot that the begin snapshot
# lacked", never a count. Its positive control is the log's own header line in the same dump.
rec_events() {
  adb shell dumpsys audio 2>/dev/null | tr -d '\r' \
    | grep -E '^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}:[0-9]{3} rec [a-z]+ riid:'
}
mic_guard_begin() {
  rec_events > "$ROW_DIR/mic_events_begin.txt"
  note "no-microphone rule: $(wc -l < "$ROW_DIR/mic_events_begin.txt") recording-activity event(s) in dumpsys audio at the row's start (mic_events_begin.txt)"
}
mic_guard_end() {
  local dump new
  dump="$(adb shell dumpsys audio 2>/dev/null | tr -d '\r')"
  assert_contains "no-microphone rule: dumpsys audio's recording-activity log is read (positive control: its header)" \
    "Events log: recording activity" "$dump"
  printf '%s\n' "$dump" | grep -E '^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}:[0-9]{3} rec [a-z]+ riid:' > "$ROW_DIR/mic_events_end.txt"
  new="$(grep -vxF -f "$ROW_DIR/mic_events_begin.txt" "$ROW_DIR/mic_events_end.txt")"
  assert_eq "no-microphone rule: no audio capture on the device during the row (the recording-activity log gained no event)" "" "$new"
}
