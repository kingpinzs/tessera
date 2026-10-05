package app.tileshell.video

import app.tileshell.diag.RingDumpService

/** The `:video` process's diagnostics ring (phase 17; Decisions "processes"): `dumpsys activity service app.tileshell/.video.VideoDumpService`. */
class VideoDumpService : RingDumpService() {
    override val processLabel = "video"
}
