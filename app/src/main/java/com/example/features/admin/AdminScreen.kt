package com.example.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.formatPriceCents
import com.example.data.remote.AuthManager
import com.example.data.repository.OrderRepository
import com.example.data.repository.ProductRepository
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.DeepRoyalBlueDark
import com.example.ui.theme.PureWhite
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AdminScreen(
    currentUserId: String,
    userEmail: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val presenter: AdminPresenter = viewModel(
        key = "admin_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(
                    context.getString(R.string.firestore_database_id)
                )
                AdminPresenter(
                    productRepository = ProductRepository(db),
                    orderRepository = OrderRepository(db),
                    userEmail = userEmail
                )
            }
        }
    )

    val state by presenter.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedbackMessage) {
        val message = state.feedbackMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            presenter.clearFeedback()
        }
    }

    if (!state.isAuthorized) {
        AccessDeniedAdminView(userEmail = userEmail, modifier = modifier)
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AdminDashboardHeader(state = state)

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                AdminTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = state.selectedTab == tab,
                        onClick = { presenter.onTabSelected(tab) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AdminTab.entries.size
                        ),
                        label = {
                            val badge = when (tab) {
                                AdminTab.INVENTORY -> "${state.products.size}"
                                AdminTab.ORDERS -> "${state.orders.size}"
                            }
                            Text("${tab.label} ($badge)")
                        }
                    )
                }
            }

            when (state.selectedTab) {
                AdminTab.INVENTORY -> {
                    AdminInventoryTab(
                        productsState = state.productsState,
                        isMutating = state.isMutating,
                        onSeedCatalog = presenter::onSeedStarterCatalog,
                        onEditProduct = presenter::onEditProductClicked,
                        onDeleteProduct = presenter::onRequestDeleteProduct
                    )
                }

                AdminTab.ORDERS -> {
                    AdminOrdersTab(
                        ordersState = state.ordersState,
                        onUpdateStatus = presenter::onUpdateOrderStatus
                    )
                }
            }
        }

        if (state.selectedTab == AdminTab.INVENTORY) {
            FloatingActionButton(
                onClick = presenter::onAddProductClicked,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = PureWhite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("admin_add_product_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add new product")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    state.editorDraft?.let { draft ->
        ProductEditorSheet(
            initialDraft = draft,
            isSaving = state.isMutating,
            onDismiss = presenter::onDismissEditor,
            onSave = presenter::onSaveProduct
        )
    }

    state.productPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { presenter.onRequestDeleteProduct(null) },
            title = { Text("Delete Product?") },
            text = { Text("Remove \"${target.title}\" permanently from the ShopSwift catalog?") },
            confirmButton = {
                Button(
                    onClick = presenter::onConfirmDeleteProduct,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { presenter.onRequestDeleteProduct(null) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminDashboardHeader(state: AdminUiState) {
    Surface(
        color = DeepRoyalBlue,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(DeepRoyalBlueDark, DeepRoyalBlue, CobaltAccent)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Admin Console",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureWhite
                    )
                    Text(
                        text = AuthManager.ADMIN_EMAIL,
                        style = MaterialTheme.typography.labelMedium,
                        color = PureWhite.copy(alpha = 0.8f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminKpiChip(
                    label = "Revenue",
                    value = formatPriceCents(state.totalRevenueCents),
                    modifier = Modifier.weight(1f)
                )
                AdminKpiChip(
                    label = "Orders",
                    value = state.orders.size.toString(),
                    modifier = Modifier.weight(0.7f)
                )
                AdminKpiChip(
                    label = "Low Stock",
                    value = state.lowStockCount.toString(),
                    modifier = Modifier.weight(0.75f)
                )
            }
        }
    }
}

@Composable
private fun AdminKpiChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PureWhite.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = PureWhite.copy(alpha = 0.78f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccessDeniedAdminView(
    userEmail: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LockPerson,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Admin Access Restricted",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Signed in as ${userEmail ?: "unknown"}. Only ${AuthManager.ADMIN_EMAIL} is authorized to manage products and view store order summaries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
