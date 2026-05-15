package com.team10.backend.domain.product.dto

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import java.time.LocalDateTime

data class ProductDetailResponse(
    @JvmField val productId: Long,
    @JvmField val productName: String,
    @JvmField val description: String?,
    @JvmField val price: Int,
    @JvmField val stock: Int,
    @JvmField val nickname: String,
    @JvmField val imageUrl: String?,
    @JvmField val type: ProductType,
    @JvmField val status: ProductStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        @JvmStatic
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