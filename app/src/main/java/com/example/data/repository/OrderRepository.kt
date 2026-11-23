package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.Order
import com.example.data.model.OrderStatus
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

class OrderRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeUserOrders(userId: String): Flow<List<Order>> = flow {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before viewing orders.")
        check(uid == userId) { "Signed-in user changed." }
        val path = "orders"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Order::class.java,
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

    fun observeAllOrdersForAdmin(): Flow<List<Order>> = flow {
        val user = auth.currentUser
            ?: throw IllegalStateException("Admin must be signed in.")
        check(AuthManager.isAdminEmail(user.email)) { "Admin privileges required." }
        val uid = user.uid
        val path = "orders"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Order::class.java,
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

    suspend fun createOrder(
        userId: String,
        customerName: String,
        shippingAddress: String,
        cartItems: List<CartItem>,
        subtotalCents: Long,
        shippingCents: Long,
        paymentMethod: String,
        paymentReference: String
    ): Result<String> {
        var docPath = "orders"
        return try {
            val user = auth.currentUser
                ?: error("User must be signed in before placing an order.")
            check(user.uid == userId) { "Signed-in user mismatch." }
            require(cartItems.isNotEmpty()) { "Cart cannot be empty." }
            require(shippingAddress.trim().length in 5..300) {
                "Please enter a valid shipping address (5-300 characters)."
            }

            val cleanName = customerName.trim().ifBlank { user.displayName ?: "Shopper" }.take(100)
            val cleanEmail = (user.email ?: "customer@shopswift.app").trim().take(200)
            val itemCount = cartItems.sumOf { it.quantity }.coerceIn(1L, 500L)
            val summary = cartItems.joinToString(", ") { "${it.quantity}x ${it.title}" }.take(500)
            val totalCents = subtotalCents + shippingCents

            val docRef = db.collection("orders").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to user.uid,
                "customerEmail" to cleanEmail,
                "customerName" to cleanName,
                "shippingAddress" to shippingAddress.trim().take(300),
                "itemsSummary" to summary,
                "itemCount" to itemCount,
                "subtotalCents" to subtotalCents,
                "shippingCents" to shippingCents,
                "totalCents" to totalCents,
                "paymentMethod" to paymentMethod,
                "paymentReference" to paymentReference.take(120),
                "status" to OrderStatus.PAID.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatusAdmin(orderId: String, newStatus: OrderStatus): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            val user = auth.currentUser
                ?: error("Admin must be signed in.")
            check(AuthManager.isAdminEmail(user.email)) {
                "Only ${AuthManager.ADMIN_EMAIL} can update order status."
            }
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            db.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to newStatus.name,
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
}
