package app.tileshell.people

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import app.tileshell.apps.AppCatalog
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.Slot
import app.tileshell.tiles.SlotResolver

/**
 * What a card's action rows do, and Share (Decisions "People over the provider"; Trust (f)). Nothing here writes a
 * contact. Each action says where it went: `[people] action <call|text|mail|map> -> <component> <uri>` — the app it
 * went to as the shell's slots know it, or `resolver` when Android was left to choose.
 *
 * Every function returns the notice to show when the action could not start, null when it did.
 */
object PeopleActions {
    private const val RESOLVER = "resolver"

    fun run(context: Context, action: CardAction): PeopleNotice? = when (action.kind) {
        CardActionKind.CALL -> call(context, action.target)
        CardActionKind.TEXT -> text(context, listOf(action.target))
        CardActionKind.MAIL -> mail(context, action.target)
        CardActionKind.MAP -> map(context, action.target)
    }

    /** Call: through Telecom, so whichever dialer holds the role shows the call (phase 06's in-call UI once it does). */
    private fun call(context: Context, number: String): PeopleNotice? {
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return PeopleNotice("People can't place calls yet. Allow Phone to call from here.", NoticeAction.GRANT_CALLS)
        }
        val uri = Uri.fromParts("tel", number, null)
        return runCatching {
            context.getSystemService(TelecomManager::class.java).placeCall(uri, null)
            log("call", slotComponent(context, Slot.PHONE) ?: "telecom", "tel:$number")
            null
        }.getOrElse {
            Diagnostics.add("people", "action call failed: ${it.javaClass.simpleName}")
            PeopleNotice("People couldn't place that call.")
        }
    }

    /** Text: `ACTION_SENDTO smsto:` to the SMS role holder — one number from a card, several from "Text the group". */
    fun text(context: Context, numbers: List<String>): PeopleNotice? {
        val data = GroupRules.smsTo(numbers) ?: return PeopleNotice("There is no number to text.")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(data))
        val holder = Telephony.Sms.getDefaultSmsPackage(context)
        if (holder != null && Intent(intent).setPackage(holder).resolveActivity(context.packageManager) != null) intent.setPackage(holder)
        return start(context, intent, "text", if (intent.`package` != null) slotComponent(context, Slot.MESSAGING) ?: holder!! else RESOLVER, data, "No app here can send a text.")
    }

    /** Mail: `ACTION_SENDTO mailto:` to the Mail slot's app when one is assigned, else Android's own choice. */
    private fun mail(context: Context, address: String): PeopleNotice? {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address, "@+")))
        return start(context, intent, "mail", toSlot(context, intent, Slot.MAIL), "mailto:$address", "No mail app is set up on this phone.")
    }

    /** Address: `geo:0,0?q=` to the Maps slot's app, as Tess's directions go. */
    private fun map(context: Context, address: String): PeopleNotice? {
        val uri = "geo:0,0?q=${Uri.encode(address)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        return start(context, intent, "map", toSlot(context, intent, Slot.MAPS), uri, "No maps app is set up on this phone.")
    }

    /**
     * Share: `ACTION_SEND text/x-vcard` whose stream is the Contacts provider's OWN vCard URI for this contact, with a
     * read grant for that one URI. The shell writes no vCard, serves no provider and makes no file of its own: the
     * receiving app reads the provider's stream.
     */
    fun share(context: Context, card: ContactCard): PeopleNotice? {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, card.lookup)
        val send = Intent(Intent.ACTION_SEND)
            .setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The grant travels with the clip: exactly this URI, read only.
        send.clipData = ClipData.newRawUri(null, uri)
        return runCatching {
            context.startActivity(Intent.createChooser(send, null))
            Diagnostics.add("people", "share ${card.lookup}: $uri")
            null
        }.getOrElse {
            Diagnostics.add("people", "share failed: ${it.javaClass.simpleName}")
            PeopleNotice("People couldn't share that contact.")
        }
    }

    /**
     * Points [intent] at the slot's app when the slot is assigned, and returns what the line names. Inside the shell a
     * hand-off targets the app the user put in the slot, never a chooser (Decisions "Intent handlers and the chooser"):
     * the app's own handler for the intent when it has one; when it has none — K-9 answers no `mailto:` until an
     * account is set up — the slot's own activity is started with the intent, so the user lands in the app they chose.
     */
    private fun toSlot(context: Context, intent: Intent, slot: Slot): String {
        val entry = slotApp(context, slot) ?: return RESOLVER
        val pkg = entry.component.packageName
        if (Intent(intent).setPackage(pkg).resolveActivity(context.packageManager) != null) {
            intent.setPackage(pkg)
        } else {
            intent.component = entry.component
        }
        return entry.component.flattenToShortString()
    }

    private fun slotApp(context: Context, slot: Slot) =
        SlotResolver(context, AppCatalog.get(context)).resolve(slot, LayoutStore.get(context).layout.value.explicitSlots)

    private fun slotComponent(context: Context, slot: Slot): String? = slotApp(context, slot)?.component?.flattenToShortString()

    private fun start(context: Context, intent: Intent, kind: String, component: String, uri: String, none: String): PeopleNotice? {
        val started = runCatching { context.startActivity(intent); true }.getOrElse {
            // The slot's activity would not take the intent as it is: the app itself is opened instead.
            val target = intent.component
            target != null && runCatching {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Diagnostics.add("people", "action $kind: ${target.flattenToShortString()} has no handler for it; the app was opened")
                true
            }.getOrDefault(false)
        }
        if (!started) {
            Diagnostics.add("people", "action $kind failed: nothing to start")
            return PeopleNotice(none)
        }
        log(kind, component, uri)
        return null
    }

    private fun log(kind: String, component: String, uri: String) {
        Diagnostics.add("people", "action $kind -> $component $uri")
    }
}
