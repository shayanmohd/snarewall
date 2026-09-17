package com.mohdshayan.snarewall.ui.board

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mohdshayan.snarewall.audio.Sfx
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.game.DistanceField
import com.mohdshayan.snarewall.game.Route
import com.mohdshayan.snarewall.game.DailyGenerator
import com.mohdshayan.snarewall.game.Difficulty
import com.mohdshayan.snarewall.game.EnemyKind
import com.mohdshayan.snarewall.game.GameContent
import com.mohdshayan.snarewall.game.Grid
import com.mohdshayan.snarewall.game.LevelDef
import com.mohdshayan.snarewall.game.Par
import com.mohdshayan.snarewall.game.Phase
import com.mohdshayan.snarewall.game.Placement
import com.mohdshayan.snarewall.game.RunSpec
import com.mohdshayan.snarewall.game.SaveCodec
import com.mohdshayan.snarewall.game.Scoring
import com.mohdshayan.snarewall.game.Sim
import com.mohdshayan.snarewall.game.StepClock
import com.mohdshayan.snarewall.game.TrapKind
import com.mohdshayan.snarewall.ui.nav.Board
import com.mohdshayan.snarewall.ui.nav.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A short line over the board: an inline error, a success, or a resume notice. */
data class BoardMessage(val text: String, val warning: Boolean, val id: Long)

/**
 * Owns the running [Sim] across rotation. The UI frame loop calls [frame]; everything else is a
 * user action. Saves the run at wave boundaries and after build-phase placements, so a process kill
 * mid-wave relaunches at the start of that wave.
 */
class BoardViewModel(app: Application, val args: Board, savedState: SavedStateHandle) : AndroidViewModel(app) {

    private val repo = ServiceLocator.progress
    private val prefs = ServiceLocator.appPrefs
    val sfx: Sfx = ServiceLocator.sfx

    var content: GameContent? = null
        private set
    var level: LevelDef? = null
        private set
    private val launch = RunLaunch(args, savedState)
    val dateKey: String? = launch.dateKey
    var region: String = "chalk"
        private set

    /** Null while loading. */
    var sim by mutableStateOf<Sim?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    // HUD. Setting an equal value does not recompose.
    var hearts by mutableIntStateOf(0)
    var coin by mutableIntStateOf(0)
    var wave by mutableIntStateOf(1)
    var phase by mutableStateOf(Phase.BUILD)
    var routeSteps by mutableIntStateOf(0)
    var undoAvailable by mutableStateOf(false)

    /** Bumped every simulated frame; the canvas reads it in its draw block to redraw without recomposing. */
    var frameTick by mutableLongStateOf(0L)
        private set

    var speed by mutableIntStateOf(1)
    var paused by mutableStateOf(false)
    var pauseSheetOpen by mutableStateOf(false)
    var tool by mutableStateOf<TrapKind?>(null)
    var selectedTile by mutableIntStateOf(-1)
    var message by mutableStateOf<BoardMessage?>(null)
    var coachStep by mutableIntStateOf(-1)
    var leftHanded by mutableStateOf(false)
    var showStepChip by mutableStateOf(true)
    var haptics by mutableStateOf(true)
    var firstRouteDraw by mutableStateOf(false)
    var finished by mutableStateOf<Result?>(null)
        private set

    // Ghost preview.
    var ghostTile by mutableIntStateOf(-1)
        private set
    var ghostError by mutableStateOf<Placement.Error?>(null)
        private set
    var ghostDelta by mutableIntStateOf(0)
        private set
    var ghostIsWall by mutableStateOf(false)
        private set

    /** Routes: committed paths per gate, and the ghost's. Buffers are reused. */
    val committedPath = Array(2) { IntArray(Grid.N) }
    val committedCount = IntArray(2)
    val ghostPath = Array(2) { IntArray(Grid.N) }
    val ghostCount = IntArray(2)
    var routeVersion by mutableIntStateOf(0)
        private set

