package com.team10.backend.domain.product.dto

data class ProductPageResponse(
    @JvmField val content: List<ProductListResponse>,
    @JvmField val page: Int,
    @JvmField val size: Int,
    @JvmField val totalElements: Long,
    @JvmField val totalPages: Int
)