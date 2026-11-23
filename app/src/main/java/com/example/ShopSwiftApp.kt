package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.remote.AuthManager
import com.example.data.remote.AuthManager.authStateFlow
import com.example.data.repository.CartRepository
import com.example.data.repository.UserRepository
import com.example.features.admin.AdminScreen
import com.example.features.auth.LoginScreen
import com.example.features.cart.CartScreen
import com.example.features.catalog.CatalogScreen
import com.example.features.splash.SplashScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

object ShopSwiftRoutes {
    const val CATALOG = "catalog"
    const val CART = "cart"
    const val ADMIN = "admin"
}

private data class ShopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun ShopSwiftApp() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    if (showSplash) {
        SplashScreen(onSplashComplete = { showSplash = false })
        return
    }

    val user = currentUser
    if (user == null) {
        LoginScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedShopShell(user = user)
        }
    }
}

@Composable
private fun AuthenticatedShopShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val isAdmin = remember(user.email) { AuthManager.isAdminEmail(user.email) }
    val displayName = remember(user.displayName, user.email) {
        user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Shopper"
    }

    val db = remember(context) {
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    }

    LaunchedEffect(user.uid) {
        UserRepository(db).syncCurrentUserProfile(user)
    }

    val cartBadgeCount by remember(user.uid, db) {
        CartRepository(db).observeCart(user.uid)
            .map { items -> items.sumOf { it.quantity }.toInt() }
            .catch { emit(0) }
    }.collectAsStateWithLifecycle(initialValue = 0)

    val destinations = remember(isAdmin) {
        buildList {
            add(
                ShopDestination(
                    route = ShopSwiftRoutes.CATALOG,
                    label = "Catalog",
                    selectedIcon = Icons.Rounded.Storefront,
                    unselectedIcon = Icons.Outlined.Storefront,
                    testTag = "nav_catalog"
                )
            )
            add(
                ShopDestination(
                    route = ShopSwiftRoutes.CART,
                    label = "Cart",
                    selectedIcon = Icons.Rounded.ShoppingCart,
                    unselectedIcon = Icons.Outlined.ShoppingCart,
                    testTag = "nav_cart"
                )
            )
            if (isAdmin) {
                add(
                    ShopDestination(
                        route = ShopSwiftRoutes.ADMIN,
                        label = "Admin",
                        selectedIcon = Icons.Rounded.AdminPanelSettings,
                        unselectedIcon = Icons.Outlined.AdminPanelSettings,
                        testTag = "nav_admin"
                    )
                )
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ShopSwiftRoutes.CATALOG

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val handleSignOut: () -> Unit = {
        AuthManager.signOut(
            credentialManager = CredentialManager.create(context),
            scope = scope
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        destination = dest,
                                        selected = selected,
                                        cartBadgeCount = cartBadgeCount
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        destination = dest,
                                        selected = selected,
                                        cartBadgeCount = cartBadgeCount
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ShopSwiftRoutes.CATALOG
                    ) {
                        composable(ShopSwiftRoutes.CATALOG) {
                            CatalogScreen(
                                currentUserId = user.uid,
                                userDisplayName = displayName,
                                isAdmin = isAdmin,
                                onOpenAdmin = {
                                    if (isAdmin) navigateToTab(ShopSwiftRoutes.ADMIN)
                                },
                                onSignOut = handleSignOut
                            )
                        }
                        composable(ShopSwiftRoutes.CART) {
                            CartScreen(
                                currentUserId = user.uid,
                                userDisplayName = displayName,
                                onContinueShopping = { navigateToTab(ShopSwiftRoutes.CATALOG) }
                            )
                        }
                        composable(ShopSwiftRoutes.ADMIN) {
                            AdminScreen(
                                currentUserId = user.uid,
                                userEmail = user.email
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    destination: ShopDestination,
    selected: Boolean,
    cartBadgeCount: Int
) {
    val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
    if (destination.route == ShopSwiftRoutes.CART && cartBadgeCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (cartBadgeCount > 99) "99+" else cartBadgeCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = destination.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = destination.label)
    }
}
