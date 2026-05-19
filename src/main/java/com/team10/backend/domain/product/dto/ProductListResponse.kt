package com.team10.backend.domain.product.dto

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import java.time.LocalDateTime

data class ProductListResponse(
    val productId: Long,
    val productName: String,
    val price: Int,
    val nickname: String,
    val imageUrl: String?,
    val type: ProductType,
    val status: ProductStatus,
    val sellerId: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
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