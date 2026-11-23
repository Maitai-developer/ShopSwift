package com.example.features.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.repository.CartRepository
import com.example.data.repository.OrderRepository
import com.example.data.repository.ProductRepository
import com.example.ui.common.UiState
import com.example.ui.theme.SapphireBlue
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun CatalogScreen(
    currentUserId: String,
    userDisplayName: String,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val presenter: CatalogPresenter = viewModel(
        key = "catalog_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(
                    context.getString(R.string.firestore_database_id)
                )
                CatalogPresenter(
                    productRepository = ProductRepository(db),
                    cartRepository = CartRepository(db),
                    orderRepository = OrderRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val state by presenter.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage) {
        val message = state.feedbackMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            presenter.clearFeedback()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columnCount = when {
                maxWidth >= 960.dp -> 4
                maxWidth >= 600.dp -> 3
                else -> 2
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("catalog_grid")
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogHeaderRow(
                        userDisplayName = userDisplayName,
                        ordersCount = state.userOrders.size,
                        onOpenOrders = { presenter.onToggleOrdersSheet(true) },
                        onSignOut = onSignOut
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogHeroBanner(
                        isAdmin = isAdmin,
                        onOpenAdmin = onOpenAdmin
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = presenter::onSearchQueryChanged,
                                placeholder = { Text("Search electronics, watches, bags...") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { presenter.onSearchQueryChanged("") }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = SapphireBlue,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("catalog_search_input")
                            )

                            Box {
                                Surface(
                                    onClick = { sortMenuExpanded = true },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .testTag("catalog_sort_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Sort,
                                            contentDescription = "Sort products",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    CatalogSortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (state.sortOption == option) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                presenter.onSortChanged(option)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.categories) { category ->
                                val selected = state.selectedCategory == category
                                FilterChip(
                                    selected = selected,
                                    onClick = { presenter.onCategorySelected(category) },
                                    label = { Text(category) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                when (val productsState = state.productsState) {
                    is UiState.Loading -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = productsState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(18.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        val items = state.filteredProducts
                        if (items.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyCatalogCard(
                                    hasFilters = state.searchQuery.isNotBlank() || state.selectedCategory != "All",
                                    isAdmin = isAdmin,
                                    onResetFilters = {
                                        presenter.onSearchQueryChanged("")
                                        presenter.onCategorySelected("All")
                                    },
                                    onOpenAdmin = onOpenAdmin
                                )
                            }
                        } else {
                            items(items, key = { it.id }) { product ->
                                ProductCard(
                                    product = product,
                                    onClick = { presenter.onProductClicked(product) },
                                    onQuickAdd = { presenter.onAddToCart(product, 1L) }
                                )
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

    state.selectedProduct?.let { product ->
        ProductDetailSheet(
            product = product,
            onDismiss = { presenter.onProductClicked(null) },
            onAddToCart = { qty -> presenter.onAddToCart(product, qty) }
        )
    }

    if (state.isOrdersSheetVisible) {
        UserOrdersSheet(
            orders = state.userOrders,
            onDismiss = { presenter.onToggleOrdersSheet(false) }
        )
    }
}
