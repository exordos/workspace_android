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
data class UserUIO(
    var email: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    val username: String,
    @PrimaryKey val uuid: String,
    var statusEmoji: String? = null,
    var statusText: String? = null,
    var status: String,
    var avatar: String
) {
    fun displayableName(): String {
        var displayName = ""
        if (firstName != null) {
            displayName += firstName
        }

        if (lastName != null) {
            if (!displayName.isEmpty()) {
                displayName += " "
            }
            displayName += lastName
        }

        if (displayName.isEmpty()) {
            displayName += username
        }

        return  displayName
    }

    fun statusDescription(): String {
        if (status == "active") {
            return "В сети"
        } else {
            return "Не в сети"
        }
    }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM UserUIO")
    fun observeUsers(): Flow<List<UserUIO>>

    @Query("SELECT * FROM UserUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getUser(uuid: String): UserUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(users: List<UserUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserUIO)
    @Delete
    suspend fun delete(user: UserUIO)
    @Query("DELETE FROM UserUIO")
    suspend fun deleteAll()
}
