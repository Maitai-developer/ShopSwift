package com.example.features.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.formatPriceCents
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.EmeraldSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StripeCheckoutSheet(
    defaultRecipientName: String,
    subtotalCents: Long,
    shippingCents: Long,
    totalCents: Long,
    isLiveStripeConfigured: Boolean,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSubmitCheckout: (recipientName: String, shippingAddress: String, sandboxLast4: String?) -> Unit
) {
    var recipientName by remember { mutableStateOf(defaultRecipientName) }
    var shippingAddress by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvc by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column {
                    Text(
                        text = "Stripe Secure Checkout",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isLiveStripeConfigured) {
                            "Live Stripe PaymentSheet enabled"
                        } else {
                            "Stripe Sandbox Preview Mode"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldSuccess
                    )
                }
            }

            if (!isLiveStripeConfigured) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Set STRIPE_PUBLISHABLE_KEY and STRIPE_BACKEND_URL in AI Studio Secrets for live Stripe PaymentSheet charges. Sandbox validation is active for preview testing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            OutlinedTextField(
                value = recipientName,
                onValueChange = {
                    recipientName = it
                    validationError = null
                },
                label = { Text("Recipient Full Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkout_name_input")
            )

            OutlinedTextField(
                value = shippingAddress,
                onValueChange = {
                    shippingAddress = it
                    validationError = null
                },
                label = { Text("Shipping Address") },
                placeholder = { Text("123 Market St, San Francisco, CA 94105") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("checkout_address_input")
            )

            if (!isLiveStripeConfigured) {
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { raw ->
                        cardNumber = raw.filter { it.isDigit() }.take(16)
                        validationError = null
                    },
                    label = { Text("Card Number (Test 4242...)") },
                    leadingIcon = {
                        Icon(Icons.Rounded.CreditCard, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_card_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = cardExpiry,
                        onValueChange = { raw ->
                            cardExpiry = raw.take(5)
                            validationError = null
                        },
                        label = { Text("MM/YY") },
                        placeholder = { Text("12/28") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cardCvc,
                        onValueChange = { raw ->
                            cardCvc = raw.filter { it.isDigit() }.take(4)
                            validationError = null
                        },
                        label = { Text("CVC") },
                        placeholder = { Text("424") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SummaryLine("Subtotal", formatPriceCents(subtotalCents))
                    SummaryLine(
                        "Shipping",
                        if (shippingCents == 0L) "FREE" else formatPriceCents(shippingCents)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Due",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatPriceCents(totalCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DeepRoyalBlue
                        )
                    }
                }
            }

            if (!validationError.isNullOrBlank()) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val trimmedName = recipientName.trim()
                    val trimmedAddress = shippingAddress.trim()
                    if (trimmedName.length < 2) {
                        validationError = "Please enter the recipient's name."
                        return@Button
                    }
                    if (trimmedAddress.length < 5) {
                        validationError = "Please enter a valid shipping address (at least 5 characters)."
                        return@Button
                    }
                    if (!isLiveStripeConfigured) {
                        val digits = cardNumber.filter { it.isDigit() }
                        if (digits.length < 12 || !isValidLuhn(digits)) {
                            validationError = "Enter a valid card number (e.g. Stripe test 4242424242424242)."
                            return@Button
                        }
                        if (!cardExpiry.matches(Regex("^\\d{2}/\\d{2}$"))) {
                            validationError = "Enter expiry as MM/YY (e.g. 12/28)."
                            return@Button
                        }
                        if (cardCvc.length !in 3..4) {
                            validationError = "Enter a valid 3 or 4 digit CVC."
                            return@Button
                        }
                    }
                    onSubmitCheckout(
                        trimmedName,
                        trimmedAddress,
                        cardNumber.takeLast(4).ifBlank { null }
                    )
                },
                enabled = !isProcessing,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_stripe_payment_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Authorizing with Stripe...")
                } else {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLiveStripeConfigured) {
                            "Pay ${formatPriceCents(totalCents)} with Stripe"
                        } else {
                            "Complete Sandbox Order • ${formatPriceCents(totalCents)}"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun isValidLuhn(digits: String): Boolean {
    var sum = 0
    var alternate = false
    for (i in digits.length - 1 downTo 0) {
        var n = digits[i].digitToIntOrNull() ?: return false
        if (alternate) {
            n *= 2
            if (n > 9) n -= 9
        }
        sum += n
        alternate = !alternate
    }
    return sum % 10 == 0
}
