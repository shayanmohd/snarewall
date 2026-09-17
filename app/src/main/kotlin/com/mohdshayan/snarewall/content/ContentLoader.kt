package com.mohdshayan.snarewall.content

import android.content.Context
import com.mohdshayan.snarewall.game.GameContent

/** Reads the bundled level and table JSON once. Everything ships in the APK; nothing is fetched. */
class ContentLoader(private val context: Context) {

    @Volatile
    private var cached: GameContent? = null

    fun load(): GameContent = cached ?: synchronized(this) {
        cached ?: parse().also { cached = it }
    }

    private fun text(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }

    private fun parse(): GameContent {
        val levels = context.assets.list("levels").orEmpty().filter { it.endsWith(".json") }.sorted()
            .map { text("levels/$it") }
        return GameContent.parse(
            text("tables/traps.json"),
            text("tables/enemies.json"),
            text("tables/combos.json"),
            text("tables/regions.json"),
            text("tables/daily.json"),
            levels,
        )
    }

    fun licence(name: String): String = text("licenses/$name")
}
