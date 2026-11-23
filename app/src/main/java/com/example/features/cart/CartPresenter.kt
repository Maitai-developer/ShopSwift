package com.example.features.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.remote.PaymentIntentRequest
import com.example.data.remote.StripeConfigHelper
import com.example.data.repository.CartRepository
import com.example.data.repository.OrderRepository
import com.example.ui.common.UiState
import com.example.ui.common.asUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class CartPresenter(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val currentUserId: String
) : ViewModel(), CartPresenterContract {

    private val _interactionState = MutableStateFlow(
        CartInteractionState(
            isLiveStripeConfigured = StripeConfigHelper.isLiveStripeConfigured()
        )
    )

    private var pendingRecipientName: String = ""
    private var pendingShippingAddress: String = ""

    private val cartFlow: StateFlow<UiState<List<CartItem>>> =
        cartRepository.observeCart(currentUserId)
            .asUiState(tag = "CartPresenter", fallbackMessage = "Failed to load cart")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    override val uiState: StateFlow<CartUiState> = combine(
        cartFlow,
        _interactionState
    ) { cartState, interaction ->
        CartUiState(
            cartState = cartState,
            isCheckoutSheetOpen = interaction.isCheckoutSheetOpen,
            isProcessingPayment = interaction.isProcessingPayment,
            isLiveStripeConfigured = interaction.isLiveStripeConfigured,
            pendingStripeSheet = interaction.pendingStripeSheet,
            completedOrderId = interaction.completedOrderId,
            feedbackMessage = interaction.feedbackMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CartUiState(isLiveStripeConfigured = StripeConfigHelper.isLiveStripeConfigured())
    )

    override fun incrementQuantity(item: CartItem) {
        viewModelScope.launch {
            val result = cartRepository.updateQuantity(
                userId = currentUserId,
                productId = item.productId,
                newQuantity = (item.quantity + 1L).coerceAtMost(99L)
            )
            if (result.isFailure) {
                _interactionState.update {
                    it.copy(
                        feedbackMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to update quantity"
                    )
                }
            }
        }
    }

    override fun decrementQuantity(item: CartItem) {
        viewModelScope.launch {
            val result = cartRepository.updateQuantity(
                userId = currentUserId,
                productId = item.productId,
                newQuantity = item.quantity - 1L
            )
            if (result.isFailure) {
                _interactionState.update {
                    it.copy(
                        feedbackMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to update quantity"
                    )
                }
            }
        }
    }

    override fun removeItem(productId: String) {
        viewModelScope.launch {
            val result = cartRepository.removeFromCart(currentUserId, productId)
            _interactionState.update {
                if (result.isSuccess) {
                    it.copy(feedbackMessage = "Item removed from cart")
                } else {
                    it.copy(
                        feedbackMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Failed to remove item"
                    )
                }
            }
        }
    }

    override fun openCheckoutSheet() {
        _interactionState.update {
            it.copy(isCheckoutSheetOpen = true, completedOrderId = null, feedbackMessage = null)
        }
    }

    override fun dismissCheckoutSheet() {
        if (_interactionState.value.isProcessingPayment) return
        _interactionState.update { it.copy(isCheckoutSheetOpen = false) }
    }

    override fun submitStripeCheckout(
        recipientName: String,
        shippingAddress: String,
        sandboxCardLast4: String?
    ) {
        val snapshot = uiState.value
        if (snapshot.items.isEmpty()) {
            _interactionState.update { it.copy(feedbackMessage = "Your cart is empty.") }
            return
        }
        val cleanName = recipientName.trim()
        val cleanAddress = shippingAddress.trim()
        if (cleanName.length < 2) {
            _interactionState.update { it.copy(feedbackMessage = "Please enter recipient name.") }
            return
        }
        if (cleanAddress.length < 5) {
            _interactionState.update { it.copy(feedbackMessage = "Please enter full shipping address.") }
            return
        }

        pendingRecipientName = cleanName
        pendingShippingAddress = cleanAddress

        if (StripeConfigHelper.isLiveStripeConfigured()) {
            initiateLiveStripeIntent(snapshot, cleanName)
        } else {
            val suffix = sandboxCardLast4?.takeLast(4)?.ifBlank { "4242" } ?: "4242"
            val sandboxRef = "pi_sandbox_${suffix}_${UUID.randomUUID().toString().take(8)}"
            finalizeOrder(
                paymentMethod = "stripe_sandbox",
                paymentReference = sandboxRef
            )
        }
    }

    private fun initiateLiveStripeIntent(snapshot: CartUiState, customerName: String) {
        viewModelScope.launch {
            _interactionState.update { it.copy(isProcessingPayment = true) }
            try {
                val api = StripeConfigHelper.createBackendApi()
                val email = Firebase.auth.currentUser?.email ?: "customer@shopswift.app"
                val response = api.createPaymentIntent(
                    PaymentIntentRequest(
                        amountCents = snapshot.totalCents,
                        currency = "usd",
                        customerEmail = email,
                        description = "ShopSwift order (${snapshot.itemCount} items) for $customerName"
                    )
                )
                val derivedPi = response.paymentIntentId
                    ?: response.paymentIntentClientSecret.substringBefore("_secret_")
                _interactionState.update {
                    it.copy(
                        isProcessingPayment = false,
                        pendingStripeSheet = StripeSheetLaunchData(
                            clientSecret = response.paymentIntentClientSecret,
                            customerId = response.customerId,
                            ephemeralKey = response.ephemeralKey,
                            paymentIntentId = derivedPi
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _interactionState.update {
                    it.copy(
                        isProcessingPayment = false,
                        feedbackMessage = "Stripe backend error: ${e.localizedMessage ?: "Unable to start PaymentSheet"}"
                    )
                }
            }
        }
    }

    override fun consumePendingStripeSheet() {
        _interactionState.update { it.copy(pendingStripeSheet = null) }
    }

    override fun onStripePaymentSheetCompleted(paymentReference: String) {
        finalizeOrder(
            paymentMethod = "stripe_payment_sheet",
            paymentReference = paymentReference
        )
    }

    override fun onStripePaymentSheetFailed(reason: String) {
        _interactionState.update {
            it.copy(isProcessingPayment = false, feedbackMessage = reason)
        }
    }

    private fun finalizeOrder(paymentMethod: String, paymentReference: String) {
        val snapshot = uiState.value
        viewModelScope.launch {
            _interactionState.update { it.copy(isProcessingPayment = true) }
            val orderResult = orderRepository.createOrder(
                userId = currentUserId,
                customerName = pendingRecipientName,
                shippingAddress = pendingShippingAddress,
                cartItems = snapshot.items,
                subtotalCents = snapshot.subtotalCents,
                shippingCents = snapshot.shippingCents,
                paymentMethod = paymentMethod,
                paymentReference = paymentReference
            )
            if (orderResult.isSuccess) {
                val productIds = snapshot.items.map { it.productId }
                cartRepository.clearCart(currentUserId, productIds)
                _interactionState.update {
                    it.copy(
                        isProcessingPayment = false,
                        isCheckoutSheetOpen = false,
                        completedOrderId = orderResult.getOrNull(),
                        feedbackMessage = "Order placed successfully!"
                    )
                }
            } else {
                _interactionState.update {
                    it.copy(
                        isProcessingPayment = false,
                        feedbackMessage = orderResult.exceptionOrNull()?.localizedMessage
                            ?: "Unable to save order."
                    )
                }
            }
        }
    }

    override fun dismissOrderConfirmation() {
        _interactionState.update { it.copy(completedOrderId = null) }
    }

    override fun clearFeedback() {
        _interactionState.update { it.copy(feedbackMessage = null) }
    }

    private data class CartInteractionState(
        val isCheckoutSheetOpen: Boolean = false,
        val isProcessingPayment: Boolean = false,
        val isLiveStripeConfigured: Boolean = false,
        val pendingStripeSheet: StripeSheetLaunchData? = null,
        val completedOrderId: String? = null,
        val feedbackMessage: String? = null
    )
}
