package com.example.features.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ProductCategory
import com.example.ui.common.ProductArtworkPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorSheet(
    initialDraft: ProductDraft,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProductDraft) -> Unit
) {
    var title by remember(initialDraft) { mutableStateOf(initialDraft.title) }
    var description by remember(initialDraft) { mutableStateOf(initialDraft.description) }
    var priceDollars by remember(initialDraft) { mutableStateOf(initialDraft.priceDollars) }
    var stock by remember(initialDraft) { mutableStateOf(initialDraft.stock) }
    var category by remember(initialDraft) { mutableStateOf(initialDraft.category) }
    var imageKey by remember(initialDraft) { mutableStateOf(initialDraft.imageKey) }
    var imageUrl by remember(initialDraft) { mutableStateOf(initialDraft.imageUrl) }
    var active by remember(initialDraft) { mutableStateOf(initialDraft.active) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialDraft.existingId == null) "Add Product" else "Edit Product",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Product Title") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_product_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_product_desc_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = priceDollars,
                    onValueChange = { priceDollars = it },
                    label = { Text("Price (USD)") },
                    placeholder = { Text("199.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_product_price_input")
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { raw -> stock = raw.filter { it.isDigit() } },
                    label = { Text("Stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_product_stock_input")
                )
            }

            Text("Category", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProductCategory.entries) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item.displayName) }
                    )
                }
            }

            Text("Studio Artwork Preset", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProductArtworkPreset.entries) { preset ->
                    FilterChip(
                        selected = imageKey == preset.key,
                        onClick = { imageKey = preset.key },
                        label = { Text(preset.label) }
                    )
                }
            }

            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Custom HTTPS Image URL (Optional)") },
                placeholder = { Text("https://...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Visible in Storefront", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Inactive items stay hidden from shoppers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = { active = it }
                )
            }

            Button(
                onClick = {
                    onSave(
                        initialDraft.copy(
                            title = title,
                            description = description,
                            priceDollars = priceDollars,
                            category = category,
                            imageKey = imageKey,
                            imageUrl = imageUrl,
                            stock = stock,
                            active = active
                        )
                    )
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("admin_save_product_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (initialDraft.existingId == null) "Publish Product" else "Save Changes",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
