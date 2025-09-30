// AuthRepository.kt
package com.example.finalproject.data.repository

import com.example.finalproject.data.models.User
import com.example.finalproject.utils.Resource

interface AuthRepository {
    suspend fun login(email: String, password: String): Resource<User>
    suspend fun createUser(name: String, email: String, phone: String, password: String): Resource<User>
    suspend fun currentUser(): Resource<User>

    // NEW
    suspend fun userProfileExists(uid: String): Boolean
    suspend fun saveProfileFirestore(user: User)
    suspend fun saveProfileLocal(user: User)
}
