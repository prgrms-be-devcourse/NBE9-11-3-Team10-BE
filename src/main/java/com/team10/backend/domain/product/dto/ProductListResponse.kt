package com.team10.backend.domain.product.dto

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import java.time.LocalDateTime

data class ProductListResponse(
    val productId: Long,
    @JvmField val productName: String,
    val price: Int,
    @JvmField val nickname: String,
    val imageUrl: String?,
    @JvmField val type: ProductType,
    @JvmField val status: ProductStatus,
    @JvmField val sellerId: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        @JvmStatic
        fun from(product: Product): ProductListResponse {
            return ProductListResponse(
                product.id,
                product.productName,
                product.price,
                product.user.nickname,
                product.imageUrl,
                product.type,
                product.status,
                product.user.id,
                product.createdAt,
                product.updatedAt,
            )
        }
    }
}