package app.tileshell.music.radio

import app.tileshell.music.MusicQueueStart

/**
 * What a station start plays (phase 20 build task 3, "the play path"; r3 D14 / D15). ONE rule for the tap
 * (`MusicPlayer.playStations`) and for a search's station match (the session), so neither can start a stream the
 * other would refuse:
 *
 *  - the network first ([StreamGate]): no network or a captive portal starts nothing, and the page shows the gate's
 *    text;
 *  - then the station asked for ([StationItem.plan]): when its address is refused nothing is played in its place —
 *    [StationItem.CANT_PLAY];
 *  - then the rest of the queue (`RadioFavourites.queueFor`): a favourite whose address is refused is left out, and
 *    the start is counted in what is left.
 *
 * Pure (`StationStartTest`).
 */
object StationStart {
    /**
     * @param stations the stations that play, in order — empty when nothing does
     * @param start where the queue starts, in [stations]
     * @param lines the `[music]` lines to write: the refusal's, and one for each station left out
     * @param refusal what the page (or Tess) says instead when nothing plays, else null
     */
    data class Plan(val stations: List<Station>, val start: Int, val lines: List<String>, val refusal: String?) {
        val plays: Boolean get() = refusal == null
    }

    fun plan(gate: StreamGate.Decision, queue: StationQueue, qaHost: String?): Plan {
        if (!gate.play) return Plan(emptyList(), 0, listOfNotNull(gate.line), gate.refusal ?: StationItem.CANT_PLAY)
        val plans = queue.stations.map { StationItem.plan(it, qaHost) }
        val asked = plans.getOrNull(queue.start)
        if (asked !is StationItem.Plan.Play) {
            return Plan(emptyList(), 0, listOfNotNull((asked as? StationItem.Plan.Refused)?.line), StationItem.CANT_PLAY)
        }
        val kept = plans.map { it is StationItem.Plan.Play }
        val start = MusicQueueStart.own(kept, queue.start) ?: return Plan(emptyList(), 0, emptyList(), StationItem.CANT_PLAY)
        return Plan(
            stations = queue.stations.filterIndexed { i, _ -> kept[i] },
            start = start,
            lines = plans.filterIsInstance<StationItem.Plan.Refused>().map { it.line },
            refusal = null,
        )
    }
}
