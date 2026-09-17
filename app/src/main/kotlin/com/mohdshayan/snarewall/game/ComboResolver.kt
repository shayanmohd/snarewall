package com.mohdshayan.snarewall.game

/** A placed trap. Wall traps sit on a wall tile and act on [target]. */
class Trap(val kind: TrapKind, val tile: Int, var facing: Int, var upgraded: Boolean = false) {
    var cooldown = 0f
    var spent = 0

    /** The floor tile this trap acts on: its own tile for floor traps, the faced tile for wall traps. */
    val target: Int get() = if (kind.wallMounted) Grid.step(tile, facing) else tile

    /** Where a pusher throws an enemy: one tile past the target, in the facing direction. */
    val shoveTile: Int get() = if (kind == TrapKind.PUSHER && target >= 0) Grid.step(target, facing) else -1

    /** Tiles linked to this trap by a combo (for example oil tiles that carry this ember grate's fire). */
    val linkedTiles = IntArray(4)
    var linkedCount = 0

    /** The deadfall a pusher throws onto, if the combo is live. */
    var linkedDeadfall: Trap? = null
    var flash = 0f
}

/** A drawn link mark between two traps whose combo is live. */
data class ComboLink(val kind: ComboKind, val a: Int, val b: Int)

/**
 * Finds live combos. Each rule is spatial, so a link appears the moment two traps are placed to work:
 *  pusher into deadfall: the pusher throws onto the deadfall's target tile;
 *  oil into ember: an oil slick next to an ember grate carries its fire;
 *  frost into shatter hammer: the hammer strikes a frost vent tile or one next to it;
 *  snare net onto spike plate: a net next to a spike plate also holds enemies on the spikes.
 */
object ComboResolver {

    fun resolve(traps: List<Trap>): List<ComboLink> {
        val links = ArrayList<ComboLink>()
        for (t in traps) {
            t.linkedCount = 0
            t.linkedDeadfall = null
        }
        for (a in traps) {
            when (a.kind) {
                TrapKind.PUSHER -> {
                    val shove = a.shoveTile
                    if (shove < 0) continue
                    val d = traps.firstOrNull { it.kind == TrapKind.DEADFALL && it.target == shove }
                    if (d != null) {
                        a.linkedDeadfall = d
                        links += ComboLink(ComboKind.PUSH_DEADFALL, a.tile, d.tile)
                    }
                }
                TrapKind.EMBER -> for (o in traps) {
                    if (o.kind == TrapKind.OIL && Grid.manhattan(o.tile, a.tile) == 1) {
                        addLinked(a, o.tile)
                        links += ComboLink(ComboKind.OIL_EMBER, o.tile, a.tile)
                    }
                }
                TrapKind.HAMMER -> {
                    val tgt = a.target
                    if (tgt < 0) continue
                    val f = traps.firstOrNull { it.kind == TrapKind.FROST && Grid.manhattan(it.tile, tgt) <= 1 }
                    if (f != null) links += ComboLink(ComboKind.FROST_HAMMER, f.tile, a.tile)
                }
                TrapKind.SNARE -> for (s in traps) {
                    if (s.kind == TrapKind.SPIKE && Grid.manhattan(s.tile, a.tile) == 1) {
                        addLinked(a, s.tile)
                        links += ComboLink(ComboKind.SNARE_SPIKE, a.tile, s.tile)
                    }
                }
                else -> Unit
            }
        }
        return links
    }

    private fun addLinked(t: Trap, tile: Int) {
        if (t.linkedCount < t.linkedTiles.size) t.linkedTiles[t.linkedCount++] = tile
    }
}
