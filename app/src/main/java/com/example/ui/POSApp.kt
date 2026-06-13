package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.CartItem
import com.example.model.Product
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.TealAccent
import com.example.viewmodel.POSViewModel
import com.example.viewmodel.Receipt
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.bluetooth.BluetoothPrinterManager
import com.example.bluetooth.PrintState
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

// Helper Currency Formatter
fun formatRupiah(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    format.maximumFractionDigits = 0
    return format.format(value).replace("Rp", "Rp ")
}

@Composable
fun POSApp(
    viewModel: POSViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Observe State Flows
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val products by viewModel.filteredProducts.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val cartTotal by viewModel.cartTotal.collectAsState()
    val cashReceived by viewModel.cashReceived.collectAsState()
    val showReceiptDialog by viewModel.showReceiptDialog.collectAsState()
    val currentReceipt by viewModel.currentReceipt.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()

    // Dialog Tambah Produk Lokal (Opsional untuk kemudahan demo)
    var showAddProductDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }

    // Dialog untuk Sukses Checkout
    if (showReceiptDialog && currentReceipt != null) {
        ReceiptDialog(
            receipt = currentReceipt!!,
            onDismiss = { viewModel.dismissReceipt() }
        )
    }

    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onProductAdded = { newProduct ->
                viewModel.repository.addProduct(newProduct)
                Toast.makeText(context, "Produk '${newProduct.name}' berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                showAddProductDialog = false
            }
        )
    }

    if (productToEdit != null) {
        EditProductDialog(
            product = productToEdit!!,
            onDismiss = { productToEdit = null },
            onProductUpdated = { updatedProduct ->
                viewModel.updateProduct(updatedProduct)
                Toast.makeText(context, "Produk '${updatedProduct.name}' berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                productToEdit = null
            },
            onProductDeleted = { productId ->
                viewModel.deleteProduct(productId)
                Toast.makeText(context, "Produk berhasil dihapus!", Toast.LENGTH_SHORT).show()
                productToEdit = null
            }
        )
    }

    // Gunakan BoxWithConstraints untuk menentukan layout yang responsif
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = maxWidth > 760.dp

        Scaffold(
            topBar = {
                if (activeTab != 2) {
                    POSTopBar(
                        isSyncing = isSyncing,
                        onSyncClick = {
                            viewModel.syncWithGoogleSheets()
                            Toast.makeText(context, "Menghubungkan & Menyinkronkan ke Google Sheets...", Toast.LENGTH_SHORT).show()
                        },
                        onAddProductClick = { showAddProductDialog = true },
                        onReportClick = { viewModel.setActiveTab(2) }
                    )
                }
            },
            bottomBar = {
                // Tampilkan bottom navigation atau tab bar HANYA di Handphone
                if (!isTablet) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = activeTab == 0,
                            onClick = { viewModel.setActiveTab(0) },
                            icon = { Icon(Icons.Default.List, contentDescription = "Katalog") },
                            label = { Text("Katalog", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("nav_tab_catalog")
                        )
                        NavigationBarItem(
                            selected = activeTab == 1,
                            onClick = { viewModel.setActiveTab(1) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (cart.isNotEmpty()) {
                                            Badge {
                                                Text(
                                                    text = cart.sumOf { it.quantity }.toString(),
                                                    modifier = Modifier.testTag("cart_badge_count")
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = "Keranjang")
                                }
                            },
                            label = { Text("Keranjang", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("nav_tab_cart")
                        )
                        NavigationBarItem(
                            selected = activeTab == 2,
                            onClick = { viewModel.setActiveTab(2) },
                            icon = { Icon(Icons.Default.Assessment, contentDescription = "Laporan") },
                            label = { Text("Laporan", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("nav_tab_report")
                        )
                    }
                }
            },
            floatingActionButton = {
                // Floating Action Button di Handphone untuk menuju ke Keranjang jika isi
                if (!isTablet && activeTab == 0 && cart.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.setActiveTab(1) },
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Keranjang") },
                        text = { Text("Keranjang (${cart.sumOf { it.quantity }})") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .testTag("floating_cart_fab")
                            .padding(bottom = 8.dp)
                    )
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)

            if (activeTab == 2) {
                DailySalesReportScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.setActiveTab(0) },
                    modifier = contentModifier.fillMaxSize()
                )
            } else if (isTablet) {
                // Layout Tablet: Katalog (Kiri) + Keranjang (Kanan) side-by-side
                Row(
                    modifier = contentModifier.fillMaxSize()
                ) {
                    // Katalog Panel (Bagian Kiri)
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxSize()
                            .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp)
                    ) {
                        CatalogHeader(
                            searchQuery = searchQuery,
                            onSearchChange = viewModel::updateSearchQuery,
                            categories = viewModel.categories,
                            selectedCategory = selectedCategory,
                            onCategorySelect = viewModel::selectCategory
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ProductGrid(
                            products = products,
                            onProductClick = { product ->
                                viewModel.addToCart(product)
                            },
                            onEditClick = { product ->
                                productToEdit = product
                            }
                        )
                    }

                    // Keranjang Panel (Bagian Ritel Kanan)
                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
                    ) {
                        CartPanel(
                            cart = cart,
                            cartTotal = cartTotal,
                            cashReceived = cashReceived,
                            onCashChange = viewModel::setCashReceived,
                            onIncreaseQty = { viewModel.increaseQuantity(it) },
                            onDecreaseQty = { viewModel.decreaseQuantity(it) },
                            onRemoveItem = { viewModel.removeFromCart(it) },
                            onCheckoutClick = {
                                viewModel.checkout()
                            },
                            onClearCart = { viewModel.clearCart() }
                        )
                    }
                }
            } else {
                // Layout Handphone: Berdasarkan Active Tab (0 = Katalog, 1 = Keranjang, 2 = Laporan)
                Crossfade(
                    targetState = activeTab,
                    modifier = contentModifier.fillMaxSize(),
                    label = "MobileNavigation"
                ) { tab ->
                    when (tab) {
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                             ) {
                                CatalogHeader(
                                    searchQuery = searchQuery,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    categories = viewModel.categories,
                                    selectedCategory = selectedCategory,
                                    onCategorySelect = viewModel::selectCategory
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                ProductGrid(
                                    products = products,
                                    onProductClick = { product ->
                                        viewModel.addToCart(product)
                                        Toast.makeText(context, "${product.name} masuk keranjang", Toast.LENGTH_SHORT).show()
                                    },
                                    onEditClick = { product ->
                                        productToEdit = product
                                    }
                                )
                            }
                        }
                        1 -> {
                            CartPanel(
                                cart = cart,
                                cartTotal = cartTotal,
                                cashReceived = cashReceived,
                                onCashChange = viewModel::setCashReceived,
                                onIncreaseQty = { viewModel.increaseQuantity(it) },
                                onDecreaseQty = { viewModel.decreaseQuantity(it) },
                                onRemoveItem = { viewModel.removeFromCart(it) },
                                onCheckoutClick = { viewModel.checkout() },
                                onClearCart = { viewModel.clearCart() }
                            )
                        }
                        2 -> {
                            DailySalesReportScreen(
                                viewModel = viewModel,
                                onBackClick = { viewModel.setActiveTab(0) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

// TOP BAR COMPOSABLE
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSTopBar(
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onAddProductClick: () -> Unit,
    onReportClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "POS Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Kasir POS UMKM",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Manajemen Penjualan Ritel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        actions = {
            // Button Laporan Penjualan Harian
            IconButton(
                onClick = onReportClick,
                modifier = Modifier.testTag("action_view_report")
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = "Laporan Penjualan Harian",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Button Tambah Produk Baru
            IconButton(
                onClick = onAddProductClick,
                modifier = Modifier.testTag("action_add_product")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah Produk Manual",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Sync Indicator / Button Google Sheets Connection
            Box(
                modifier = Modifier.padding(end = 8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = onSyncClick,
                        modifier = Modifier.testTag("action_sync_sheets")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sinkronisasi Sheets",
                            tint = TealAccent
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ),
        modifier = Modifier.border(0.dp, Color.Transparent)
    )
}

// CATALOG CONFIG HEADER COMPOSABLE
@Composable
fun CatalogHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cari Produk...", color = MaterialTheme.colorScheme.outline) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ikon Cari") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Bersihkan")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("product_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Selection Row
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelect(category) },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .testTag("category_chip_$category")
                                .height(36.dp)
                        )
                    }
                }
            }
        }
    }
}

// PRODUCT GRID COMPOSABLE
@Composable
fun ProductGrid(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    onEditClick: (Product) -> Unit
) {
    if (products.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Tidak ada produk",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Produk Tidak Ditemukan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Coba ubah kata kunci pencarian Anda atau sinkronkan kembali database.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("product_grid"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(products, key = { it.id }) { product ->
                ProductCard(
                    product = product,
                    onProductClick = onProductClick,
                    onEditClick = onEditClick
                )
            }
        }
    }
}

