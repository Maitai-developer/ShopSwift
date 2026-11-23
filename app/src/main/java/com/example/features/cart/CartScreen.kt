package com.example.features.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.remote.StripeConfigHelper
import com.example.data.repository.CartRepository
import com.example.data.repository.OrderRepository
import com.example.ui.common.UiState
import com.example.ui.theme.EmeraldSuccess
import com.google.firebase.firestore.FirebaseFirestore
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet

@Composable
fun CartScreen(
    currentUserId: String,
    userDisplayName: String,
    onContinueShopping: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val presenter: CartPresenter = viewModel(
        key = "cart_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(
                    context.getString(R.string.firestore_database_id)
                )
                CartPresenter(
                    cartRepository = CartRepository(db),
                    orderRepository = OrderRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val state by presenter.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var lastPaymentIntentId by remember { mutableStateOf("") }

    val paymentSheet = rememberPaymentSheet { result ->
        when (result) {
            is PaymentSheetResult.Completed -> {
                presenter.onStripePaymentSheetCompleted(
                    lastPaymentIntentId.ifBlank { "pi_stripe_verified" }
                )
            }
            is PaymentSheetResult.Canceled -> {
                presenter.onStripePaymentSheetFailed("Payment cancelled.")
            }
            is PaymentSheetResult.Failed -> {
                presenter.onStripePaymentSheetFailed(
                    result.error.localizedMessage ?: "Stripe payment failed."
                )
            }
        }
    }

    LaunchedEffect(state.pendingStripeSheet) {
        val launchData = state.pendingStripeSheet ?: return@LaunchedEffect
        PaymentConfiguration.init(context, StripeConfigHelper.publishableKey())
        lastPaymentIntentId = launchData.paymentIntentId
        val customerConfig = if (!launchData.customerId.isNullOrBlank() && !launchData.ephemeralKey.isNullOrBlank()) {
            PaymentSheet.CustomerConfiguration(
                id = launchData.customerId,
                ephemeralKeySecret = launchData.ephemeralKey
            )
        } else {
            null
        }
        paymentSheet.presentWithPaymentIntent(
            paymentIntentClientSecret = launchData.clientSecret,
            configuration = PaymentSheet.Configuration(
                merchantDisplayName = "ShopSwift",
                customer = customerConfig
            )
        )
        presenter.consumePendingStripeSheet()
    }

    LaunchedEffect(state.feedbackMessage) {
        val message = state.feedbackMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            presenter.clearFeedback()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("cart_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Shopping Cart",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${state.itemCount} ${if (state.itemCount == 1) "item" else "items"} ready for checkout",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (val cartState = state.cartState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = cartState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    if (cartState.data.isEmpty()) {
                        EmptyCartState(onContinueShopping = onContinueShopping)
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isWide = maxWidth >= 720.dp
                            if (isWide) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    LazyColumn(
                                        contentPadding = PaddingValues(bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1.3f)
                                    ) {
                                        items(cartState.data, key = { it.productId }) { item ->
                                            CartItemRow(
                                                item = item,
                                                onIncrement = { presenter.incrementQuantity(item) },
                                                onDecrement = { presenter.decrementQuantity(item) },
                                                onRemove = { presenter.removeItem(item.productId) }
                                            )
                                        }
                                    }
                                    CartOrderSummaryCard(
                                        state = state,
                                        onCheckout = presenter::openCheckoutSheet,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            bottom = 16.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(cartState.data, key = { it.productId }) { item ->
                                            CartItemRow(
                                                item = item,
                                                onIncrement = { presenter.incrementQuantity(item) },
                                                onDecrement = { presenter.decrementQuantity(item) },
                                                onRemove = { presenter.removeItem(item.productId) }
                                            )
                                        }
                                    }
                                    CartOrderSummaryCard(
                                        state = state,
                                        onCheckout = presenter::openCheckoutSheet,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (state.isCheckoutSheetOpen) {
        StripeCheckoutSheet(
            defaultRecipientName = userDisplayName,
            subtotalCents = state.subtotalCents,
            shippingCents = state.shippingCents,
            totalCents = state.totalCents,
            isLiveStripeConfigured = state.isLiveStripeConfigured,
            isProcessing = state.isProcessingPayment,
            onDismiss = presenter::dismissCheckoutSheet,
            onSubmitCheckout = presenter::submitStripeCheckout
        )
    }

    state.completedOrderId?.let { orderId ->
        AlertDialog(
            onDismissRequest = presenter::dismissOrderConfirmation,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = { Text("Order Confirmed!") },
            text = {
                Text(
                    "Your payment was processed and order #${orderId.takeLast(6).uppercase()} is now synced to ShopSwift."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        presenter.dismissOrderConfirmation()
                        onContinueShopping()
                    }
                ) {
                    Text("Continue Shopping")
                }
            }
        )
    }
}
