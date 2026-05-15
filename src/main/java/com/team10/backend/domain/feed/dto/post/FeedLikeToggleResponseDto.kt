package com.team10.backend.domain.feed.dto.post

data class FeedLikeToggleResponseDto(
    @JvmField val liked: Boolean,
    @JvmField val likeCount: Int
)
