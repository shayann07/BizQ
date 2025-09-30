// app/src/main/java/com/example/finalproject/data/repository/FirebaseImpl/AuthRepositoryFirebase.kt
package com.example.finalproject.data.repository.FirebaseImpl

import com.example.finalproject.data.models.onboarding.OnboardingState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.finalproject.data.repository.AuthRepository
import com.example.finalproject.data.models.User
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.safeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.example.finalproject.data.loca_db.UserDao
import com.example.finalproject.data.repository.onboarding.OnboardingStateRepository
import com.example.finalproject.data.repository.onboarding.OnboardingRepository

@Singleton
class AuthRepositoryFirebase @Inject constructor(
    private val userDao: UserDao,
    private val onboardingStateRepo: OnboardingStateRepository, // progress/state
    private val onboardingDraftRepo: OnboardingRepository        // draft
) : AuthRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val userRef = FirebaseFirestore.getInstance().collection("users")

    override suspend fun currentUser(): Resource<User> = withContext(Dispatchers.IO) {
        safeCall {
            val current = firebaseAuth.currentUser
                ?: return@safeCall Resource.Error("No user logged in")

            // Try local first
            val local = userDao.getUserByUid(current.uid)
            if (local != null) return@safeCall Resource.Success(local)

            // Fallback to Firestore
            val snap = userRef.document(current.uid).get().await()
            if (!snap.exists()) return@safeCall Resource.Error("User document not found")

            val remote = snap.toObject(User::class.java)?.copy(uid = current.uid)
                ?: return@safeCall Resource.Error("Invalid user document")

            // Cache locally
            userDao.insert(remote.copy(password = ""))

            Resource.Success(remote)
        }
    }

    // -------------------------
    // NEW / IMPLEMENTED METHODS
    // -------------------------
    override suspend fun userProfileExists(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Fast local check
            val local = userDao.getUserByUid(uid)
            if (local != null) return@withContext true

            // Remote check
            val snap = userRef.document(uid).get().await()
            snap.exists()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun saveProfileFirestore(user: User) {
        withContext(Dispatchers.IO) {
            val id = if (user.uid.isNotBlank()) user.uid else (firebaseAuth.currentUser?.uid.orEmpty())
            require(id.isNotBlank()) { "No UID available for Firestore write" }

            // Never store password in Firestore; also ensure uid field is present
            userRef.document(id)
                .set(user.copy(uid = id, password = ""))
                .await()

            // explicitly Unit by using block body (nothing returned)
        }
    }


    override suspend fun saveProfileLocal(user: User) {
        withContext(Dispatchers.IO) {
            // Room insert returns Long — ignore it so our function returns Unit
            userDao.insert(user.copy(password = ""))
        }
    }

    // -------------------------

    override suspend fun login(email: String, password: String): Resource<User> = withContext(Dispatchers.IO) {
        safeCall {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return@safeCall Resource.Error("Login failed: no uid")

            // Prefer local
            val local = userDao.getUserByUid(uid)
            if (local != null) {
                // Hydrate local onboarding
                onboardingStateRepo.fetchRemote(uid)
                onboardingDraftRepo.getOrFetchDraft(uid)
                return@safeCall Resource.Success(local)
            }

            // Fetch remote
            val snap = userRef.document(uid).get().await()
            if (!snap.exists()) return@safeCall Resource.Error("User document not found")

            val remote = snap.toObject(User::class.java)?.copy(uid = uid)
                ?: return@safeCall Resource.Error("Invalid user document")

            // Mirror locally
            userDao.insert(remote.copy(password = ""))

            // Hydrate local onboarding state & draft
            onboardingStateRepo.fetchRemote(uid)
            onboardingDraftRepo.getOrFetchDraft(uid)

            Resource.Success(remote)
        }
    }

    override suspend fun createUser(
        userName: String,
        userEmail: String,
        userPhone: String,
        userLoginPass: String
    ): Resource<User> = withContext(Dispatchers.IO) {
        safeCall {
            val registration = firebaseAuth.createUserWithEmailAndPassword(userEmail, userLoginPass).await()
            val uid = registration.user?.uid ?: return@safeCall Resource.Error("Registration failed: no UID")

            // Build user WITH uid and WITHOUT password
            val newUser = User(
                uid = uid,
                username = userName,
                email = userEmail,
                phone = userPhone,
                password = "" // never store password
            )

            // Save to Firestore + Room via the new helpers
            saveProfileFirestore(newUser)
            saveProfileLocal(newUser)

            // Initialize onboarding state (Step 1)
            val initState = OnboardingState(uid = uid, currentStep = 1, isCompleted = false)
            onboardingStateRepo.upsertLocal(initState)
            onboardingStateRepo.upsertRemote(initState)

            Resource.Success(newUser)
        }
    }

    fun logout() {
        firebaseAuth.signOut()
    }
}
