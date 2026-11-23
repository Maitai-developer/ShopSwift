package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class OrderStatus(val label: String) {
    PAID("Paid"),
    PROCESSING("Processing"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered");

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PAID
    }
}

@Keep
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerEmail: String = "",
    val customerName: String = "",
    val shippingAddress: String = "",
    val itemsSummary: String = "",
    val itemCount: Long = 0L,
    val subtotalCents: Long = 0L,
    val shippingCents: Long = 0L,
    val totalCents: Long = 0L,
    val paymentMethod: String = "stripe_sandbox",
    val paymentReference: String = "",
    val status: String = OrderStatus.PAID.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
