package com.team10.backend.domain.product.dto

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus

data class ProductInactiveResponse(
    @JvmField val productId: Long,
    @JvmField val status: ProductStatus,
    @JvmField val message: String
) {
    companion object {
        @JvmStatic
        fun from(product: Product): ProductInactiveResponse {
            val productId = product.id
                ?: throw IllegalStateException("상품 ID는 null일 수 없습니다.")

            return ProductInactiveResponse(
                productId,
                product.status,
                "상품이 삭제되었습니다."
            )
        }
    }
}