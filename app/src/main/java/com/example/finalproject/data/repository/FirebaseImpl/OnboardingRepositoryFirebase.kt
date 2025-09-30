package com.example.finalproject.data.repository.FirebaseImpl

import android.content.Context
import com.example.finalproject.data.models.onboarding.OnboardingDraft
import com.example.finalproject.data.repository.onboarding.OnboardingRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Stores the entire draft as a single JSON string in Firestore to avoid no-arg constructor issues.
 * Also mirrors to SharedPreferences locally for fast resume after app restarts.
 *
 * Firestore:
 *   Collection: "onboarding_drafts"
 *   Document:   {uid}
 *   Fields:
 *      - "json" (String)    -> full OnboardingDraft as JSON
 *      - "updatedAt" (TS)   -> server timestamp
 */
@Singleton
class OnboardingRepositoryFirebase @Inject constructor(
    @ApplicationContext private val context: Context
) : OnboardingRepository {

    private val prefs = context.getSharedPreferences("onboarding_draft_store", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // ---------------------- LOCAL ----------------------

    override suspend fun saveDraftLocal(draft: OnboardingDraft) = withContext(Dispatchers.IO) {
        if (draft.uid.isBlank()) return@withContext
        prefs.edit().putString(key(draft.uid), gson.toJson(draft)).apply()
    }

    override suspend fun getDraftLocal(uid: String): OnboardingDraft? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null
        val json = prefs.getString(key(uid), null) ?: return@withContext null
        runCatching { gson.fromJson(json, OnboardingDraft::class.java) }.getOrNull()
    }

    override suspend fun deleteDraftLocal(uid: String) = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext
        prefs.edit().remove(key(uid)).apply()
    }

    // ---------------------- REMOTE (FIRESTORE) ----------------------

    override suspend fun saveDraftRemote(draft: OnboardingDraft): Any? = withContext(Dispatchers.IO) {
        if (draft.uid.isBlank()) return@withContext null
        val data = mapOf(
            "json" to gson.toJson(draft),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore.collection(COLLECTION)
            .document(draft.uid)
            .set(data, SetOptions.merge())
            .await()
    }

    override suspend fun getDraftRemote(uid: String): OnboardingDraft? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null
        val snap = firestore.collection(COLLECTION).document(uid).get().await()
        val json = snap.getString("json") ?: return@withContext null
        runCatching { gson.fromJson(json, OnboardingDraft::class.java) }.getOrNull()
    }

    // ---------------------- COMBINED ----------------------

    override suspend fun getOrFetchDraft(uid: String): OnboardingDraft? {
        if (uid.isBlank()) return null
        getDraftLocal(uid)?.let { return it }
        val remote = getDraftRemote(uid)
        if (remote != null) saveDraftLocal(remote)
        return remote
    }

    private fun key(uid: String) = "draft_$uid"

    private companion object {
        private const val COLLECTION = "onboarding_drafts"
    }
}
