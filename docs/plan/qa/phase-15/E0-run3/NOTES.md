# E0 run 3: 49 passed, 4 failed — the open cross-phase defect (2) and two driver faults (fixed)

- "the face moved to Auxio's tile", "Auxio paused from its tile": the open MusicFeed / TileNotificationListener key
  collision (E0-run2/DEFECT.md), unchanged.
- "the recorder session is alive (paused)": PLAYING PLAYING. The UI pause tap came after a dump; by then the 3-s file had
  ended and the tap played it again. The driver now pauses through the media session right after the PLAYING read.
- "nothing was published for it": the matching publish was the PAUSED shell Music session's own idle face on its own
  key (ring_rec.txt 09:53:26.921: "[music] idle app.tileshell title=Bloom"). With the recording excluded, MusicFeed
  re-picks among the paused sessions, which is phase 10's pick(). The recording itself routed to none. The driver now
  asserts that no feed line names the recording's title.
- Confirmed fixed in this run: Voice Recorder's page request (the take started from page=record) and the deleted
  timer's notification (restore: stores empty, 0 pending); the listener's positive control passes (app.tileshell >= 1).
