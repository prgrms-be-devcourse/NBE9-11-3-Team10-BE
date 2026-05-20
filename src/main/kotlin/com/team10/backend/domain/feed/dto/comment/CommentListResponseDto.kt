package com.team10.backend.domain.feed.dto.comment

data class CommentListResponseDto(
    val comments: List<CommentResponseDto>,
    val pagination: PaginationResponseDto
)
