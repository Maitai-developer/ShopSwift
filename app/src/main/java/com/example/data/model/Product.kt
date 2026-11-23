package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.util.Locale

enum class ProductCategory(val displayName: String) {
    AUDIO("Audio"),
    WEARABLES("Wearables"),
    BAGS("Bags"),
    ACCESSORIES("Accessories"),
    HOME_TECH("Home Tech");

    companion object {
        fun fromRaw(raw: String?): ProductCategory =
            entries.firstOrNull { it.displayName.equals(raw, ignoreCase = true) } ?: AUDIO
    }
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val priceCents: Long = 0L,
    val category: String = ProductCategory.AUDIO.displayName,
    val imageKey: String = "headphones",
    val imageUrl: String? = null,
    val stock: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun formatPriceCents(cents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(cents / 100.0)
}
