package com.team10.backend.domain.product.dto

data class ProductPageResponse(
    val content: List<ProductListResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)