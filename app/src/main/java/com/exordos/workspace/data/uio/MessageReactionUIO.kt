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
data class MessageReactionUIO(
    @PrimaryKey val uuid: String,
    val userUuid: String,
    val emojiName: String,
    val messageUuid: String,
)

@Dao
interface MessageReactionDao {
    @Query("SELECT * FROM MessageReactionUIO")
    fun observeMessageReactions(): Flow<List<MessageReactionUIO>>
    @Query("SELECT * FROM MessageReactionUIO WHERE messageUuid LIKE :messageUuid LIMIT 1")
    fun getMessageReactions(messageUuid: String): MessageReactionUIO?
    @Query("SELECT * FROM MessageReactionUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getMessageReaction(uuid: String): MessageReactionUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(messageReactions: List<MessageReactionUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(messageReaction: MessageReactionUIO)
    @Delete
    suspend fun delete(messageReaction: MessageReactionUIO)
    @Query("DELETE FROM MessageReactionUIO")
    suspend fun deleteAll()
}
