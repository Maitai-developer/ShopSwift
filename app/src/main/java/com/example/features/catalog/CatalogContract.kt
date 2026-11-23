package com.example.features.catalog

import com.example.data.model.Order
import com.example.data.model.Product
import com.example.ui.common.UiState
import kotlinx.coroutines.flow.StateFlow

enum class CatalogSortOption(val label: String) {
    FEATURED("Featured"),
    PRICE_ASC("Price: Low to High"),
    PRICE_DESC("Price: High to Low")
}

data class CatalogUiState(
    val productsState: UiState<List<Product>> = UiState.Loading,
    val userOrders: List<Order> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val sortOption: CatalogSortOption = CatalogSortOption.FEATURED,
    val selectedProduct: Product? = null,
    val isOrdersSheetVisible: Boolean = false,
    val feedbackMessage: String? = null
) {
    val categories: List<String> =
        listOf("All", "Audio", "Wearables", "Bags", "Accessories", "Home Tech")

    val filteredProducts: List<Product>
        get() {
            val all = (productsState as? UiState.Success)?.data ?: return emptyList()
            val query = searchQuery.trim().lowercase()
            val filtered = all.filter { product ->
                val matchesCategory =
                    selectedCategory == "All" ||
                        product.category.equals(selectedCategory, ignoreCase = true)
                val matchesQuery = query.isEmpty() ||
                    product.title.lowercase().contains(query) ||
                    product.description.lowercase().contains(query) ||
                    product.category.lowercase().contains(query)
                matchesCategory && matchesQuery
            }
            return when (sortOption) {
                CatalogSortOption.FEATURED -> filtered
                CatalogSortOption.PRICE_ASC -> filtered.sortedBy { it.priceCents }
                CatalogSortOption.PRICE_DESC -> filtered.sortedByDescending { it.priceCents }
            }
        }
}

interface CatalogPresenterContract {
    val uiState: StateFlow<CatalogUiState>
    fun onSearchQueryChanged(query: String)
    fun onCategorySelected(category: String)
    fun onSortChanged(sortOption: CatalogSortOption)
    fun onProductClicked(product: Product?)
    fun onAddToCart(product: Product, quantity: Long = 1L)
    fun onToggleOrdersSheet(visible: Boolean)
    fun clearFeedback()
}
