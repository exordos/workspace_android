package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(
    tableName = "cached_streams",
    primaryKeys = ["owner_key_hash", "uuid"],
    indices = [
        Index(value = ["owner_key_hash", "position"]),
    ],
)
internal data class CachedStreamEntity(
    @ColumnInfo(name = "owner_key_hash")
    val ownerKeyHash: String,
    val uuid: String,
    val position: Int,
    @ColumnInfo(name = "encrypted_payload", typeAffinity = ColumnInfo.BLOB)
    val encryptedPayload: ByteArray,
    @ColumnInfo(name = "cached_at_millis")
    val cachedAtMillis: Long,
)

@Entity(
    tableName = "cached_topics",
    primaryKeys = ["owner_key_hash", "uuid"],
    indices = [
        Index(value = ["owner_key_hash", "stream_uuid", "position"]),
    ],
)
internal data class CachedTopicEntity(
    @ColumnInfo(name = "owner_key_hash")
    val ownerKeyHash: String,
    val uuid: String,
    @ColumnInfo(name = "stream_uuid")
    val streamUuid: String,
    val position: Int,
    @ColumnInfo(name = "encrypted_payload", typeAffinity = ColumnInfo.BLOB)
    val encryptedPayload: ByteArray,
    @ColumnInfo(name = "cached_at_millis")
    val cachedAtMillis: Long,
)

@Entity(
    tableName = "cached_messages",
    primaryKeys = ["owner_key_hash", "uuid"],
    indices = [
        Index(
            value = [
                "owner_key_hash",
                "stream_uuid",
                "topic_uuid",
                "position",
            ],
        ),
    ],
)
internal data class CachedMessageEntity(
    @ColumnInfo(name = "owner_key_hash")
    val ownerKeyHash: String,
    val uuid: String,
    @ColumnInfo(name = "stream_uuid")
    val streamUuid: String,
    @ColumnInfo(name = "topic_uuid")
    val topicUuid: String,
    val position: Int,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long,
    @ColumnInfo(name = "encrypted_payload", typeAffinity = ColumnInfo.BLOB)
    val encryptedPayload: ByteArray,
    @ColumnInfo(name = "cached_at_millis")
    val cachedAtMillis: Long,
)

internal data class CachedWorkspaceRows(
    val streams: List<CachedStreamEntity>,
    val topics: List<CachedTopicEntity>,
    val messages: List<CachedMessageEntity>,
)

@Dao
internal interface WorkspaceSnapshotDao {
    @Query(
        """
        SELECT * FROM cached_streams
        WHERE owner_key_hash = :ownerKeyHash
          AND length(encrypted_payload) <= :maxEncryptedBytes
        ORDER BY position ASC
        LIMIT :limit
        """,
    )
    suspend fun readStreams(
        ownerKeyHash: String,
        limit: Int,
        maxEncryptedBytes: Int,
    ): List<CachedStreamEntity>

    @Query(
        """
        SELECT * FROM cached_topics
        WHERE owner_key_hash = :ownerKeyHash
          AND length(encrypted_payload) <= :maxEncryptedBytes
        ORDER BY stream_uuid ASC, position ASC
        LIMIT :limit
        """,
    )
    suspend fun readTopics(
        ownerKeyHash: String,
        limit: Int,
        maxEncryptedBytes: Int,
    ): List<CachedTopicEntity>

    @Query(
        """
        SELECT * FROM cached_messages
        WHERE owner_key_hash = :ownerKeyHash
          AND length(encrypted_payload) <= :maxEncryptedBytes
        ORDER BY stream_uuid ASC, topic_uuid ASC, position ASC
        LIMIT :limit
        """,
    )
    suspend fun readMessages(
        ownerKeyHash: String,
        limit: Int,
        maxEncryptedBytes: Int,
    ): List<CachedMessageEntity>

    @Transaction
    suspend fun readAccount(
        ownerKeyHash: String,
        streamLimit: Int,
        topicLimit: Int,
        messageLimit: Int,
        maxEncryptedBytes: Int,
    ): CachedWorkspaceRows = CachedWorkspaceRows(
        streams = readStreams(
            ownerKeyHash,
            streamLimit,
            maxEncryptedBytes,
        ),
        topics = readTopics(
            ownerKeyHash,
            topicLimit,
            maxEncryptedBytes,
        ),
        messages = readMessages(
            ownerKeyHash,
            messageLimit,
            maxEncryptedBytes,
        ),
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<CachedStreamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<CachedTopicEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessageEntity>)

    @Query("DELETE FROM cached_messages WHERE owner_key_hash = :ownerKeyHash")
    suspend fun deleteMessages(ownerKeyHash: String)

    @Query("DELETE FROM cached_topics WHERE owner_key_hash = :ownerKeyHash")
    suspend fun deleteTopics(ownerKeyHash: String)

    @Query("DELETE FROM cached_streams WHERE owner_key_hash = :ownerKeyHash")
    suspend fun deleteStreams(ownerKeyHash: String)

    @Transaction
    suspend fun replaceAccount(
        ownerKeyHash: String,
        streams: List<CachedStreamEntity>,
        topics: List<CachedTopicEntity>,
        messages: List<CachedMessageEntity>,
    ) {
        deleteMessages(ownerKeyHash)
        deleteTopics(ownerKeyHash)
        deleteStreams(ownerKeyHash)
        if (streams.isNotEmpty()) insertStreams(streams)
        if (topics.isNotEmpty()) insertTopics(topics)
        if (messages.isNotEmpty()) insertMessages(messages)
    }

    @Transaction
    suspend fun clearAccount(ownerKeyHash: String) {
        deleteMessages(ownerKeyHash)
        deleteTopics(ownerKeyHash)
        deleteStreams(ownerKeyHash)
    }
}

@Database(
    entities = [
        CachedStreamEntity::class,
        CachedTopicEntity::class,
        CachedMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class WorkspaceSnapshotDatabase : RoomDatabase() {
    abstract fun snapshotDao(): WorkspaceSnapshotDao

    companion object {
        const val DATABASE_NAME = "workspace_snapshot.db"

        @Volatile
        private var instance: WorkspaceSnapshotDatabase? = null

        fun getInstance(context: Context): WorkspaceSnapshotDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkspaceSnapshotDatabase::class.java,
                    DATABASE_NAME,
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}
