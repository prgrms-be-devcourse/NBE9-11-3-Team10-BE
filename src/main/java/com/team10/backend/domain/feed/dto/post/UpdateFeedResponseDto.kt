package com.team10.backend.domain.feed.dto.post

import com.team10.backend.domain.feed.entity.FeedPost

data class UpdateFeedResponseDto(
    @JvmField val feedId: Long,
    @JvmField val content: String,
    @JvmField val imageUrl: String?,
    val updatedAt: String
) {
    companion object {
        @JvmStatic
        fun from(feedPost: FeedPost): UpdateFeedResponseDto {
            return UpdateFeedResponseDto(
                feedPost.getId(),
                feedPost.getContent(),
                feedPost.getImageUrl(),
                feedPost.getUpdatedAt().toString()
            )
        }
    }
}