// INDIVIDUAL PRODUCT CARD
@Composable
fun ProductCard(
    product: Product,
    onProductClick: (Product) -> Unit,
    onEditClick: (Product) -> Unit
) {
    val isOutOfStock = product.stock <= 0
    val isLowStock = product.stock in 1..10

    // Abstract dynamic visual circle for applets based on product category
    val categoryShortcut = when {
        product.category.contains("makanan", ignoreCase = true) -> "MK"
        product.category.contains("minuman", ignoreCase = true) -> "MN"
        product.category.contains("cemilan", ignoreCase = true) -> "CM"
        else -> "PK"
    }

    val shortcutBg = when (categoryShortcut) {
        "MK" -> Color(0xFFFEE2E2) // Redish
        "MN" -> Color(0xFFDBEAFE) // Blueish
        "CM" -> Color(0xFFFEF3C7) // Orange/Cream
        else -> Color(0xFFE2E8F0)
    }

    val shortcutText = when (categoryShortcut) {
        "MK" -> Color(0xFFDC2626)
        "MN" -> Color(0xFF2563EB)
        "CM" -> Color(0xFFD97706)
        else -> Color(0xFF475569)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}")
            .clickable(enabled = !isOutOfStock) { onProductClick(product) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOutOfStock) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOutOfStock) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Kategori Tag di Atas
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Abstract Round Avatar Image
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(shortcutBg)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = categoryShortcut,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = shortcutText
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOutOfStock) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Price
                Text(
                    text = formatRupiah(product.price),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = if (isOutOfStock) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stock Indicator Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val stockColor = when {
                        isOutOfStock -> Color.Red
                        isLowStock -> Color(0xFFD97706) // Orange
                        else -> MintSuccess
                    }

                    val stockLabel = when {
                        isOutOfStock -> "Habis"
                        isLowStock -> "Terbatas: ${product.stock}"
                        else -> "Stok: ${product.stock}"
                    }

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(stockColor)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = stockLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = stockColor,
                        maxLines = 1
                    )
                }
            }

            // Edit button overlay badge (top right corner of the card)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onEditClick(product) }
                    .size(36.dp)
                    .testTag("edit_product_btn_${product.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Produk",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// CART CONSOLIDATED PANEL
@Composable
fun CartPanel(
    cart: List<CartItem>,
    cartTotal: Double,
    cashReceived: String,
    onCashChange: (String) -> Unit,
    onIncreaseQty: (CartItem) -> Unit,
    onDecreaseQty: (CartItem) -> Unit,
    onRemoveItem: (CartItem) -> Unit,
    onCheckoutClick: () -> Unit,
    onClearCart: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cart_panel")
    ) {
        // Title & Clear Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Cart Icon",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Keranjang Belanja",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (cart.isNotEmpty()) {
                IconButton(
                    onClick = onClearCart,
                    modifier = Modifier.testTag("cart_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Kosongkan Keranjang",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // Cart items or empty state
        if (cart.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Cart Empty",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Keranjang Masih Kosong",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tekan produk di katalog sebelah kiri untuk menambahkannya ke sini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Cart List Scrollable
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("cart_items_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(cart, key = { it.product.id }) { item ->
                    CartItemRow(
                        item = item,
                        onIncreaseQty = { onIncreaseQty(item) },
                        onDecreaseQty = { onDecreaseQty(item) },
                        onRemoveItem = { onRemoveItem(item) }
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Checkout Calculation Drawer Bottom Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                // Calculation Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "${cart.sumOf { it.quantity }} Pcs",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtotal Belanja",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatRupiah(cartTotal),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cash Received input (for change calculation)
                OutlinedTextField(
                    value = cashReceived,
                    onValueChange = onCashChange,
                    label = { Text("Nominal Tunai Pelanggan (Rp)") },
                    placeholder = { Text("Contoh: 50000") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cash_input_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Auto-calculation of Change
                val numericCash = cashReceived.toDoubleOrNull() ?: 0.0
                if (numericCash > 0.0) {
                    val changeAmount = numericCash - cartTotal
                    val changeColor = if (changeAmount >= 0) MintSuccess else Color.Red
                    val changeLabel = if (changeAmount >= 0) "Kembalian:" else "Kurang:"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(changeColor.copy(alpha = 0.08f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = changeLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = changeColor
                        )
                        Text(
                            text = formatRupiah(kotlin.math.abs(changeAmount)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = changeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val canPay = numericCash >= cartTotal || cashReceived.isEmpty()

                // Pay Button
                Button(
                    onClick = onCheckoutClick,
                    enabled = canPay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintSuccess,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("checkout_pay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Bayar",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (cashReceived.isEmpty()) "Bayar (Uang Pas)" else "Proses Pembayaran",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// INDIVIDUAL CART ITEM ROW
@Composable
fun CartItemRow(
    item: CartItem,
    onIncreaseQty: () -> Unit,
    onDecreaseQty: () -> Unit,
    onRemoveItem: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cart_item_${item.product.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1.1f)
            ) {
                Text(
                    text = item.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatRupiah(item.product.price),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = " × ${item.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Subtotal: ${formatRupiah(item.subtotal)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Qty adjust container
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Delete / Minus Button
                IconButton(
                    onClick = onDecreaseQty,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("qty_minus_${item.product.id}")
                ) {
                    Icon(
                        imageVector = if (item.quantity == 1) Icons.Default.Delete else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Kurangi Qty",
                        tint = if (item.quantity == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Quantity Text Display
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.quantity.toString(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                // Plus Button
                val isLimitReached = item.quantity >= item.product.stock
                IconButton(
                    onClick = onIncreaseQty,
                    enabled = !isLimitReached,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("qty_plus_${item.product.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Qty",
                        tint = if (isLimitReached) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// BILL/RECEIPT DIALOG (CETAK STRUK PREVIEW)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDialog(
    receipt: Receipt,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val printerManager = remember { BluetoothPrinterManager(context) }
    var pairedDevices by remember { mutableStateOf<List<android.bluetooth.BluetoothDevice>>(emptyList()) }
    var selectedPrinterAddress by remember { mutableStateOf(printerManager.getSelectedPrinterMac() ?: "") }
    val printState by printerManager.printState.collectAsState()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        if (connectGranted) {
            pairedDevices = printerManager.getPairedPrinters()
        }
    }

    LaunchedEffect(Unit) {
        if (printerManager.hasBluetoothPermission()) {
            pairedDevices = printerManager.getPairedPrinters()
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("receipt_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Ring Header
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MintSuccess.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success Icon",
                        tint = MintSuccess,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Transaksi Berhasil",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MintSuccess
                )

                Text(
                    text = "Silakan periksa pratinjau struk penjualan di bawah.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // STRUK BELANJA CANVAS (PAPER PREVIEW)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9)) // Thermal Paper light gray background
                        .border(BorderStroke(1.dp, Color(0xFFCBD5E1)), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Title / Nama Toko
                        Text(
                            text = receipt.storeName.uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Black
                        )
                        Text(
                            text = "Ruko Grand Gateway No. 12",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.DarkGray
                        )
                        Text(
                            text = "Telp: 0812-3456-7890",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Thermal Separation dotted line
                        Text(
                            text = "-".repeat(45),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )

                        // Tanggal & Waktu Kasir
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TANGGAL :",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                            Text(
                                text = receipt.transactionDateTime,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Black
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "KASIR   :",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "UMKM ADMIN",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Black
                            )
                        }

                        Text(
                            text = "-".repeat(45),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )

                        // DAFTAR BARANG (Nama, Qty, Subtotal)
                        receipt.items.forEach { cartItem ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = cartItem.product.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${cartItem.quantity} x ${receipt.formatRupiah(cartItem.product.price)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = receipt.formatRupiah(cartItem.subtotal),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Text(
                            text = "-".repeat(45),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )

                        // TOTAL BELANJA / JUMLAH
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TOTAL           :",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = receipt.formatRupiah(receipt.totalAmount),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TUNAI (BAYAR)   :",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                            Text(
                                text = receipt.formatRupiah(receipt.paymentReceived),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Black
                            )
                        }

                        // KEMBALIAN
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "KEMBALIAN       :",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = receipt.formatRupiah(receipt.changeAmount),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        Text(
                            text = "-".repeat(45),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "TERIMA KASIH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Black
                        )
                        Text(
                            text = "SUDAH BERBELANJA DI TOKO KAMI",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // SEKTOR PEMILIH PRINTER BLUETOOTH
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "PILIH PRINTER BLUETOOTH (Thermal 58mm)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (pairedDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable {
                                    if (printerManager.hasBluetoothPermission()) {
                                        pairedDevices = printerManager.getPairedPrinters()
                                        if (pairedDevices.isEmpty()) {
                                            Toast.makeText(context, "Tidak ada printer dipasangkan. Pastikan Bluetooth aktif & MP-58A1 sudah terhubung di HP.", Toast.LENGTH_LONG).show()
                                        }
                                    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                                    } else {
                                        Toast.makeText(context, "Bluetooth tidak diizinkan. Silakan aktifkan manual.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "No printers available",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Belum ada printer dipasangkan. Ketuk untuk memindai ulang perangkat Bluetooth...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val currentPrinter = pairedDevices.find { it.address == selectedPrinterAddress }
                        val displayName = currentPrinter?.name ?: if (selectedPrinterAddress.isNotEmpty()) "Printer ($selectedPrinterAddress)" else "Ketuk untuk memilih Printer..."

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Printer",
                                        tint = TealAccent
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                pairedDevices.forEach { device ->
                                    val isMP58 = device.name?.contains("MP-58", ignoreCase = true) == true || device.name?.contains("58A1", ignoreCase = true) == true
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = device.name ?: "Perangkat Tanpa Nama",
                                                        fontWeight = if (isMP58) FontWeight.Bold else FontWeight.Normal,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = device.address,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                if (isMP58) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MintSuccess.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Rekomendasi MP-58A1",
                                                            fontSize = 9.sp,
                                                            color = MintSuccess,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedPrinterAddress = device.address
                                            printerManager.saveSelectedPrinter(device.address)
                                            dropdownExpanded = false
                                            Toast.makeText(context, "Printer terpilih: ${device.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ALERT STATUS PROSES PRINTING
                when (printState) {
                    is PrintState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NavySecondary.copy(alpha = 0.08f))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = NavySecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Menghubungkan & mencetak...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NavySecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    is PrintState.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSuccess.copy(alpha = 0.08f))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sukses",
                                    tint = MintSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Struk terkirim ke printer MP-58A1!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MintSuccess,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    is PrintState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Red.copy(alpha = 0.08f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = (printState as PrintState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Red,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    else -> {}
                }

                // ACTIONS BUTTONS SECTION
                Button(
                    onClick = {
                        if (selectedPrinterAddress.isEmpty()) {
                            Toast.makeText(context, "Pilih/hubungkan printer Bluetooth MP-58A1 terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                printerManager.printReceipt(receipt)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPrinterAddress.isEmpty()) Color.Gray else NavyPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("action_print_bluetooth")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share, // Wireless / sharing representation
                        contentDescription = "Bluetooth Print Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cetak Struk via Bluetooth",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("action_close_receipt")
                ) {
                    Text(
                        text = "Selesai & Sesi Baru",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// DIALOG UNTUK MENAMBAH PRODUK BARU SECARA LOKAL
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onProductAdded: (Product) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Makanan") }
    var stockStr by remember { mutableStateOf("") }

    val categories = listOf("Makanan", "Minuman")
    var categoryExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Tambah Produk Baru",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input Nama
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Produk") },
                    placeholder = { Text("Contoh: Kopi Espresso") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_product_input_name")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input Harga
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) priceStr = it },
                    label = { Text("Harga Produk (Rp)") },
                    placeholder = { Text("Contoh: 15000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_product_input_price")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Dropdown Kategori
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    category = item
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Input Stok
                OutlinedTextField(
                    value = stockStr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) stockStr = it },
                    label = { Text("Jumlah Stok Awal") },
                    placeholder = { Text("Contoh: 50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_product_input_stock")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    val isFormValid = name.isNotBlank() && priceStr.isNotBlank() && stockStr.isNotBlank()

                    Button(
                        onClick = {
                            val price = priceStr.toDoubleOrNull() ?: 0.0
                            val stock = stockStr.toIntOrNull() ?: 0
                            val newProduct = Product(
                                id = "P" + (100 + (1..900).random()), // Generate random ID like P123
                                name = name,
                                price = price,
                                category = category,
                                stock = stock
                            )
                            onProductAdded(newProduct)
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Simpan Produk")
                    }
                }
            }
        }
    }
}

// DIALOG UNTUK MENGEDIT ATAU MENGHAPUS PRODUK LOKAL YANG ADA
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onProductUpdated: (Product) -> Unit,
    onProductDeleted: (String) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var priceStr by remember { mutableStateOf(product.price.toInt().toString()) }
    var category by remember { mutableStateOf(product.category) }
    var stockStr by remember { mutableStateOf(product.stock.toString()) }

    val categories = listOf("Makanan", "Minuman")
    var categoryExpanded by remember { mutableStateOf(false) }

    // State untuk konfirmasi hapus
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (showDeleteConfirm) "Konfirmasi Hapus" else "Edit Produk",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (showDeleteConfirm) {
                    Text(
                        text = "Apakah Anda yakin ingin menghapus produk '${product.name}'? Tindakan ini tidak dapat dibatalkan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = { onProductDeleted(product.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ya, Hapus")
                        }
                    }
                } else {
                    // Input Nama
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Produk") },
                        placeholder = { Text("Contoh: Kopi Espresso") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_product_input_name")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Input Harga
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { if (it.all { char -> char.isDigit() }) priceStr = it },
                        label = { Text("Harga Produk (Rp)") },
                        placeholder = { Text("Contoh: 15000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_product_input_price")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dropdown Kategori
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categories.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        category = item
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Input Stok
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { if (it.all { char -> char.isDigit() }) stockStr = it },
                        label = { Text("Jumlah Stok") },
                        placeholder = { Text("Contoh: 50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_product_input_stock")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Row with Delete, Cancel, Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                                .testTag("btn_delete_product")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Produk",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Batal")
                        }

                        val isFormValid = name.isNotBlank() && priceStr.isNotBlank() && stockStr.isNotBlank()

                        Button(
                            onClick = {
                                val price = priceStr.toDoubleOrNull() ?: product.price
                                val stock = stockStr.toIntOrNull() ?: product.stock
                                val updatedProduct = product.copy(
                                    name = name,
                                    price = price,
                                    category = category,
                                    stock = stock
                                )
                                onProductUpdated(updatedProduct)
                            },
                            enabled = isFormValid,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(2f)
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}
