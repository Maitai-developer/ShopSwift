package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CartRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before modifying cart.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user cart operation." }
        return uid
    }

    fun observeCart(userId: String): Flow<List<CartItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/cart"
        emitAll(
            db.collection("users").document(uid).collection("cart")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CartItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addToCart(userId: String, product: Product, quantityToAdd: Long = 1L): Result<Unit> {
        var docPath = "users/$userId/cart/${product.id}"
        return try {
            val uid = requireUserId(userId)
            require(product.id.isNotBlank()) { "Product ID must not be blank." }
            require(quantityToAdd in 1L..99L) { "Quantity must be between 1 and 99." }

            val docRef = db.collection("users").document(uid)
                .collection("cart").document(product.id)
            docPath = docRef.path

            val cleanImageUrl = product.imageUrl?.trim()?.takeIf { it.startsWith("https://") }

            db.runTransaction { transaction ->
                val existingSnap = transaction.get(docRef)
                if (existingSnap.exists()) {
                    val currentQty = existingSnap.getLong("quantity") ?: 0L
                    val nextQty = (currentQty + quantityToAdd).coerceIn(1L, 99L)
                    val updates = mutableMapOf<String, Any>(
                        "title" to product.title.take(120),
                        "priceCents" to product.priceCents,
                        "quantity" to nextQty,
                        "category" to product.category.take(50),
                        "imageKey" to product.imageKey.take(50),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (cleanImageUrl != null) {
                        updates["imageUrl"] = cleanImageUrl
                    }
                    transaction.update(docRef, updates)
                } else {
                    val payload = mapOf(
                        "title" to product.title.take(120),
                        "priceCents" to product.priceCents,
                        "quantity" to quantityToAdd.coerceIn(1L, 99L),
                        "category" to product.category.take(50),
                        "imageKey" to product.imageKey.take(50),
                        "imageUrl" to cleanImageUrl,
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

    suspend fun updateQuantity(userId: String, productId: String, newQuantity: Long): Result<Unit> {
        if (newQuantity <= 0L) {
            return removeFromCart(userId, productId)
        }
        val docPath = "users/$userId/cart/$productId"
        return try {
            val uid = requireUserId(userId)
            require(productId.isNotBlank()) { "Product ID must not be blank." }
            val clampedQty = newQuantity.coerceIn(1L, 99L)
            db.collection("users").document(uid)
                .collection("cart").document(productId)
                .update(
                    mapOf(
                        "quantity" to clampedQty,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFromCart(userId: String, productId: String): Result<Unit> {
        val docPath = "users/$userId/cart/$productId"
        return try {
            val uid = requireUserId(userId)
            require(productId.isNotBlank()) { "Product ID must not be blank." }
            db.collection("users").document(uid)
                .collection("cart").document(productId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearCart(userId: String, productIds: List<String>): Result<Unit> {
        val path = "users/$userId/cart"
        return try {
            val uid = requireUserId(userId)
            if (productIds.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            val cartCol = db.collection("users").document(uid).collection("cart")
            productIds.forEach { id ->
                if (id.isNotBlank()) {
                    batch.delete(cartCol.document(id))
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
