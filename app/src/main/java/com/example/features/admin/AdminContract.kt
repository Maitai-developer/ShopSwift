package com.example.features.admin

import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.ui.common.UiState
import kotlinx.coroutines.flow.StateFlow

enum class AdminTab(val label: String) {
    INVENTORY("Inventory"),
    ORDERS("Orders")
}

data class ProductDraft(
    val existingId: String? = null,
    val title: String = "",
    val description: String = "",
    val priceDollars: String = "",
    val category: ProductCategory = ProductCategory.AUDIO,
    val imageKey: String = "headphones",
    val imageUrl: String = "",
    val stock: String = "10",
    val active: Boolean = true
)

data class AdminUiState(
    val isAuthorized: Boolean = false,
    val selectedTab: AdminTab = AdminTab.INVENTORY,
    val productsState: UiState<List<Product>> = UiState.Loading,
    val ordersState: UiState<List<Order>> = UiState.Loading,
    val editorDraft: ProductDraft? = null,
    val productPendingDelete: Product? = null,
    val isMutating: Boolean = false,
    val feedbackMessage: String? = null
) {
    val products: List<Product>
        get() = (productsState as? UiState.Success)?.data.orEmpty()

    val orders: List<Order>
        get() = (ordersState as? UiState.Success)?.data.orEmpty()

    val totalRevenueCents: Long
        get() = orders.sumOf { it.totalCents }

    val lowStockCount: Int
        get() = products.count { it.stock <= 5L }
}

interface AdminPresenterContract {
    val uiState: StateFlow<AdminUiState>
    fun onTabSelected(tab: AdminTab)
    fun onAddProductClicked()
    fun onEditProductClicked(product: Product)
    fun onDismissEditor()
    fun onSaveProduct(draft: ProductDraft)
    fun onRequestDeleteProduct(product: Product?)
    fun onConfirmDeleteProduct()
    fun onSeedStarterCatalog()
    fun onUpdateOrderStatus(orderId: String, newStatus: OrderStatus)
    fun clearFeedback()
}
