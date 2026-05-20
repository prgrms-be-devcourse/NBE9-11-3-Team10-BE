package com.team10.backend.domain.feed.dto.comment

data class CommentLikeToggleResponseDto(
    val liked: Boolean,
    val likeCount: Int
)
