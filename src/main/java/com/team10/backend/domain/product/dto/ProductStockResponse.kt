package com.team10.backend.domain.product.dto

data class ProductStockResponse(
    @JvmField val productId: Long,
    @JvmField val stock: Int,
    @JvmField val message: String
) {
    companion object {
        @JvmStatic
        fun of(productId: Long, stock: Int): ProductStockResponse {
            return ProductStockResponse(productId, stock, "상품 재고가 수정되었습니다.")
        }
    }
}