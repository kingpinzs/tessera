package app.tileshell.files

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi

/**
 * Files' copy / move / extract service (phase 18, r3 D8): a foreground service of type `dataSync` in the main
 * process, not exported, started only from a visible [FilesActivity]. Build task 1 declares it so the manifest and
 * the exported surface are settled; the operations themselves arrive with the build task that owns them.
 *
 * The platform limits how long a `dataSync` service may run and calls [onTimeout] at the limit; a service that
 * outlives it is killed, so it stops itself.
 */
class FileOpsService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        stopSelf()
    }
}
