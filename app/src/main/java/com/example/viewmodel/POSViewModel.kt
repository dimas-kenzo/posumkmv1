package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProductRepository
import com.example.model.CartItem
import com.example.model.Product
import com.example.model.Transaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class GroupedProductSales(
    val product: Product,
    val totalQuantity: Int,
    val totalRevenue: Double
)

class POSViewModel(val repository: ProductRepository = ProductRepository()) : ViewModel() {

    // Kategori yang tersedia
    val categories = listOf("Semua", "Makanan", "Minuman")

    // State filter kategori
    private val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory = _selectedCategory.asStateFlow()

    // State query pencarian
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // State transaksi harian (dengan dummy data awal harian)
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    init {
        _transactions.value = getDummyTransactions()
    }

    // State ringkasan laporan harian
    val totalRevenueToday: StateFlow<Double> = _transactions.map { list ->
        list.sumOf { it.totalPay }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalTransactionsToday: StateFlow<Int> = _transactions.map { list ->
        list.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalItemsSoldToday: StateFlow<Int> = _transactions.map { list ->
        list.flatMap { it.items }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Fitur grouping logika sales hari ini
    val groupedSalesToday: StateFlow<List<GroupedProductSales>> = _transactions.map { list ->
        list.flatMap { it.items }
            .groupBy { it.product.id }
            .map { (productId, cartItems) ->
                val firstItem = cartItems.first()
                val totalQty = cartItems.sumOf { it.quantity }
                val totalRev = totalQty * firstItem.product.price
                GroupedProductSales(
                    product = firstItem.product,
                    totalQuantity = totalQty,
                    totalRevenue = totalRev
                )
            }
            .sortedByDescending { it.totalQuantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State sinkronisasi cloud (dummy)
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    // State daftar produk (reaktif terhadap repositori dan filter)
    val filteredProducts: StateFlow<List<Product>> = combine(
        repository.productsFlow,
        _selectedCategory,
        _searchQuery
    ) { products, category, query ->
        products.filter { product ->
            val matchCategory = category == "Semua" || product.category.equals(category, ignoreCase = true)
            val matchQuery = query.isEmpty() || product.name.contains(query, ignoreCase = true)
            matchCategory && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State Keranjang Belanja (Cart)
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart = _cart.asStateFlow()

    // Total Belanja
    val cartTotal: StateFlow<Double> = _cart.map { items ->
        items.sumOf { it.subtotal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Uang yang dibayarkan oleh pelanggan
    private val _cashReceived = MutableStateFlow("")
    val cashReceived = _cashReceived.asStateFlow()

    // Dialog Resi Pembayaran
    private val _showReceiptDialog = MutableStateFlow(false)
    val showReceiptDialog = _showReceiptDialog.asStateFlow()

    // Resi aktif
    private val _currentReceipt = MutableStateFlow<Receipt?>(null)
    val currentReceipt = _currentReceipt.asStateFlow()

    // Mobile tabs: 0 for Catalog, 1 for Cart
    private val _activeTab = MutableStateFlow(0)
    val activeTab = _activeTab.asStateFlow()

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setActiveTab(index: Int) {
        _activeTab.value = index
    }

    fun addToCart(product: Product) {
        if (product.stock <= 0) return // Stok habis

        val currentList = _cart.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.product.id == product.id }

        if (existingIndex != -1) {
            val existingItem = currentList[existingIndex]
            val nextQuantity = existingItem.quantity + 1
            if (nextQuantity <= product.stock) {
                // Buat item baru agar Compose mendeteksi perubahan state dengan benar
                currentList[existingIndex] = existingItem.copy(quantity = nextQuantity)
                _cart.value = currentList
            }
        } else {
            currentList.add(CartItem(product, 1))
            _cart.value = currentList
        }
    }

    fun decreaseQuantity(cartItem: CartItem) {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == cartItem.product.id }
        if (index != -1) {
            val item = currentList[index]
            if (item.quantity > 1) {
                currentList[index] = item.copy(quantity = item.quantity - 1)
                _cart.value = currentList
            } else {
                currentList.removeAt(index)
                _cart.value = currentList
            }
        }
    }

    fun increaseQuantity(cartItem: CartItem) {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == cartItem.product.id }
        if (index != -1) {
            val item = currentList[index]
            if (item.quantity < item.product.stock) {
                currentList[index] = item.copy(quantity = item.quantity + 1)
                _cart.value = currentList
            }
        }
    }

    fun removeFromCart(cartItem: CartItem) {
        val currentList = _cart.value.toMutableList()
        currentList.removeAll { it.product.id == cartItem.product.id }
        _cart.value = currentList
    }

    fun setCashReceived(value: String) {
        // Hanya izinkan angka desimal/bulat
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _cashReceived.value = value
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _cashReceived.value = ""
    }

    fun syncWithGoogleSheets() {
        viewModelScope.launch {
            _isSyncing.value = true
            // Jalankan sinkronisasi dummy selama 1.5 detik
            kotlinx.coroutines.delay(1500)
            repository.fetchProductsFromNetwork()
            _isSyncing.value = false
        }
    }

    fun checkout(storeName: String = "Toko Berkah") {
        val items = _cart.value
        if (items.isEmpty()) return

        val total = cartTotal.value
        val cash = _cashReceived.value.toDoubleOrNull() ?: total
        val change = (cash - total).coerceAtLeast(0.0)

        // Membuat format tanggal & waktu Bahasa Indonesia
        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale("id", "ID"))
        val currentDateTime = dateFormat.format(Date())

        val receipt = Receipt(
            storeName = storeName,
            transactionDateTime = currentDateTime,
            items = items.toList(),
            totalAmount = total,
            paymentReceived = cash,
            changeAmount = change
        )

        // Mengurangi stok produk saat dibeli secara sukses
        items.forEach { item ->
            repository.reduceStock(item.product.id, item.quantity)
        }

        // Tambah ke daftar transaksi harian
        val newTransaction = Transaction(
            id = "TRX-${System.currentTimeMillis().toString().takeLast(6)}",
            timestamp = System.currentTimeMillis(),
            items = items.toList(),
            totalPay = total
        )
        _transactions.update { currentList ->
            currentList + newTransaction
        }

        _currentReceipt.value = receipt
        _showReceiptDialog.value = true
    }

    fun dismissReceipt() {
        _showReceiptDialog.value = false
        _currentReceipt.value = null
        clearCart()
        _activeTab.value = 0 // Kembali ke katalog produk di HP
    }

    /**
     * Memperbarui produk di repositori dan menyinkronkan keranjang belanja
     */
    fun updateProduct(product: Product) {
        repository.updateProduct(product)
        
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            val existingItem = currentCart[index]
            val newQuantity = existingItem.quantity.coerceAtMost(product.stock)
            if (newQuantity <= 0) {
                currentCart.removeAt(index)
            } else {
                currentCart[index] = existingItem.copy(product = product, quantity = newQuantity)
            }
            _cart.value = currentCart
        }
    }

    /**
     * Menghapus produk dari repositori dan mengeluarkannya dari keranjang belanja
     */
    fun deleteProduct(productId: String) {
        repository.deleteProduct(productId)
        
        val currentCart = _cart.value.toMutableList()
        currentCart.removeAll { it.product.id == productId }
        _cart.value = currentCart
    }

    private fun getDummyTransactions(): List<Transaction> {
        val products = repository.getProducts()
        val p1 = products.find { it.id == "P001" } ?: Product("P001", "Kopi Susu Gula Aren", 15000.0, "Minuman", 45)
        val p2 = products.find { it.id == "P002" } ?: Product("P002", "Es Teh Manis", 5000.0, "Minuman", 80)
        val p3 = products.find { it.id == "P003" } ?: Product("P003", "Roti Bakar Keju Cokelat", 12000.0, "Makanan", 15)
        val p4 = products.find { it.id == "P004" } ?: Product("P004", "Mie Goreng Spesial + Telur", 15000.0, "Makanan", 25)
        val p7 = products.find { it.id == "P007" } ?: Product("P007", "Indomie Nyemek Pedas", 13000.0, "Makanan", 40)
        val p10 = products.find { it.id == "P010" } ?: Product("P010", "Pisang Goreng Keju", 10000.0, "Makanan", 18)

        val now = Date().time
        val tenMinsAgo = now - 10 * 60 * 1000
        val thirtyMinsAgo = now - 30 * 60 * 1000
        val twoHoursAgo = now - 2 * 60 * 60 * 1000
        val fourHoursAgo = now - 4 * 60 * 60 * 1000

        return listOf(
            Transaction(
                id = "TRX-00101",
                timestamp = fourHoursAgo,
                items = listOf(
                    CartItem(p1, 2),
                    CartItem(p10, 1)
                ),
                totalPay = 40000.0
            ),
            Transaction(
                id = "TRX-00202",
                timestamp = twoHoursAgo,
                items = listOf(
                    CartItem(p7, 1),
                    CartItem(p2, 2)
                ),
                totalPay = 23000.0
            ),
            Transaction(
                id = "TRX-00303",
                timestamp = thirtyMinsAgo,
                items = listOf(
                    CartItem(p3, 1),
                    CartItem(p1, 1),
                    CartItem(p2, 1)
                ),
                totalPay = 32000.0
            ),
            Transaction(
                id = "TRX-00404",
                timestamp = tenMinsAgo,
                items = listOf(
                    CartItem(p4, 1),
                    CartItem(p1, 2)
                ),
                totalPay = 45000.0
            )
        )
    }
}

data class Receipt(
    val storeName: String,
    val transactionDateTime: String,
    val items: List<CartItem>,
    val totalAmount: Double,
    val paymentReceived: Double,
    val changeAmount: Double
) {
    fun formatRupiah(value: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        format.maximumFractionDigits = 0
        return format.format(value).replace("Rp", "Rp ")
    }
}
