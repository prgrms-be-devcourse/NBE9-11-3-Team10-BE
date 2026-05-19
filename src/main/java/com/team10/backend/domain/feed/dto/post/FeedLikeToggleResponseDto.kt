package com.team10.backend.domain.feed.dto.post

data class FeedLikeToggleResponseDto(
    val liked: Boolean,
    val likeCount: Int
)
