package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun syncCurrentUserProfile(user: FirebaseUser): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val uid = auth.currentUser?.uid
                ?: error("User must be signed in before syncing profile.")
            check(uid == user.uid) { "Authenticated user changed." }

            val docRef = db.collection("users").document(uid)
            val displayName = (user.displayName ?: user.email?.substringBefore("@") ?: "Shopper")
                .trim()
                .take(100)
                .ifBlank { "Shopper" }
            val email = (user.email ?: "user@shopswift.app").trim().take(200)
            val photoUrl = user.photoUrl?.toString()?.take(500)?.takeIf { it.isNotBlank() }

            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val updates = mutableMapOf<String, Any>(
                        "displayName" to displayName,
                        "email" to email,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (photoUrl != null) {
                        updates["photoUrl"] = photoUrl
                    }
                    transaction.update(docRef, updates)
                } else {
                    val payload = mapOf(
                        "userId" to uid,
                        "displayName" to displayName,
                        "email" to email,
                        "photoUrl" to photoUrl,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    transaction.set(docRef, payload)
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
