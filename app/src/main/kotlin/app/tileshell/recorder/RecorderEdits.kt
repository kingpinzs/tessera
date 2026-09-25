package app.tileshell.recorder

import android.content.ContentUris
import android.content.Context
import app.tileshell.diag.Diagnostics

/**
 * The edits the page makes to the shell's OWN recordings (T15-3): trim (U3), rename (U4) and delete (U5). Each
 * checks its rule first, is refused at MediaStore unless the row is the shell's own (the write guard re-reads
 * the owner), and keeps recordings.json in step with the row it changed. Nothing here ever touches another
 * app's file, so no Android consent dialog can appear. Every one runs off the main thread.
 */
object RecorderEdits {

    sealed interface Outcome {
        /** The recording now on the list: the new file a trim made. */
        data class Done(val id: Long) : Outcome
        data class Refused(val notice: String) : Outcome
        /** Nothing to do: a trim that keeps the whole recording. */
        data object Unchanged : Outcome
    }

    /** What the new file is called while it is made; it takes the original's name once the original is gone. */
    private const val WORKING_SUFFIX = " (trimming)"

    private const val NOT_OWN = "Only recordings made by Voice Recorder can be changed here."
    private const val FAILED = "The recording couldn't be trimmed."

    /**
     * Trim (U3, E16): a real cut. The kept part is written as a NEW pending file under a working name, published,
     * and given the original's markers that fall inside it (moved to the new clock); only then is the original
     * deleted and the new file given its name. A failure at any step before the delete leaves the original as it
     * was, and a working-named file is discarded — so at no point is there less than one whole recording.
     */
    fun trim(context: Context, recording: Recording, startMs: Long, endMs: Long): Outcome {
        val cut = when (val check = TrimRule.check(startMs, endMs, recording.durationMs)) {
            is TrimRule.Result.Refused -> {
                Diagnostics.add("recorder", "trim ${recording.id} refused: ${check.notice}")
                return Outcome.Refused(check.notice)
            }
            TrimRule.Result.Whole -> {
                Diagnostics.add("recorder", "trim ${recording.id}: the whole recording kept, nothing written")
                return Outcome.Unchanged
            }
            is TrimRule.Result.Cut -> check
        }
        if (!RecordingStore.guard(context, recording.id, "trim")) return Outcome.Refused(NOT_OWN)
        val working = recording.name + WORKING_SUFFIX
        val uri = RecordingStore.createPending(context, working) ?: return Outcome.Refused(FAILED)
        val result = try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                M4aFiles.cut(context, RecordingStore.uriOf(recording.id), cut.startMs, cut.endMs, pfd.fileDescriptor)
            }
        } catch (t: Throwable) {
            Diagnostics.add("recorder", "trim ${recording.id}: the cut failed: $t")
            null
        }
        if (result == null || result.frames == 0L) {
            RecordingStore.discard(context, uri)
            Diagnostics.add("recorder", "trim ${recording.id}: no audio in ${cut.startMs}-${cut.endMs}; nothing written")
            return Outcome.Refused(if (result == null) FAILED else "Choose a longer part of the recording to keep.")
        }
        if (!RecordingStore.publish(context, uri)) {
            RecordingStore.discard(context, uri)
            return Outcome.Refused(FAILED)
        }
        val newId = RecordingStore.row(context, uri)?.id ?: ContentUris.parseId(uri)
        val meta = RecordingMetaStore.get(context, recording.id)
        RecordingMetaStore.put(
            context, newId,
            RecordingMetaStore.Meta(
                recordedAtMs = meta?.recordedAtMs ?: recording.recordedAtMs,
                durationMs = result.durationMs,
                markers = TrimRule.shiftMarkers(meta?.markers.orEmpty(), cut.startMs, cut.endMs),
            ),
        )
        // The original goes only now, with the new file whole and published (E16: it is gone after Save).
        if (!RecordingStore.delete(context, recording.id)) {
            Diagnostics.add("recorder", "trim ${recording.id} -> $newId ms=${result.durationMs}: the original could not be deleted; both stay")
            return Outcome.Done(newId)
        }
        RecordingMetaStore.remove(context, recording.id)
        val named = RecordingStore.rename(context, newId, recording.name)
        Diagnostics.add("recorder", "trim ${recording.name} ${cut.startMs}-${cut.endMs} -> $newId ms=${result.durationMs} name=${named ?: RecordingNames.displayName(working)}")
        return Outcome.Done(newId)
    }

    /**
     * Rename (U4): `_display_name` becomes `<name>.m4a`; a clash is MediaProvider's " (1)". [name] is checked
     * again here, so a name the dialog's check refused never reaches MediaStore by another route.
     * @return the display name MediaStore now holds, or null when refused or failed
     */
    fun rename(context: Context, recording: Recording, name: String): String? {
        val checked = when (val check = RenameRule.check(name)) {
            is RenameRule.Result.Ok -> check.name
            is RenameRule.Result.Refused -> {
                Diagnostics.add("recorder", "rename ${recording.id} refused: ${check.notice}")
                return null
            }
        }
        val now = RecordingStore.rename(context, recording.id, checked) ?: return null
        Diagnostics.add("recorder", "rename ${recording.id} -> $now")
        return now
    }

    /** Delete (U5, after the dialog's confirmation): the row, the file and the recording's markers go together. */
    fun delete(context: Context, recording: Recording): Boolean {
        val ok = RecordingStore.delete(context, recording.id)
        if (ok) {
            RecordingMetaStore.remove(context, recording.id)
            Diagnostics.add("recorder", "delete ${recording.id} (${recording.name})")
        }
        return ok
    }
}
