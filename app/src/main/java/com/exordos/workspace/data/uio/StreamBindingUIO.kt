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
data class StreamBindingUIO(
    @PrimaryKey val uuid: String,
    var role: String,
    val streamUuid: String,
    var userUuid: String
)

@Dao
interface StreamBindingDao {

    @Query("DELETE FROM StreamBindingUIO WHERE uuid = :uuid AND streamUuid = :streamUuid")
    suspend fun deleteBinding(uuid: String, streamUuid: String)

    @Query("DELETE FROM StreamBindingUIO WHERE streamUuid = :streamUuid")
    suspend fun deleteByStream(streamUuid: String)

    @Query("SELECT * FROM StreamBindingUIO WHERE streamUuid LIKE :streamUuid")
    fun observeStreamBindings(streamUuid: String): Flow<List<StreamBindingUIO>>

    @Query("SELECT * FROM StreamBindingUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getStreamBinding(uuid: String): StreamBindingUIO?

    @Query("SELECT * FROM StreamBindingUIO WHERE streamUuid LIKE :streamUuid AND userUuid LIKE :userUuid LIMIT 1")
    fun getStreamBinding(streamUuid: String, userUuid: String): StreamBindingUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(streamBindings: List<StreamBindingUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(streamBinding: StreamBindingUIO)
    @Delete
    suspend fun delete(streamBinding: StreamBindingUIO)
    @Query("DELETE FROM StreamBindingUIO")
    suspend fun deleteAll()
}
