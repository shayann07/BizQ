package com.example.finalproject.data.loca_db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.finalproject.data.models.User

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY username ASC")
    fun getAllUsers(): LiveData<List<User>>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUserByUid(uid: String): User?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    fun getUserByUidLiveData(uid: String): LiveData<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("DELETE FROM users WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)
}