    /** The route as it was before the last change, so the canvas can fade it out while the new one shows. */
    val previousPath = Array(2) { IntArray(Grid.N) }
    val previousCount = IntArray(2)
    var routeChangeSerial by mutableIntStateOf(0)
        private set

    /** The tile of the last wall placed, and a serial the canvas keys its settle animation on. */
    var settleTile by mutableIntStateOf(-1)
        private set
    var settleSerial by mutableIntStateOf(0)
        private set
    /** The ghost's distance field, reused for every preview. Null until a run starts. */
    private var ghostField: DistanceField? = null
    private var wallSigA = -1L
    private var wallSigB = -1L

    /** Per-tile flash timers for combo links, in seconds. */
    val comboFlash = FloatArray(Grid.N)

    private val clock = StepClock()

    /** Where rendering sits between the last tick and the next, for smooth enemy motion. */
    val renderAlpha: Float get() = if (sim?.phase == Phase.WAVE && !paused && !pauseSheetOpen) clock.alpha else 1f
    private val event = IntArray(3)
    private var messageSerial = 0L
    private var messageJob: Job? = null
    private var hapticRequest = 0
    var hapticTick by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val s = prefs.current()
        speed = s.defaultSpeed
        leftHanded = s.leftHandedTray
        showStepChip = s.showStepChip
        haptics = s.haptics
        sfx.volume = s.sfxVolume
        val c = try {
            withContext(Dispatchers.IO) { ServiceLocator.content.load() }
        } catch (e: Exception) {
            loadError = "Levels could not be read."
            return
        }
        content = c
        val difficulty = Difficulty.byId(args.difficulty)
        val spec = try {
            if (args.daily) {
                val map = DailyGenerator.generate(dateKey!!)
                region = map.region
                DailyGenerator.spec(map, c)
            } else {
                val lv = c.level(args.levelId) ?: error("No level ${args.levelId}")
                level = lv
                region = lv.region
                RunSpec.forLevel(lv, difficulty)
            }
        } catch (e: Exception) {
            loadError = if (args.daily) "Today's map could not be built." else "Levels could not be read."
            return
        }
        var fresh = Sim(spec, c)
        var resumedAt = -1
        if (!launch.ignoreSave) {
            val row = withContext(Dispatchers.IO) { repo.resumeRow() }
            if (row != null) {
                val save = SaveCodec.decodeRun(row.stateJson)
                val matches = save != null && save.levelId == spec.levelId && save.difficulty == spec.difficulty.id &&
                    save.dailyKey == spec.dailyKey
                if (matches) {
                    try {
                        fresh.restore(save!!)
                        resumedAt = save.wave
                    } catch (e: IllegalArgumentException) {
                        withContext(Dispatchers.IO) { repo.clearResume() }
                        loadError = "This level's save could not be read."
                        pendingSpec = spec
                        return
                    }
                } else if (save == null && row.levelId == spec.levelId) {
                    withContext(Dispatchers.IO) { repo.clearResume() }
                    loadError = "This level's save could not be read."
                    pendingSpec = spec
                    return
                }
            }
        }
        start(fresh)
        if (resumedAt >= 0) {
            val where = if (args.daily) "today's map" else "level ${spec.levelId}"
            show("Resumed $where at the start of wave ${resumedAt + 1}", warning = false, millis = 4500)
        }
        if (!args.daily && spec.levelId == 1 && !s.tutorialDone && resumedAt < 0) coachStep = 0
        firstRouteDraw = !s.firstRouteDrawn
    }

    private var pendingSpec: RunSpec? = null
    private var firstRouteReady = false

    private fun start(s: Sim) {
        sim = s
        loadError = null
        selectedTile = -1
        tool = null
        paused = false
        clock.reset()
        comboFlash.fill(0f)
        ghostField = DistanceField(s.layout)
        wallSigA = -1L
        firstRouteReady = false
        committedCount.fill(0)
        settleTile = -1
        syncHud(s)
        refreshRoutes(s)
        saveBoundary()
        launch.started()
    }

    /** Starts the run again from wave 1. */
    fun restart() {
        val c = content ?: return
        val spec = sim?.spec ?: pendingSpec ?: return
        pendingSpec = null
        pauseSheetOpen = false
        coachStep = -1
        start(Sim(spec, c))
        show("Restarted from wave 1", warning = false)
    }

    // ------------------------------------------------------------------ frame loop

    /** Called once per display frame with the time since the last one. Allocates nothing. */
    fun frame(frameNanos: Long) {
        val s = sim ?: return
        if (s.phase == Phase.WAVE && !paused && !pauseSheetOpen) {
            val ticks = clock.advance(frameNanos, speed)
            for (k in 0 until ticks) {
                s.step()
                if (s.phase != Phase.WAVE) break
            }
            val dt = frameNanos / 1_000_000_000f * speed
            for (i in 0 until Grid.N) if (comboFlash[i] > 0f) comboFlash[i] -= dt
            drainEvents(s)
            if (sigChanged(s)) refreshRoutes(s)
            syncHud(s)
            frameTick++
            if (s.phase != Phase.WAVE) onWaveEnded(s)
        }
    }

    private fun drainEvents(s: Sim) {
        while (s.pollEvent(event)) {
            when (event[0]) {
                Sim.EV_KILL -> sfx.play(Sfx.Sound.KILL)
                Sim.EV_LEAK -> sfx.play(Sfx.Sound.LEAK)
                Sim.EV_COMBO -> {
                    sfx.play(Sfx.Sound.COMBO)
                    val t = event[1]
                    if (t in 0 until Grid.N) comboFlash[t] = COMBO_FLASH
                    for (li in s.links.indices) { val l = s.links[li]; if (l.a != t && l.b != t) continue
                        comboFlash[l.a] = COMBO_FLASH; comboFlash[l.b] = COMBO_FLASH
                    }
                    requestHaptic()
                }
                Sim.EV_TRAP -> when (TrapKind.entries[event[2]]) {
                    TrapKind.SPIKE, TrapKind.GRINDER -> sfx.play(Sfx.Sound.SPIKE)
                    TrapKind.EMBER -> sfx.play(Sfx.Sound.FIRE)
                    TrapKind.DEADFALL -> sfx.play(Sfx.Sound.DEADFALL)
                    TrapKind.HAMMER, TrapKind.PUSHER -> sfx.play(Sfx.Sound.HAMMER)
                    else -> Unit
                }
                Sim.EV_DART -> sfx.play(Sfx.Sound.DART)
                Sim.EV_WALL_BROKEN -> {
                    sfx.play(Sfx.Sound.BREAK)
                    if (selectedTile == event[1]) selectedTile = -1
                }
                Sim.EV_WON -> sfx.play(Sfx.Sound.WON)
                Sim.EV_LOST -> sfx.play(Sfx.Sound.LOST)
                else -> Unit
            }
        }
    }

    private fun requestHaptic() {
        if (haptics) hapticTick = ++hapticRequest
    }

    private fun onWaveEnded(s: Sim) {
        when (s.phase) {
            Phase.BUILD -> saveBoundary()
            Phase.WON, Phase.LOST -> finish(s)
            else -> Unit
        }
    }

    private fun syncHud(s: Sim) {
        hearts = s.hearts
        coin = s.coin
        wave = minOf(s.waveNumber, if (s.spec.endless) Int.MAX_VALUE else s.totalWaves)
        phase = s.phase
        undoAvailable = s.undoCount > 0
        var best = Int.MAX_VALUE
        for (g in s.layout.gates) {
            val d = s.field.dist[g]
            if (d in 0 until best) best = d
        }
        routeSteps = if (best == Int.MAX_VALUE) 0 else best
    }

    private fun sigChanged(s: Sim): Boolean {
        var a = 0L
        var b = 0L
        for (i in 0 until Grid.N) if (s.walls[i]) {
            if (i < 64) a = a or (1L shl i) else b = b or (1L shl (i - 64))
        }
        if (a == wallSigA && b == wallSigB) return false
        wallSigA = a; wallSigB = b
        return true
    }

    private fun refreshRoutes(s: Sim) {
        if (ghostField == null) return
        sigChanged(s)
        var changed = false
        for (k in 0 until 2) {
            val oldCount = committedCount[k]
            System.arraycopy(committedPath[k], 0, previousPath[k], 0, oldCount)
            previousCount[k] = oldCount
            committedCount[k] = if (k < s.layout.gates.size) Route.follow(s.layout, s.walls, s.field, s.layout.gates[k], -1, committedPath[k]) else 0
            if (oldCount != committedCount[k]) changed = true
            else for (i in 0 until oldCount) if (previousPath[k][i] != committedPath[k][i]) { changed = true; break }
        }
        if (changed && firstRouteReady) routeChangeSerial++
        firstRouteReady = true
        if (ghostTile >= 0) previewGhost(s, ghostTile)
        routeVersion++
    }

    // ------------------------------------------------------------------ placement

    /** What a release on [tile] would do with the current tool. */
    private fun wouldPlace(s: Sim, tile: Int): Boolean {
        if (tile !in 0 until Grid.N) return false
        val t = tool
        if (s.trapAt[tile] >= 0) return false
        if (s.walls[tile]) return t != null && t.wallMounted
        return true
    }

    fun ghostAt(tile: Int) {
        val s = sim ?: return
        if (s.phase == Phase.WON || s.phase == Phase.LOST) return
        if (tile == ghostTile) return
        if (tile < 0 || !wouldPlace(s, tile) || (tool == null && !s.layout.buildable(tile))) {
            if (ghostTile != -1) {
                ghostTile = -1
                routeVersion++
            }
            return
        }
        ghostTile = tile
        previewGhost(s, tile)
        routeVersion++
        frameTick++
    }

    private fun previewGhost(s: Sim, tile: Int) {
        val t = tool
        ghostIsWall = t == null
        if (t == null) {
            val err = s.wallError(tile)
            ghostError = err
            val f = ghostField ?: return
            if (err == null || err == Placement.Error.NEEDS_COIN) {
                var best = Int.MAX_VALUE
                f.compute(s.walls, tile)
                for (k in 0 until 2) {
                    ghostCount[k] = if (k < s.layout.gates.size) Route.follow(s.layout, s.walls, f, s.layout.gates[k], tile, ghostPath[k]) else 0
                    if (k < s.layout.gates.size && ghostCount[k] > 0) best = minOf(best, ghostCount[k] - 1)
                }
                ghostDelta = if (best == Int.MAX_VALUE) 0 else best - routeSteps
            } else {
                ghostCount[0] = 0; ghostCount[1] = 0
                ghostDelta = 0
            }
        } else {
            ghostError = s.trapError(t, tile)
            ghostCount[0] = 0; ghostCount[1] = 0
            ghostDelta = 0
        }
    }

    fun clearGhost() {
        if (ghostTile != -1) {
            ghostTile = -1
            ghostError = null
            routeVersion++
            frameTick++
        }
    }

    /** A finger lifted on [tile]: place with the current tool, or open what is already there. */
    fun release(tile: Int) {
        val s = sim ?: return
        clearGhost()
        if (tile !in 0 until Grid.N) return
        if (s.phase == Phase.WON || s.phase == Phase.LOST) return
        if (!wouldPlace(s, tile)) {
            if (s.walls[tile] || s.trapAt[tile] >= 0) {
                selectedTile = if (selectedTile == tile) -1 else tile
                sfx.play(Sfx.Sound.TAP)
                frameTick++
            }
            return
        }
        selectedTile = -1
        val t = tool
        val err = if (t == null) s.placeWall(tile) else s.placeTrap(t, tile)
        if (err != null) {
            sfx.play(Sfx.Sound.INVALID)
            show(errorText(err, t), warning = true)
            frameTick++
            return
        }
        sfx.play(if (t == null) Sfx.Sound.WALL else Sfx.Sound.PLACE)
        requestHaptic()
        settleTile = tile
        settleSerial++
        if (s.links.isNotEmpty()) {
            for (l in s.links) if (l.a == tile || l.b == tile) {
                comboFlash[l.a] = COMBO_FLASH; comboFlash[l.b] = COMBO_FLASH
            }
        }
        advanceCoach(t)
        afterChange(s)
    }

    private fun advanceCoach(t: TrapKind?) {
        when {
            coachStep == 0 && t == null -> {
                coachStep = 1
                sim?.let { if (TrapKind.SPIKE in it.spec.roster) tool = TrapKind.SPIKE }
            }
            coachStep == 1 && t == TrapKind.SPIKE -> coachStep = 2
        }
    }

    private fun afterChange(s: Sim) {
        syncHud(s)
        refreshRoutes(s)
        frameTick++
        if (s.phase == Phase.BUILD) saveBoundary()
    }

    fun errorText(err: Placement.Error, t: TrapKind?): String = when (err) {
        Placement.Error.NEEDS_COIN -> {
            val cost = if (t == null) Sim.WALL_COST else sim?.trapCost(t) ?: 0
            "Needs $cost coin"
        }
        else -> err.message
    }

    fun upgradeSelected() {
        val s = sim ?: return
        val tile = selectedTile
        val i = s.trapAt.getOrElse(tile) { -1 }
        if (i < 0) return
        val kind = s.traps[i].kind
        val err = s.upgrade(tile)
        if (err != null) {
            sfx.play(Sfx.Sound.INVALID)
            show(if (err == Placement.Error.NEEDS_COIN) "Needs ${s.trapCost(kind)} coin" else "Already upgraded", warning = true)
            return
        }
        sfx.play(Sfx.Sound.PLACE)
        show("Upgraded ${kind.label.lowercase()}", warning = false)
        afterChange(s)
    }

    fun rotateSelected() {
        val s = sim ?: return
        if (s.rotate(selectedTile)) {
            sfx.play(Sfx.Sound.TAP)
            afterChange(s)
        } else {
            show("No other open floor to face", warning = true)
        }
    }

    fun sellSelected() {
        val s = sim ?: return
        val tile = selectedTile
        val label = s.trapAt.getOrElse(tile) { -1 }.let { if (it >= 0) s.traps[it].kind.label.lowercase() else "wall" }
        val refund = s.sell(tile)
        if (refund < 0) return
        selectedTile = -1
        sfx.play(Sfx.Sound.TAP)
        show(if (refund > 0) "Sold $label for $refund coin" else "Sold $label", warning = false)
        afterChange(s)
    }

    fun undo() {
        val s = sim ?: return
        if (s.undoLast()) {
            selectedTile = -1
            sfx.play(Sfx.Sound.TAP)
            show("Undid last placement", warning = false)
            afterChange(s)
        } else {
            show(if (s.phase == Phase.WAVE) "Undo is for the build phase. Sell instead." else "Nothing to undo", warning = true)
        }
    }

    fun selectTool(t: TrapKind?) {
        tool = t
        selectedTile = -1
        sfx.play(Sfx.Sound.TAP)
    }

    fun sendWave() {
        val s = sim ?: return
        if (s.phase != Phase.BUILD) return
        saveBoundary()
        if (s.sendWave()) {
            selectedTile = -1
            paused = false
            clock.reset()
            sfx.play(Sfx.Sound.WAVE)
            if (coachStep >= 0) {
                coachStep = -1
                viewModelScope.launch { prefs.setTutorialDone(true) }
            }
            syncHud(s)
            frameTick++
        }
    }

    fun cycleSpeed() {
        speed = if (speed >= 3) 1 else speed + 1
        viewModelScope.launch { prefs.setDefaultSpeed(speed) }
    }

    fun openPause() {
        pauseSheetOpen = true
        paused = true
        selectedTile = -1
    }

    /** Resume: close the sheet and let the wave run. */
    fun closePause() {
        pauseSheetOpen = false
        paused = false
        clock.reset()
    }

    /** The sheet was dismissed without Resume: the board stays paused so the player can build. */
    fun dismissPauseSheet() {
        pauseSheetOpen = false
        if (sim?.phase != Phase.WAVE) paused = false
        if (sim?.phase == Phase.WAVE) show("Paused. Build, then press play.", warning = false, millis = 3000)
    }

    /** The HUD pause button: pause into the sheet, or resume a board left paused. */
    fun togglePause() {
        if (paused) closePause() else openPause()
    }

    fun onStop() {
        if (sim?.phase == Phase.BUILD) saveBoundary()
        if (sim?.phase == Phase.WAVE) {
            pauseSheetOpen = true
            paused = true
        }
    }

    fun firstRouteDrawn() {
        firstRouteDraw = false
        viewModelScope.launch { prefs.setFirstRouteDrawn(true) }
    }

    fun dismissCoach() {
        coachStep = -1
        viewModelScope.launch { prefs.setTutorialDone(true) }
    }

    fun show(text: String, warning: Boolean, millis: Long = if (warning) 1800 else 2600) {
        val m = BoardMessage(text, warning, ++messageSerial)
        message = m
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(millis)
            if (message?.id == m.id) message = null
        }
    }

    // ------------------------------------------------------------------ saving and finishing

    /** Saves the run as it stands at a wave boundary. Only valid in the build phase. */
    private fun saveBoundary() {
        val s = sim ?: return
        if (s.phase != Phase.BUILD) return
        val snap = s.snapshot()
        viewModelScope.launch(saveDispatcher) { repo.saveResume(snap) }
    }

    /** One writer at a time, in order, so a quick run of placements saves the last one. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)

    private fun finish(s: Sim) {
        if (finished != null || recording) return
        recording = true
        val won = s.phase == Phase.WON
        val score = s.score()
        viewModelScope.launch {
            val result = withContext(saveDispatcher) {
                if (args.daily) {
                    val outcome = repo.recordDaily(dateKey!!, s.waveIndex, score)
                    prefs.addDailyRun()
                    Result(0, s.spec.difficulty.id, true, false, score, s.waveNumber, -1, s.kills, 0, Scoring.Medal.NONE.id, outcome.newBest, 0, 0, dateKey)
                } else {
                    val par = level?.par?.get(s.spec.difficulty.id) ?: Par(0, 0)
                    val outcome = repo.recordLevel(s.spec.levelId, s.spec.difficulty.id, won, score, s.hearts, par)
                    if (won) prefs.addLevelClear()
                    Result(
                        s.spec.levelId, s.spec.difficulty.id, false, won, score,
                        if (won) s.totalWaves else s.waveNumber, s.totalWaves, s.kills, s.hearts,
                        outcome.medal.id, outcome.newBest, par.silver, par.gold,
                    )
                }
            }
            finished = result
        }
    }

    private var recording = false

    fun describe(): String {
        val s = sim ?: return "Loading the board"
        val waveText = if (s.spec.endless) "Wave $wave" else "Wave $wave of ${s.totalWaves}"
        return "$waveText, $hearts hearts, $coin coin, route $routeSteps steps"
    }

    fun enemyLabel(kind: EnemyKind) = kind.label

    companion object {
        const val COMBO_FLASH = 0.6f
    }
}
