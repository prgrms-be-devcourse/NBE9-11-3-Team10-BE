package com.team10.backend.domain.feed.dto.comment

data class CommentListResponseDto(
    @JvmField val comments: List<CommentResponseDto>,
    @JvmField val pagination: PaginationResponseDto
)
