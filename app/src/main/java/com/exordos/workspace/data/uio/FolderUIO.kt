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
data class FolderUIO(
    @PrimaryKey val uuid: String,
    var title: String,
    var unreadCount: Int,
    val systemType: String,
    val creationDate: String
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM FolderUIO")
    fun observeFolders(): Flow<List<FolderUIO>>

    @Query("SELECT * FROM FolderUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getFolder(uuid: String): FolderUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folders: List<FolderUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderUIO)
    @Delete
    suspend fun delete(folder: FolderUIO)
    @Query("DELETE FROM FolderUIO")
    suspend fun deleteAll()
}
