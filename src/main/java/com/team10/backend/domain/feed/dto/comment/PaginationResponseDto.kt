package com.team10.backend.domain.feed.dto.comment

data class PaginationResponseDto(
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Long
)
