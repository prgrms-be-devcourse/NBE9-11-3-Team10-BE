package com.team10.backend.domain.feed.dto.comment

data class CommentLikeToggleResponseDto(
    @JvmField val liked: Boolean,
    @JvmField val likeCount: Int
)
