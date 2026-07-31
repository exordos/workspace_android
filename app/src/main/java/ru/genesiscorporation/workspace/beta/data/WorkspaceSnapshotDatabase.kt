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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

@Entity(
    tableName = "cached_folders",
    primaryKeys = ["owner_key_hash", "uuid"],
    indices = [
        Index(value = ["owner_key_hash", "position"]),
    ],
)
internal data class CachedFolderEntity(
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
    tableName = "cached_users",
    primaryKeys = ["owner_key_hash", "uuid"],
    indices = [
        Index(value = ["owner_key_hash", "position"]),
    ],
)
internal data class CachedUserEntity(
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
    tableName = "cached_stream_bindings",
    primaryKeys = ["owner_key_hash", "uuid"],
    indices = [
        Index(value = ["owner_key_hash", "stream_uuid", "position"]),
    ],
)
internal data class CachedStreamBindingEntity(
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

internal data class CachedWorkspaceRows(
    val streams: List<CachedStreamEntity>,
    val topics: List<CachedTopicEntity>,
    val messages: List<CachedMessageEntity>,
    val folders: List<CachedFolderEntity>,
    val users: List<CachedUserEntity>,
    val streamBindings: List<CachedStreamBindingEntity>,
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

    @Query(
        """
        SELECT * FROM cached_folders
        WHERE owner_key_hash = :ownerKeyHash
          AND length(encrypted_payload) <= :maxEncryptedBytes
        ORDER BY position ASC
        LIMIT :limit
        """,
    )
    suspend fun readFolders(
        ownerKeyHash: String,
        limit: Int,
        maxEncryptedBytes: Int,
    ): List<CachedFolderEntity>

    @Query(
        """
        SELECT * FROM cached_users
        WHERE owner_key_hash = :ownerKeyHash
          AND length(encrypted_payload) <= :maxEncryptedBytes
        ORDER BY position ASC
        LIMIT :limit
        """,
    )
    suspend fun readUsers(
        ownerKeyHash: String,
        limit: Int,
        maxEncryptedBytes: Int,
    ): List<CachedUserEntity>

    @Query(
        """
        SELECT * FROM cached_stream_bindings
        WHERE owner_key_hash = :ownerKeyHash
          AND length(encrypted_payload) <= :maxEncryptedBytes
        ORDER BY stream_uuid ASC, position ASC
        LIMIT :limit
        """,
    )
    suspend fun readStreamBindings(
        ownerKeyHash: String,
        limit: Int,
        maxEncryptedBytes: Int,
    ): List<CachedStreamBindingEntity>

    @Transaction
    suspend fun readAccount(
        ownerKeyHash: String,
        streamLimit: Int,
        topicLimit: Int,
        messageLimit: Int,
        folderLimit: Int,
        userLimit: Int,
        streamBindingLimit: Int,
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
        folders = readFolders(
            ownerKeyHash,
            folderLimit,
            maxEncryptedBytes,
        ),
        users = readUsers(
            ownerKeyHash,
            userLimit,
            maxEncryptedBytes,
        ),
        streamBindings = readStreamBindings(
            ownerKeyHash,
            streamBindingLimit,
            maxEncryptedBytes,
        ),
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<CachedStreamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<CachedTopicEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<CachedFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<CachedUserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreamBindings(
        streamBindings: List<CachedStreamBindingEntity>,
    )

    @Query(
        "DELETE FROM cached_stream_bindings " +
            "WHERE owner_key_hash = :ownerKeyHash",
    )
    suspend fun deleteStreamBindings(ownerKeyHash: String)

    @Query("DELETE FROM cached_users WHERE owner_key_hash = :ownerKeyHash")
    suspend fun deleteUsers(ownerKeyHash: String)

    @Query("DELETE FROM cached_folders WHERE owner_key_hash = :ownerKeyHash")
    suspend fun deleteFolders(ownerKeyHash: String)

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
        folders: List<CachedFolderEntity>,
        users: List<CachedUserEntity>,
        streamBindings: List<CachedStreamBindingEntity>,
    ) {
        deleteStreamBindings(ownerKeyHash)
        deleteUsers(ownerKeyHash)
        deleteFolders(ownerKeyHash)
        deleteMessages(ownerKeyHash)
        deleteTopics(ownerKeyHash)
        deleteStreams(ownerKeyHash)
        if (streams.isNotEmpty()) insertStreams(streams)
        if (topics.isNotEmpty()) insertTopics(topics)
        if (messages.isNotEmpty()) insertMessages(messages)
        if (folders.isNotEmpty()) insertFolders(folders)
        if (users.isNotEmpty()) insertUsers(users)
        if (streamBindings.isNotEmpty()) insertStreamBindings(streamBindings)
    }

    @Transaction
    suspend fun clearAccount(ownerKeyHash: String) {
        deleteStreamBindings(ownerKeyHash)
        deleteUsers(ownerKeyHash)
        deleteFolders(ownerKeyHash)
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
        CachedFolderEntity::class,
        CachedUserEntity::class,
        CachedStreamBindingEntity::class,
    ],
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_folders` (
                        `owner_key_hash` TEXT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `encrypted_payload` BLOB NOT NULL,
                        `cached_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`owner_key_hash`, `uuid`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                        `index_cached_folders_owner_key_hash_position`
                    ON `cached_folders` (`owner_key_hash`, `position`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_users` (
                        `owner_key_hash` TEXT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `encrypted_payload` BLOB NOT NULL,
                        `cached_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`owner_key_hash`, `uuid`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                        `index_cached_users_owner_key_hash_position`
                    ON `cached_users` (`owner_key_hash`, `position`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_stream_bindings` (
                        `owner_key_hash` TEXT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `stream_uuid` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `encrypted_payload` BLOB NOT NULL,
                        `cached_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`owner_key_hash`, `uuid`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                        `index_cached_stream_bindings_owner_key_hash_stream_uuid_position`
                    ON `cached_stream_bindings`
                        (`owner_key_hash`, `stream_uuid`, `position`)
                    """.trimIndent(),
                )
            }
        }
    }
}
