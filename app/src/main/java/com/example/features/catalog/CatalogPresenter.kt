package com.example.features.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.repository.CartRepository
import com.example.data.repository.OrderRepository
import com.example.data.repository.ProductRepository
import com.example.ui.common.UiState
import com.example.ui.common.asUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CatalogPresenter(
    private val productRepository: ProductRepository,
    private val cartRepository: CartRepository,
    orderRepository: OrderRepository,
    private val currentUserId: String
) : ViewModel(), CatalogPresenterContract {

    private val _filterState = MutableStateFlow(FilterState())

    private val productsFlow: StateFlow<UiState<List<Product>>> =
        productRepository.observeActiveProducts()
            .asUiState(tag = "CatalogPresenter", fallbackMessage = "Failed to load catalog")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val ordersFlow: StateFlow<List<Order>> =
        orderRepository.observeUserOrders(currentUserId)
            .asUiState(tag = "CatalogOrders", fallbackMessage = "Failed to load orders")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )
            .let { stateFlow ->
                MutableStateFlow<List<Order>>(emptyList()).also { target ->
                    viewModelScope.launch {
                        stateFlow.collect { state ->
                            if (state is UiState.Success) {
                                target.value = state.data
                            }
                        }
                    }
                }
            }

    override val uiState: StateFlow<CatalogUiState> = combine(
        productsFlow,
        ordersFlow,
        _filterState
    ) { productsState, orders, filters ->
        CatalogUiState(
            productsState = productsState,
            userOrders = orders,
            searchQuery = filters.searchQuery,
            selectedCategory = filters.selectedCategory,
            sortOption = filters.sortOption,
            selectedProduct = filters.selectedProduct,
            isOrdersSheetVisible = filters.isOrdersSheetVisible,
            feedbackMessage = filters.feedbackMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CatalogUiState()
    )

    override fun onSearchQueryChanged(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    override fun onCategorySelected(category: String) {
        _filterState.update { it.copy(selectedCategory = category) }
    }

    override fun onSortChanged(sortOption: CatalogSortOption) {
        _filterState.update { it.copy(sortOption = sortOption) }
    }

    override fun onProductClicked(product: Product?) {
        _filterState.update { it.copy(selectedProduct = product) }
    }

    override fun onAddToCart(product: Product, quantity: Long) {
        if (product.stock <= 0L) {
            _filterState.update { it.copy(feedbackMessage = "${product.title} is currently out of stock.") }
            return
        }
        viewModelScope.launch {
            val result = cartRepository.addToCart(currentUserId, product, quantity)
            _filterState.update {
                if (result.isSuccess) {
                    it.copy(
                        selectedProduct = null,
                        feedbackMessage = "Added ${quantity}x ${product.title} to cart"
                    )
                } else {
                    it.copy(
                        feedbackMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to add item to cart"
                    )
                }
            }
        }
    }

    override fun onToggleOrdersSheet(visible: Boolean) {
        _filterState.update { it.copy(isOrdersSheetVisible = visible) }
    }

    override fun clearFeedback() {
        _filterState.update { it.copy(feedbackMessage = null) }
    }

    private data class FilterState(
        val searchQuery: String = "",
        val selectedCategory: String = "All",
        val sortOption: CatalogSortOption = CatalogSortOption.FEATURED,
        val selectedProduct: Product? = null,
        val isOrdersSheetVisible: Boolean = false,
        val feedbackMessage: String? = null
    )
}
