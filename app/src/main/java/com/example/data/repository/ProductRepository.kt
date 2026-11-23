package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.remote.AuthManager
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

class ProductRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing catalog.")

    fun observeActiveProducts(): Flow<List<Product>> = flow {
        val uid = requireSignedInUid()
        val path = "products"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
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

    fun observeAllProductsForAdmin(): Flow<List<Product>> = flow {
        val user = auth.currentUser
            ?: throw IllegalStateException("Admin must be signed in.")
        check(AuthManager.isAdminEmail(user.email)) { "Admin privileges required." }
        val uid = user.uid
        val path = "products"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
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

    suspend fun saveProduct(
        existingProductId: String?,
        title: String,
        description: String,
        priceCents: Long,
        category: ProductCategory,
        imageKey: String,
        imageUrl: String?,
        stock: Long,
        active: Boolean
    ): Result<Unit> {
        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        val cleanImageUrl = imageUrl?.trim()?.takeIf { it.startsWith("https://") }
        val targetId = existingProductId?.takeIf { it.isNotBlank() }

        var docPath = if (targetId != null) "products/$targetId" else "products"
        return try {
            val user = auth.currentUser
                ?: error("User must be signed in before modifying products.")
            check(AuthManager.isAdminEmail(user.email)) {
                "Only ${AuthManager.ADMIN_EMAIL} can manage products."
            }
            require(cleanTitle.length in 2..120) { "Title must be 2 to 120 characters." }
            require(cleanDesc.length in 2..1000) { "Description must be 2 to 1000 characters." }
            require(priceCents in 1L..100_000_000L) { "Price must be greater than $0.00." }
            require(stock in 0L..100_000L) { "Stock must be between 0 and 100,000." }

            if (targetId != null) {
                val docRef = db.collection("products").document(targetId)
                docPath = docRef.path
                val updates = mutableMapOf<String, Any>(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "priceCents" to priceCents,
                    "category" to category.displayName,
                    "imageKey" to imageKey.ifBlank { "headphones" },
                    "stock" to stock,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanImageUrl != null) {
                    updates["imageUrl"] = cleanImageUrl
                }
                docRef.update(updates).await()
            } else {
                val docRef = db.collection("products").document()
                docPath = docRef.path
                val payload = mapOf(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "priceCents" to priceCents,
                    "category" to category.displayName,
                    "imageKey" to imageKey.ifBlank { "headphones" },
                    "imageUrl" to cleanImageUrl,
                    "stock" to stock,
                    "active" to active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (targetId != null) OperationType.UPDATE else OperationType.CREATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        val docPath = "products/$productId"
        return try {
            val user = auth.currentUser
                ?: error("User must be signed in before deleting products.")
            check(AuthManager.isAdminEmail(user.email)) {
                "Only ${AuthManager.ADMIN_EMAIL} can delete products."
            }
            require(productId.isNotBlank()) { "Product ID must not be blank." }
            db.collection("products").document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterCatalog(): Result<Unit> {
        val path = "products"
        return try {
            val user = auth.currentUser
                ?: error("Admin must be signed in to seed catalog.")
            check(AuthManager.isAdminEmail(user.email)) {
                "Only ${AuthManager.ADMIN_EMAIL} can seed the catalog."
            }

            val starterProducts = listOf(
                mapOf(
                    "title" to "Aero Studio ANC Headphones",
                    "description" to "Flagship wireless over-ear headphones with adaptive active noise cancellation, spatial acoustics, and 42-hour USB-C battery life.",
                    "priceCents" to 24900L,
                    "category" to ProductCategory.AUDIO.displayName,
                    "imageKey" to "headphones",
                    "stock" to 28L,
                    "active" to true
                ),
                mapOf(
                    "title" to "Chrono S Sapphire Watch",
                    "description" to "Brushed titanium smart chronograph featuring sapphire crystal glass, 10-day battery, heart-rate telemetry, and Italian navy strap.",
                    "priceCents" to 32900L,
                    "category" to ProductCategory.WEARABLES.displayName,
                    "imageKey" to "watch",
                    "stock" to 16L,
                    "active" to true
                ),
                mapOf(
                    "title" to "Nordic Commuter Tech Pack",
                    "description" to "Weatherproof 22L ballistic nylon commuter backpack with padded 16-inch laptop vault and magnetic quick-access front pocket.",
                    "priceCents" to 14500L,
                    "category" to ProductCategory.BAGS.displayName,
                    "imageKey" to "backpack",
                    "stock" to 42L,
                    "active" to true
                ),
                mapOf(
                    "title" to "Pulse Qi2 MagSafe Dock",
                    "description" to "Anodized aluminum 3-in-1 magnetic wireless charging stand delivering 15W fast power for phone, watch, and earbuds.",
                    "priceCents" to 8900L,
                    "category" to ProductCategory.ACCESSORIES.displayName,
                    "imageKey" to "accessories",
                    "stock" to 54L,
                    "active" to true
                ),
                mapOf(
                    "title" to "Lumina Ambient Desk Bar",
                    "description" to "Color-calibrated asymmetric LED monitor light bar with wireless rotary dimmer for glare-free late-night focus.",
                    "priceCents" to 11900L,
                    "category" to ProductCategory.HOME_TECH.displayName,
                    "imageKey" to "hometech",
                    "stock" to 19L,
                    "active" to true
                ),
                mapOf(
                    "title" to "Echo Mini Hi-Fi Earbuds",
                    "description" to "Pocket-sized lossless Bluetooth 5.4 earbuds with dual-driver clarity and IPX5 sweat resistance.",
                    "priceCents" to 12900L,
                    "category" to ProductCategory.AUDIO.displayName,
                    "imageKey" to "headphones",
                    "stock" to 35L,
                    "active" to true
                )
            )

            val batch = db.batch()
            starterProducts.forEach { base ->
                val docRef = db.collection("products").document()
                val payload = base + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
