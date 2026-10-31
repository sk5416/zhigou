package com.sk5416.zhigou.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

object Codec { val json = Json { encodeDefaults = true; ignoreUnknownKeys = false } }

@Dao interface LearningDao {
    @Query("SELECT * FROM Reading ORDER BY updatedAt DESC") fun readings(): Flow<List<Reading>>
    @Query("SELECT * FROM Reading") suspend fun allReadings(): List<Reading>
    @Query("SELECT * FROM Reading WHERE chapterId=:id") suspend fun reading(id: String): Reading?
    @Upsert suspend fun putReading(value: Reading)
    @Upsert suspend fun putReadings(value: List<Reading>)
    @Query("SELECT * FROM SessionRow ORDER BY updatedAt DESC") fun sessions(): Flow<List<SessionRow>>
    @Query("SELECT * FROM SessionRow") suspend fun allSessions(): List<SessionRow>
    @Query("SELECT * FROM SessionRow WHERE id=:id") suspend fun session(id: String): SessionRow?
    @Upsert suspend fun putSession(value: SessionRow)
    @Upsert suspend fun putSessions(value: List<SessionRow>)
    @Query("SELECT * FROM Attempt ORDER BY answeredAt DESC") fun attempts(): Flow<List<Attempt>>
    @Query("SELECT * FROM Attempt") suspend fun allAttempts(): List<Attempt>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putAttempt(value: Attempt): Long
    @Insert suspend fun putAttempts(value: List<Attempt>)
    @Query("SELECT * FROM Mistake ORDER BY updatedAt DESC") fun mistakes(): Flow<List<Mistake>>
    @Query("SELECT * FROM Mistake") suspend fun allMistakes(): List<Mistake>
    @Query("SELECT * FROM Mistake WHERE questionId=:id AND revision=:revision") suspend fun mistake(id: String, revision: Int): Mistake?
    @Upsert suspend fun putMistake(value: Mistake)
    @Upsert suspend fun putMistakes(value: List<Mistake>)
    @Upsert suspend fun putIndex(value: List<ChapterIndex>)
    @Query("DELETE FROM ChapterIndex") suspend fun clearIndex()
    @Query("DELETE FROM Reading") suspend fun clearReadings()
    @Query("DELETE FROM SessionRow") suspend fun clearSessions()
    @Query("DELETE FROM Attempt") suspend fun clearAttempts()
    @Query("DELETE FROM Mistake") suspend fun clearMistakes()
}
@Database(entities = [Reading::class, SessionRow::class, Attempt::class, Mistake::class, ChapterIndex::class], version = 1, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() { abstract fun dao(): LearningDao }
