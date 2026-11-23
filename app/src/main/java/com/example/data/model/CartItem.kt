package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CartItem(
    @DocumentId val productId: String = "",
    val title: String = "",
    val priceCents: Long = 0L,
    val quantity: Long = 1L,
    val category: String = ProductCategory.AUDIO.displayName,
    val imageKey: String = "headphones",
    val imageUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val lineTotalCents: Long
        get() = priceCents * quantity
}
