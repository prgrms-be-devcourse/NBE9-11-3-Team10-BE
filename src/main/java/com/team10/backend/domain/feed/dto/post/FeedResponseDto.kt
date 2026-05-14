package com.team10.backend.domain.feed.dto.post

import com.team10.backend.domain.feed.entity.FeedPost

data class FeedResponseDto(
    @JvmField val feedId: Long,
    @JvmField val content: String,
    @JvmField val imageUrl: String?,
    val createdAt: String
) {
    companion object {
        @JvmStatic
        fun from(feedPost: FeedPost): FeedResponseDto {
            return FeedResponseDto(
                feedPost.getId(),
                feedPost.getContent(),
                feedPost.getImageUrl(),
                feedPost.getCreatedAt().toString()
            )
        }
    }
}
