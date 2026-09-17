package com.mohdshayan.snarewall.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Best result per level and difficulty. */
@Entity(tableName = "level_progress", primaryKeys = ["levelId", "difficulty"])
data class LevelProgressEntity(
    val levelId: Int,
    val difficulty: String,
    val bestScore: Int,
    val medal: String,
    val bestHearts: Int,
    val clears: Int,
    val firstClearedAt: Long?,
    val lastPlayedAt: Long,
)

/** The one run in progress, saved at a wave boundary. */
@Entity(tableName = "resume")
data class ResumeEntity(
    @androidx.room.PrimaryKey val slot: Int = 0,
    val levelId: Int,
    val difficulty: String,
    val waveIndex: Int,
    val stateJson: String,
    val savedAt: Long,
)

/** Best daily run per UTC date. */
@Entity(tableName = "daily_score")
data class DailyScoreEntity(
    @androidx.room.PrimaryKey val dateKey: String,
    val bestWave: Int,
    val bestScore: Int,
    val runs: Int,
    val lastPlayedAt: Long,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM level_progress")
    fun observeProgress(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress")
    suspend fun allProgress(): List<LevelProgressEntity>

    @Query("SELECT * FROM level_progress WHERE levelId = :levelId AND difficulty = :difficulty")
    suspend fun progress(levelId: Int, difficulty: String): LevelProgressEntity?

    @Upsert
    suspend fun upsertProgress(row: LevelProgressEntity)

    @Upsert
    suspend fun upsertProgress(rows: List<LevelProgressEntity>)

    @Query("DELETE FROM level_progress")
    suspend fun clearProgress()

    @Query("SELECT * FROM resume WHERE slot = 0")
    fun observeResume(): Flow<ResumeEntity?>

    @Query("SELECT * FROM resume WHERE slot = 0")
    suspend fun resume(): ResumeEntity?

    @Upsert
    suspend fun upsertResume(row: ResumeEntity)

    @Query("DELETE FROM resume")
    suspend fun clearResume()

    @Query("SELECT * FROM daily_score ORDER BY dateKey DESC")
    fun observeDaily(): Flow<List<DailyScoreEntity>>

    @Query("SELECT * FROM daily_score ORDER BY dateKey DESC")
    suspend fun allDaily(): List<DailyScoreEntity>

    @Query("SELECT * FROM daily_score WHERE dateKey = :dateKey")
    suspend fun daily(dateKey: String): DailyScoreEntity?

    @Upsert
    suspend fun upsertDaily(row: DailyScoreEntity)

    @Upsert
    suspend fun upsertDaily(rows: List<DailyScoreEntity>)

    @Query("DELETE FROM daily_score")
    suspend fun clearDaily()
}

@Database(
    entities = [LevelProgressEntity::class, ResumeEntity::class, DailyScoreEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snarewall.db",
                ).build().also { instance = it }
            }
    }
}
