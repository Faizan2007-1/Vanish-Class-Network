package com.example.vanish.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Delivered messages (text is stored here, and only here, until expires_at). */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val text: String,
    val sentAt: Long,
    val expiresAt: Long,
    val mine: Boolean
)

/** Messages typed but not yet confirmed by the relay. createdAt = phone clock, used only for age. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val text: String,
    val createdAt: Long
)

/** Ids already processed. Holds NO text. Used to ignore duplicates / replays. */
@Entity(tableName = "seen")
data class SeenEntity(
    @PrimaryKey val id: String,
    val expiresAt: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages ORDER BY sentAt") fun observeMessages(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM outbox ORDER BY createdAt") fun observeOutbox(): Flow<List<OutboxEntity>>
    @Query("SELECT * FROM outbox ORDER BY createdAt") suspend fun outboxAll(): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessage(m: MessageEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOutbox(o: OutboxEntity): Long
    /** Returns -1 if the id already exists => duplicate. Atomic duplicate check. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSeen(s: SeenEntity): Long

    @Query("DELETE FROM outbox WHERE id = :id") suspend fun deleteOutbox(id: String): Int
    @Query("DELETE FROM messages WHERE id = :id") suspend fun deleteMessage(id: String): Int
    @Query("DELETE FROM messages WHERE expiresAt <= :now") suspend fun deleteExpired(now: Long): Int
    @Query("DELETE FROM seen WHERE expiresAt < :cutoff") suspend fun purgeSeen(cutoff: Long): Int
    @Query("SELECT id FROM outbox WHERE createdAt <= :cutoff") suspend fun staleOutboxIds(cutoff: Long): List<String>
    @Query("SELECT COUNT(*) FROM outbox WHERE id = :id") suspend fun inOutbox(id: String): Int
}

@Database(entities = [MessageEntity::class, OutboxEntity::class, SeenEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ChatDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: android.content.Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "vanish.db")
                // TRUNCATE journal: no WAL file that could keep copies of deleted text.
                .setJournalMode(JournalMode.TRUNCATE)
                .addCallback(object : Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // secure_delete: SQLite overwrites deleted content with zeros instead of
                        // leaving it in free pages.
                        db.query("PRAGMA secure_delete = ON").close()
                    }
                })
                .build().also { inst = it }
        }
    }
}
