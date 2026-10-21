package app.tileshell.music

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.tileshell.diag.Diagnostics

/**
 * The player Music's session is built on (ledger L18-5; the adversarial review's pass 3, P3-1): every index a controller
 * can send is weighed by [MusicIndexRule] against the list as it is at that moment, and one the list does not have is
 * refused here, never handed on, with a `[music]` line ([MusicIndexRule.line]). What passes is handed on unchanged, with
 * its own arguments — the session's callbacks have already decided the items (L18-1, L18-4).
 *
 * Every index-taking command of Media3's [Player] is here (`BoundedIndexPlayerTest` holds the list to Media3's): the two
 * seeks, the single and ranged removes, replaces and moves, and the indexed adds. Two need nothing: the start of
 * `setMediaItems(items, index, position)`, which the session hands the player only from [MusicQueueStart] (L18-4), and
 * `getMediaItemAt`, a read the session makes with indices of its own.
 *
 * Only the session sees this player. The crossfade drives [KnownDurationPlayer] and the stream watch the ExoPlayer under
 * it, each with indices of its own, exactly as before.
 */
class BoundedIndexPlayer(
    player: Player,
    /** The uid of the controller whose command the session is handling (`MediaSession.controllerForCurrentRequest`), or null. */
    private val caller: () -> Int?,
) : ForwardingPlayer(player) {

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) =
        if (MusicIndexRule.item(mediaItemIndex, mediaItemCount)) super.seekTo(mediaItemIndex, positionMs) else refused("seekTo($mediaItemIndex, $positionMs)")

    override fun seekToDefaultPosition(mediaItemIndex: Int) =
        if (MusicIndexRule.item(mediaItemIndex, mediaItemCount)) super.seekToDefaultPosition(mediaItemIndex) else refused("seekToDefaultPosition($mediaItemIndex)")

    override fun removeMediaItem(index: Int) =
        if (MusicIndexRule.item(index, mediaItemCount)) super.removeMediaItem(index) else refused("removeMediaItem($index)")

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) =
        if (MusicIndexRule.item(index, mediaItemCount)) super.replaceMediaItem(index, mediaItem) else refused("replaceMediaItem($index)")

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) =
        if (MusicIndexRule.item(currentIndex, mediaItemCount) && MusicIndexRule.place(newIndex)) super.moveMediaItem(currentIndex, newIndex)
        else refused("moveMediaItem($currentIndex, $newIndex)")

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) =
        if (MusicIndexRule.range(fromIndex, toIndex, mediaItemCount)) super.removeMediaItems(fromIndex, toIndex) else refused("removeMediaItems($fromIndex, $toIndex)")

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) =
        if (MusicIndexRule.range(fromIndex, toIndex, mediaItemCount)) super.replaceMediaItems(fromIndex, toIndex, mediaItems)
        else refused("replaceMediaItems($fromIndex, $toIndex)")

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) =
        if (MusicIndexRule.range(fromIndex, toIndex, mediaItemCount) && MusicIndexRule.place(newIndex)) super.moveMediaItems(fromIndex, toIndex, newIndex)
        else refused("moveMediaItems($fromIndex, $toIndex, $newIndex)")

    override fun addMediaItem(index: Int, mediaItem: MediaItem) =
        if (MusicIndexRule.place(index)) super.addMediaItem(index, mediaItem) else refused("addMediaItem($index)")

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) =
        if (MusicIndexRule.place(index)) super.addMediaItems(index, mediaItems) else refused("addMediaItems($index)")

    private fun refused(call: String) = Diagnostics.add("music", MusicIndexRule.line(caller(), call, mediaItemCount))
}
