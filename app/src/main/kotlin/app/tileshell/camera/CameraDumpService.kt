package app.tileshell.camera

import app.tileshell.diag.RingDumpService

/** The `:camera` process's diagnostics ring (phase 17; Decisions "processes"): `dumpsys activity service app.tileshell/.camera.CameraDumpService`. */
class CameraDumpService : RingDumpService() {
    override val processLabel = "camera"
}
