package com.example.features.cart

import com.example.data.model.CartItem
import com.example.ui.common.UiState
import kotlinx.coroutines.flow.StateFlow

data class StripeSheetLaunchData(
    val clientSecret: String,
    val customerId: String?,
    val ephemeralKey: String?,
    val paymentIntentId: String
)

data class CartUiState(
    val cartState: UiState<List<CartItem>> = UiState.Loading,
    val isCheckoutSheetOpen: Boolean = false,
    val isProcessingPayment: Boolean = false,
    val isLiveStripeConfigured: Boolean = false,
    val pendingStripeSheet: StripeSheetLaunchData? = null,
    val completedOrderId: String? = null,
    val feedbackMessage: String? = null
) {
    val items: List<CartItem>
        get() = (cartState as? UiState.Success)?.data.orEmpty()

    val itemCount: Int
        get() = items.sumOf { it.quantity }.toInt()

    val subtotalCents: Long
        get() = items.sumOf { it.lineTotalCents }

    val shippingCents: Long
        get() = when {
            items.isEmpty() -> 0L
            subtotalCents >= 10_000L -> 0L
            else -> 799L
        }

    val totalCents: Long
        get() = subtotalCents + shippingCents
}

interface CartPresenterContract {
    val uiState: StateFlow<CartUiState>
    fun incrementQuantity(item: CartItem)
    fun decrementQuantity(item: CartItem)
    fun removeItem(productId: String)
    fun openCheckoutSheet()
    fun dismissCheckoutSheet()
    fun submitStripeCheckout(
        recipientName: String,
        shippingAddress: String,
        sandboxCardLast4: String?
    )
    fun onStripePaymentSheetCompleted(paymentReference: String)
    fun onStripePaymentSheetFailed(reason: String)
    fun consumePendingStripeSheet()
    fun dismissOrderConfirmation()
    fun clearFeedback()
}
