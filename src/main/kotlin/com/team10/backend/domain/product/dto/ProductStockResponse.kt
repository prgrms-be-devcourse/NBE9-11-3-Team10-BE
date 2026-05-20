package com.team10.backend.domain.product.dto

data class ProductStockResponse(
    val productId: Long,
    val stock: Int,
    val message: String
) {
    companion object {
        fun of(productId: Long, stock: Int): ProductStockResponse {
            return ProductStockResponse(productId, stock, "상품 재고가 수정되었습니다.")
        }
    }
}