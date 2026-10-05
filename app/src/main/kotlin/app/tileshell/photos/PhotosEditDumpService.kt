package app.tileshell.photos

import app.tileshell.diag.RingDumpService

/** The `:photosedit` process's diagnostics ring (phase 17; r3 D8): `dumpsys activity service app.tileshell/.photos.PhotosEditDumpService`. */
class PhotosEditDumpService : RingDumpService() {
    override val processLabel = "photosedit"
}
