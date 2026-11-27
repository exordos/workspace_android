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
data class StreamUIO(
    @PrimaryKey val uuid: String,
    var unreadCount: Int,
    var activeUnreadCount: Int,
    var passiveUnreadCount: Int,
    var updatedAt: String,
    var name: String,
    val isPrivate: Boolean,
    val color: Int,
    var lastMessageUuid: String? = null,
    var defaultTopicUuid: String? = null,
    val directUserUuid: String? = null,
    var notificationMode: String,
    val isArchived: Boolean = false,
    val encryption: Boolean = false,
    var currentEncryptionKeyUuid: String? = null,
    val ownerUuid: String? = null,
    val projectId: String? = null,
    val userUuid: String? = null,
    var role: String? = null,
    val description: String = "",
    val inviteOnly: Boolean = false,
    val announce: Boolean = false,
) {
//    fun displayableName(participant: UserUIO? = directUser): String =
//        participant?.takeIf { it.uuid == directUserUuid }
//            ?.displayableName()?.trim()?.takeIf { it.isNotEmpty() }
//            ?: name
}

@Dao
interface StreamDao {
    @Query("SELECT * FROM StreamUIO WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): StreamUIO?


    @Query("UPDATE StreamUIO SET role = :role WHERE uuid = :streamUuid")
    suspend fun setOwnRole(streamUuid: String, role: String)


    @Query("UPDATE StreamUIO SET lastMessageUuid = NULL WHERE lastMessageUuid = :messageUuid")
    suspend fun clearMessagePreview(messageUuid: String)

    @Query("DELETE FROM StreamUIO WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT * FROM StreamUIO")
    fun observeStreams(): Flow<List<StreamUIO>>

    @Query("SELECT COUNT(*) FROM StreamUIO")
    fun countObjects(): Int
    @Query("SELECT * FROM StreamUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getStream(uuid: String): StreamUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(streams: List<StreamUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stream: StreamUIO)
    @Delete
    suspend fun delete(stream: StreamUIO)
    @Query("DELETE FROM StreamUIO")
    suspend fun deleteAll()
}
