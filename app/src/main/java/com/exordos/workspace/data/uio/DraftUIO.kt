package com.exordos.workspace.data.uio

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Entity
data class DraftUIO(
    @PrimaryKey val uuid: String,
    val streamUuid: String,
    val topicUuid: String,
    val updatedAt: String,
    @Embedded(prefix = "payload_")
    var payload: MessagePayloadUIO,
    val revision: Int
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM DraftUIO")
    fun observeDrafts(): Flow<List<DraftUIO>>

    @Query("SELECT * FROM DraftUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getDraft(uuid: String): DraftUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(drafts: List<DraftUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftUIO)
    @Delete
    suspend fun delete(draft: DraftUIO)
    @Query("DELETE FROM DraftUIO")
    suspend fun deleteAll()
}
