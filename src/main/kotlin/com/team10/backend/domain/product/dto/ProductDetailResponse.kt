package com.team10.backend.domain.product.dto

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import java.time.LocalDateTime

data class ProductDetailResponse(
    val productId: Long,
    val productName: String,
    val description: String?,
    val price: Int,
    val stock: Int,
    val nickname: String,
    val imageUrl: String?,
    val type: ProductType,
    val status: ProductStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(product: Product): ProductDetailResponse {
            return ProductDetailResponse(
                product.id,
                product.productName,
                product.description,
                product.price,
                product.stock,
                product.user.nickname,
                product.imageUrl,
                product.type,
                product.status,
                product.createdAt,
                product.updatedAt
            )
        }
    }
}