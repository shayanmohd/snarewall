package com.mohdshayan.snarewall.data

import androidx.room.withTransaction
import com.mohdshayan.snarewall.data.db.AppDatabase
import com.mohdshayan.snarewall.data.db.DailyScoreEntity
import com.mohdshayan.snarewall.data.db.LevelProgressEntity
import com.mohdshayan.snarewall.data.db.ResumeEntity
import com.mohdshayan.snarewall.game.DailyScoreRow
import com.mohdshayan.snarewall.game.LevelProgressRow
import com.mohdshayan.snarewall.game.Par
import com.mohdshayan.snarewall.game.ProgressRules
import com.mohdshayan.snarewall.game.ResumeRow
import com.mohdshayan.snarewall.game.RunSave
import com.mohdshayan.snarewall.game.SaveCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun LevelProgressEntity.toRow() = LevelProgressRow(levelId, difficulty, bestScore, medal, bestHearts, clears, firstClearedAt, lastPlayedAt)
fun LevelProgressRow.toEntity() = LevelProgressEntity(levelId, difficulty, bestScore, medal, bestHearts, clears, firstClearedAt, lastPlayedAt)
fun DailyScoreEntity.toRow() = DailyScoreRow(dateKey, bestWave, bestScore, runs, lastPlayedAt)
fun DailyScoreRow.toEntity() = DailyScoreEntity(dateKey, bestWave, bestScore, runs, lastPlayedAt)
fun ResumeEntity.toRow() = ResumeRow(levelId, difficulty, waveIndex, stateJson, savedAt)
fun ResumeRow.toEntity() = ResumeEntity(0, levelId, difficulty, waveIndex, stateJson, savedAt)

class ProgressRepository(private val db: AppDatabase) {
    private val dao = db.progressDao()

    val progress: Flow<List<LevelProgressRow>> = dao.observeProgress().map { list -> list.map { it.toRow() } }
    val resume: Flow<ResumeEntity?> = dao.observeResume()
    val daily: Flow<List<DailyScoreRow>> = dao.observeDaily().map { list -> list.map { it.toRow() } }

    suspend fun resumeRow(): ResumeEntity? = dao.resume()

    suspend fun saveResume(save: RunSave) {
        dao.upsertResume(
            ResumeEntity(0, save.levelId, save.difficulty, save.wave, SaveCodec.encodeRun(save), System.currentTimeMillis()),
        )
    }

    suspend fun clearResume() = dao.clearResume()

    /** Records a finished level and drops its resume save in one transaction, so a kill cannot half-apply it. */
    suspend fun recordLevel(levelId: Int, difficulty: String, won: Boolean, score: Int, hearts: Int, par: Par): ProgressRules.LevelOutcome =
        db.withTransaction {
            val old = dao.progress(levelId, difficulty)?.toRow()
            val outcome = ProgressRules.level(old, levelId, difficulty, won, score, hearts, par, System.currentTimeMillis())
            dao.upsertProgress(outcome.row.toEntity())
            dao.clearResume()
            outcome
        }

    suspend fun recordDaily(dateKey: String, wavesHeld: Int, score: Int): ProgressRules.DailyOutcome =
        db.withTransaction {
            val old = dao.daily(dateKey)?.toRow()
            val outcome = ProgressRules.daily(old, dateKey, wavesHeld, score, System.currentTimeMillis())
            dao.upsertDaily(outcome.row.toEntity())
            dao.clearResume()
            outcome
        }

    suspend fun snapshotRows(): Triple<List<LevelProgressRow>, ResumeRow?, List<DailyScoreRow>> =
        db.withTransaction {
            Triple(dao.allProgress().map { it.toRow() }, dao.resume()?.toRow(), dao.allDaily().map { it.toRow() })
        }

    /** Replaces every table in one transaction. */
    suspend fun replaceAll(levels: List<LevelProgressRow>, resume: ResumeRow?, daily: List<DailyScoreRow>) =
        db.withTransaction {
            dao.clearProgress()
            dao.clearResume()
            dao.clearDaily()
            dao.upsertProgress(levels.map { it.toEntity() })
            resume?.let { dao.upsertResume(it.toEntity()) }
            dao.upsertDaily(daily.map { it.toEntity() })
        }
}
