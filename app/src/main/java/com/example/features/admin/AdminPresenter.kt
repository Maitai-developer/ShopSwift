package com.example.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.remote.AuthManager
import com.example.data.repository.OrderRepository
import com.example.data.repository.ProductRepository
import com.example.ui.common.UiState
import com.example.ui.common.asUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class AdminPresenter(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    userEmail: String?
) : ViewModel(), AdminPresenterContract {

    private val isAuthorized = AuthManager.isAdminEmail(userEmail)
    private val _uiControl = MutableStateFlow(AdminControlState())

    private val productsFlow: StateFlow<UiState<List<Product>>> =
        if (isAuthorized) {
            productRepository.observeAllProductsForAdmin()
                .asUiState(tag = "AdminProducts", fallbackMessage = "Unable to load inventory")
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = UiState.Loading
                )
        } else {
            flowOf<UiState<List<Product>>>(UiState.Error("Unauthorized")).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Error("Unauthorized")
            )
        }

    private val ordersFlow: StateFlow<UiState<List<Order>>> =
        if (isAuthorized) {
            orderRepository.observeAllOrdersForAdmin()
                .asUiState(tag = "AdminOrders", fallbackMessage = "Unable to load orders")
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = UiState.Loading
                )
        } else {
            flowOf<UiState<List<Order>>>(UiState.Error("Unauthorized")).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Error("Unauthorized")
            )
        }

    override val uiState: StateFlow<AdminUiState> = combine(
        productsFlow,
        ordersFlow,
        _uiControl
    ) { productsState, ordersState, control ->
        AdminUiState(
            isAuthorized = isAuthorized,
            selectedTab = control.selectedTab,
            productsState = productsState,
            ordersState = ordersState,
            editorDraft = control.editorDraft,
            productPendingDelete = control.productPendingDelete,
            isMutating = control.isMutating,
            feedbackMessage = control.feedbackMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AdminUiState(isAuthorized = isAuthorized)
    )

    override fun onTabSelected(tab: AdminTab) {
        _uiControl.update { it.copy(selectedTab = tab) }
    }

    override fun onAddProductClicked() {
        _uiControl.update { it.copy(editorDraft = ProductDraft()) }
    }

    override fun onEditProductClicked(product: Product) {
        val dollars = String.format(java.util.Locale.US, "%.2f", product.priceCents / 100.0)
        _uiControl.update {
            it.copy(
                editorDraft = ProductDraft(
                    existingId = product.id,
                    title = product.title,
                    description = product.description,
                    priceDollars = dollars,
                    category = ProductCategory.fromRaw(product.category),
                    imageKey = product.imageKey,
                    imageUrl = product.imageUrl.orEmpty(),
                    stock = product.stock.toString(),
                    active = product.active
                )
            )
        }
    }

    override fun onDismissEditor() {
        if (_uiControl.value.isMutating) return
        _uiControl.update { it.copy(editorDraft = null) }
    }

    override fun onSaveProduct(draft: ProductDraft) {
        if (!isAuthorized) return
        val parsedDollars = draft.priceDollars.trim().toDoubleOrNull()
        val parsedStock = draft.stock.trim().toLongOrNull()

        if (draft.title.trim().length !in 2..120) {
            _uiControl.update { it.copy(feedbackMessage = "Title must be 2-120 characters.") }
            return
        }
        if (draft.description.trim().length !in 2..1000) {
            _uiControl.update { it.copy(feedbackMessage = "Description must be 2-1000 characters.") }
            return
        }
        if (parsedDollars == null || parsedDollars <= 0.0) {
            _uiControl.update { it.copy(feedbackMessage = "Enter a valid price greater than $0.00.") }
            return
        }
        if (parsedStock == null || parsedStock !in 0L..100_000L) {
            _uiControl.update { it.copy(feedbackMessage = "Stock must be between 0 and 100,000.") }
            return
        }
        val cleanImageUrl = draft.imageUrl.trim()
        if (cleanImageUrl.isNotEmpty() && !cleanImageUrl.startsWith("https://")) {
            _uiControl.update { it.copy(feedbackMessage = "Custom image URL must start with https://") }
            return
        }

        val priceCents = (parsedDollars * 100.0).roundToLong().coerceAtLeast(1L)

        viewModelScope.launch {
            _uiControl.update { it.copy(isMutating = true) }
            val result = productRepository.saveProduct(
                existingProductId = draft.existingId,
                title = draft.title,
                description = draft.description,
                priceCents = priceCents,
                category = draft.category,
                imageKey = draft.imageKey,
                imageUrl = cleanImageUrl.ifBlank { null },
                stock = parsedStock,
                active = draft.active
            )
            _uiControl.update {
                if (result.isSuccess) {
                    it.copy(
                        isMutating = false,
                        editorDraft = null,
                        feedbackMessage = if (draft.existingId == null) "Product created" else "Product updated"
                    )
                } else {
                    it.copy(
                        isMutating = false,
                        feedbackMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to save product"
                    )
                }
            }
        }
    }

    override fun onRequestDeleteProduct(product: Product?) {
        _uiControl.update { it.copy(productPendingDelete = product) }
    }

    override fun onConfirmDeleteProduct() {
        val target = _uiControl.value.productPendingDelete ?: return
        viewModelScope.launch {
            _uiControl.update { it.copy(isMutating = true) }
            val result = productRepository.deleteProduct(target.id)
            _uiControl.update {
                if (result.isSuccess) {
                    it.copy(
                        isMutating = false,
                        productPendingDelete = null,
                        feedbackMessage = "Deleted ${target.title}"
                    )
                } else {
                    it.copy(
                        isMutating = false,
                        productPendingDelete = null,
                        feedbackMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Failed to delete product"
                    )
                }
            }
        }
    }

    override fun onSeedStarterCatalog() {
        if (!isAuthorized || _uiControl.value.isMutating) return
        viewModelScope.launch {
            _uiControl.update { it.copy(isMutating = true) }
            val result = productRepository.seedStarterCatalog()
            _uiControl.update {
                it.copy(
                    isMutating = false,
                    feedbackMessage = if (result.isSuccess) {
                        "Published 6 starter products to ShopSwift!"
                    } else {
                        result.exceptionOrNull()?.localizedMessage ?: "Failed to seed catalog"
                    }
                )
            }
        }
    }

    override fun onUpdateOrderStatus(orderId: String, newStatus: OrderStatus) {
        if (!isAuthorized) return
        viewModelScope.launch {
            val result = orderRepository.updateOrderStatusAdmin(orderId, newStatus)
            _uiControl.update {
                it.copy(
                    feedbackMessage = if (result.isSuccess) {
                        "Order updated to ${newStatus.label}"
                    } else {
                        result.exceptionOrNull()?.localizedMessage ?: "Failed to update order status"
                    }
                )
            }
        }
    }

    override fun clearFeedback() {
        _uiControl.update { it.copy(feedbackMessage = null) }
    }

    private data class AdminControlState(
        val selectedTab: AdminTab = AdminTab.INVENTORY,
        val editorDraft: ProductDraft? = null,
        val productPendingDelete: Product? = null,
        val isMutating: Boolean = false,
        val feedbackMessage: String? = null
    )
}
