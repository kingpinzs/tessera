package app.tileshell.testclient.qacapture

import android.os.Parcel
import android.os.Parcelable

/**
 * A Parcelable of THIS app's own class, for ledger L18-3's N4 device check (phase 18 l18_3 leg b): put into
 * `AlarmClock.EXTRA_DAYS` (an ArrayList) and sent to the shell's exported `AlarmApiActivity`, whose process has no
 * such class — unpacking it there throws `ClassNotFoundException`/`BadParcelableException`. The shell must read it
 * through a reader that cannot throw (SafeExtras) and refuse the request, not crash. Nothing but the harness sends it.
 */
class QaParcel() : Parcelable {
    constructor(parcel: Parcel) : this() { parcel.readString() }

    override fun writeToParcel(parcel: Parcel, flags: Int) { parcel.writeString("qa") }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<QaParcel> {
            override fun createFromParcel(parcel: Parcel): QaParcel = QaParcel(parcel)
            override fun newArray(size: Int): Array<QaParcel?> = arrayOfNulls(size)
        }
    }
}
