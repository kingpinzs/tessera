# E0 run 2: 46 passed, 5 failed — one cross-phase defect (open; Jeremy's call), three driver faults (fixed)

## Open: another app's now-playing face is wiped by its own notification (phases 01 and 10's parts)
Auxio played Zoo Station ("Auxio is playing" PASS) and its pinned tile grew, but it showed no face and no transport
(start_auxio.tiles.txt: `controls=no texts=Auxio`). The ring (collision.txt):

    08:55:29.908 [engine] publish pkg:org.oxycblt.auxio faces=0 front=true source=music:org.oxycblt.auxio   <- MusicFeed (phase 10)
    08:55:29.908 [music] now playing org.oxycblt.auxio title=Zoo Station front=true flip=false grow=true
    08:55:30.106 [notif] posted org.oxycblt.auxio key=0|org.oxycblt.auxio|41120|...                      <- Auxio's media notification
    08:55:30.108 [engine] publish pkg:org.oxycblt.auxio faces=0 front=false source=null                    <- TileNotificationListener (phase 01)

Two producers publish under the same key, `LiveTileEngine.packageKey(pkg)`: MusicFeed (the now-playing face, phase 10
Q4) and TileNotificationListener (notification previews, phase 01). The last write wins, and every media app posts a
notification as it starts playing, so the face is gone within ~200 ms. Phase 15's task 0 moved only the shell's OWN apps
onto component keys, which is why the shell's Music face survives (E0 step 1 passes). Neither phase doc settles which of
the two a tile shows. Phase 01 has "preview content = the API queue if the app uses it, else notifications"; phase 10 Q4
has "the face belongs to the tile of the app that owns the session". Phase 10's own E11 would fail the same way; its
tile rows have never been driven on an emulator, and E0 is their first driver. Brought to Jeremy, not fixed here.

## Driver faults, fixed in e0.sh
- The listener's positive control read `grep '^badges='`, but the dump indents the line. The count was actually
  there: `badges={com.android.systemui=1, app.tileshell=2}` (listener.txt).
- The recorder step: the 3-s other.m4a ended during the Start dump (`PLAYING STOPPED`). The driver now pauses it as
  soon as it plays, so the session stays alive for the dump, and reads "nothing grew / nothing published" from the ring.
- The capture before the recorder step still had Auxio playing and grown (its tile control was missing, above). The
  recorder's audio focus then paused Auxio and its tile shrank: the only row that differed. The driver now pauses Auxio
  through its session when the tile could not.
