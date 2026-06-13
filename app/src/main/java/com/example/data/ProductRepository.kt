package com.example.data

import com.example.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProductRepository {

    // Dummy data lokal untuk UMKM (Warung Kopi / Kedai POS)
    private val initialProducts = listOf(
        Product("P001", "Kopi Susu Gula Aren", 15000.0, "Minuman", 45),
        Product("P002", "Es Teh Manis", 5000.0, "Minuman", 80),
        Product("P003", "Roti Bakar Keju Cokelat", 12000.0, "Makanan", 15),
        Product("P004", "Mie Goreng Spesial + Telur", 15000.0, "Makanan", 25),
        Product("P005", "Keripik Singkong Pedas", 8000.0, "Makanan", 30),
        Product("P006", "Aqua Botol 600ml", 4000.0, "Minuman", 120),
        Product("P007", "Indomie Nyemek Pedas", 13000.0, "Makanan", 40),
        Product("P008", "Kentang Goreng Crispy", 10000.0, "Makanan", 20),
        Product("P009", "Es Jeruk Peras", 7000.0, "Minuman", 35),
        Product("P010", "Pisang Goreng Keju", 10000.0, "Makanan", 18)
    )

    private val _products = MutableStateFlow<List<Product>>(initialProducts)
    val productsFlow: Flow<List<Product>> = _products.asStateFlow()

    fun getProducts(): List<Product> {
        return _products.value
    }

    /**
     * Fungsi kosong/tiruan sebagai tempat untuk memanggil API Google Sheets nantinya.
     * Dapat digunakan untuk menyinkronkan data produk dari Google Sheets secara asinkron.
     */
    suspend fun fetchProductsFromNetwork() {
        // TODO: Implementasikan integrasi Google Sheets API di sini.
        // Contoh:
        // val response = googleSheetsService.getSpreadsheetValues(spreadsheetId, range)
        // val parsedProducts = parseGoogleSheetsData(response)
        // _products.value = parsedProducts
    }

    /**
     * Memperbarui stok produk setelah pembelian sukses
     */
    fun reduceStock(productId: String, quantitySold: Int) {
        _products.update { currentList ->
            currentList.map { product ->
                if (product.id == productId) {
                    val newStock = (product.stock - quantitySold).coerceAtLeast(0)
                    product.copy(stock = newStock)
                } else {
                    product
                }
            }
        }
    }

    /**
     * Menambahkan produk baru secara lokal
     */
    fun addProduct(product: Product) {
        _products.update { currentList ->
            currentList + product
        }
    }

    /**
     * Memperbarui data produk yang ada secara lokal
     */
    fun updateProduct(updatedProduct: Product) {
        _products.update { currentList ->
            currentList.map { product ->
                if (product.id == updatedProduct.id) {
                    updatedProduct
                } else {
                    product
                }
            }
        }
    }

    /**
     * Menghapus produk secara lokal
     */
    fun deleteProduct(productId: String) {
        _products.update { currentList ->
            currentList.filter { product -> product.id != productId }
        }
    }
}
