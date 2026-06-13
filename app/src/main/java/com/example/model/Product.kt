package com.example.model

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    var stock: Int // Var so we can update stock locally if required
)

data class CartItem(
    val product: Product,
    var quantity: Int
) {
    val subtotal: Double
        get() = product.price * quantity
}

data class Transaction(
    val id: String,
    val timestamp: Long,
    val items: List<CartItem>,
    val totalPay: Double
)
