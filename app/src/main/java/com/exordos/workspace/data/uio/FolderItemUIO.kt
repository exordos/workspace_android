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
data class FolderItemUIO(
    @PrimaryKey val uuid: String,
    val folder: String,
    val streamUuid: String,
    val chatType: String,
    var unreadCount: Int,
)

@Dao
interface FolderItemDao {

    @Query("DELETE FROM FolderItemUIO WHERE folder = :folderUuid")
    suspend fun deleteByFolder(folderUuid: String)

    @Query("DELETE FROM FolderItemUIO WHERE streamUuid = :streamUuid")
    suspend fun deleteByStream(streamUuid: String)

    @Query("SELECT * FROM FolderItemUIO WHERE folder LIKE :folderUuid")
    fun observeFolderItems(folderUuid: String): Flow<List<FolderItemUIO>>

    @Query("SELECT * FROM FolderItemUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getFolderItem(uuid: String): FolderItemUIO?

    @Query("SELECT * FROM FolderItemUIO WHERE folder LIKE :folderUuid")
    fun getFolderItems(folderUuid: String): List<FolderItemUIO>

    @Query("SELECT * FROM FolderItemUIO WHERE folder LIKE :folderUuid AND streamUuid LIKE :streamUuid LIMIT 1")
    fun getFolderItem(folderUuid: String, streamUuid: String): FolderItemUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folderItems: List<FolderItemUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folderItem: FolderItemUIO)
    @Delete
    suspend fun delete(folderItem: FolderItemUIO)
    @Query("DELETE FROM FolderItemUIO")
    suspend fun deleteAll()
}
