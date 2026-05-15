package com.team10.backend.domain.feed.dto.comment

data class PaginationResponseDto(
    @JvmField val currentPage: Int,
    val totalPages: Int,
    @JvmField val totalElements: Long
)
