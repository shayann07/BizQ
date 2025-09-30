// AuthRepositoryImpl.kt
package com.example.finalproject.data.repository

import com.example.finalproject.data.loca_db.UserDao
import com.example.finalproject.data.models.User
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao
) : AuthRepository {

    override suspend fun login(email: String, password: String): Resource<User> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            val uid = auth.currentUser?.uid ?: return Resource.Error("No UID after login")
            // Prefer local cached user, else try Firestore
            val local = userDao.getUserByUid(uid)
            if (local != null) return Resource.Success(local)

            val snap = firestore.collection("users").document(uid).get().await()
            if (snap.exists()) {
                val user = snap.toObject(User::class.java)?.copy(uid = uid)
                    ?: return Resource.Error("Invalid user document")
                // cache locally
                userDao.insert(user)
                Resource.Success(user)
            } else {
                Resource.Error("Profile not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Login failed")
        }
    }

    override suspend fun createUser(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Resource<User> {
        return try {
            // Create Firebase account
            auth.createUserWithEmailAndPassword(email, password).await()
            val uid = auth.currentUser?.uid ?: return Resource.Error("No UID after create")

            // Build user WITHOUT password for storage
            val user = User(
                uid = uid,
                username = name,
                email = email,
                phone = phone,
                password = "" // never save password locally or in Firestore
            )

            // Save to Firestore + Room
            saveProfileFirestore(user)
            saveProfileLocal(user)

            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Create failed")
        }
    }

    override suspend fun currentUser(): Resource<User> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("No logged-in user")
        return try {
            // Try local first
            val local = userDao.getUserByUid(uid)
            if (local != null) return Resource.Success(local)

            // Fallback to Firestore
            val snap = firestore.collection("users").document(uid).get().await()
            if (snap.exists()) {
                val user = snap.toObject(User::class.java)?.copy(uid = uid)
                    ?: return Resource.Error("Invalid user document")
                userDao.insert(user)
                Resource.Success(user)
            } else {
                Resource.Error("Profile not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get current user")
        }
    }

    override suspend fun userProfileExists(uid: String): Boolean {
        try {
            // Local fast check
            val local = userDao.getUserByUid(uid)
            if (local != null) return true

            // Remote check
            val snap = firestore.collection("users").document(uid).get().await()
            if (snap.exists()) return true
        } catch (_: Exception) { /* ignore and return false */ }
        return false
    }

    override suspend fun saveProfileFirestore(user: User) {
        firestore.collection("users").document(user.uid)
            .set(user.copy(password = "")) // enforce no password
            .await()
    }

    override suspend fun saveProfileLocal(user: User) {
        userDao.insert(user.copy(password = "")) // enforce no password
    }
}
