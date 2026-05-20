package com.team10.backend.domain.feed.dto.post

import com.team10.backend.domain.feed.entity.FeedPost

data class UpdateFeedResponseDto(
    val feedId: Long,
    val content: String,
    val imageUrl: String?,
    val updatedAt: String
) {
    companion object {
        fun from(feedPost: FeedPost): UpdateFeedResponseDto {
            return UpdateFeedResponseDto(
                feedPost.id,
                feedPost.content,
                feedPost.imageUrl,
                feedPost.updatedAt.toString()
            )
        }
    }
}
