package com.exordos.workspace.data.uio

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Entity
data class TopicUIO(
    @PrimaryKey val uuid: String,
    var name: String,
    val color: Int,
    val streamUuid: String,
    var updatedAt: String,
    var unreadCount: Int,
    var activeUnreadCount: Int,
    var isDone: Boolean,
    val isDefault: Boolean,
    var lastMessageUuid: String? = null,
    var notificationMode: String,
    var summary: String? = null
)

@Dao
interface TopicDao {

    @Query("UPDATE TopicUIO SET lastMessageUuid = NULL WHERE lastMessageUuid = :messageUuid")
    suspend fun clearMessagePreview(messageUuid: String)

    @Query("DELETE FROM TopicUIO WHERE streamUuid = :streamUuid")
    suspend fun deleteByStream(streamUuid: String)

    @Query("DELETE FROM TopicUIO WHERE streamUuid IN (:streamUuids) AND uuid NOT IN (:keptUuids)")
    suspend fun deleteMissingForStreams(streamUuids: List<String>, keptUuids: List<String>)

    @Query("SELECT * FROM TopicUIO")
    fun observeTopics(): Flow<List<TopicUIO>>

    @Query("SELECT COUNT(*) FROM TopicUIO")
    fun countObjects(): Int
    @Query("SELECT * FROM TopicUIO WHERE streamUuid LIKE :streamUuid")
    fun observeTopics(streamUuid: String): Flow<List<TopicUIO>>
    @Query("SELECT * FROM TopicUIO WHERE streamUuid LIKE :streamUuid")
    fun getTopics(streamUuid: String): List<TopicUIO>
    @Query("SELECT * FROM TopicUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getTopic(uuid: String): TopicUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(topics: List<TopicUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(topic: TopicUIO)
    @Delete
    suspend fun delete(topic: TopicUIO)
    @Query("DELETE FROM TopicUIO")
    suspend fun deleteAll()
}
