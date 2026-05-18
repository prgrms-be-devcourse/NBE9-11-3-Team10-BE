package com.team10.backend.domain.feed.dto.post

import com.team10.backend.domain.feed.entity.FeedPost

data class CreateFeedResponseDto(
    val feedId: Long,
    val content: String,
    val imageUrl: String?,
    val createdAt: String
) {
    companion object {
        fun from(feedPost: FeedPost): CreateFeedResponseDto {
            return CreateFeedResponseDto(
                feedPost.getId(),
                feedPost.content,
                feedPost.imageUrl,
                feedPost.getCreatedAt().toString()
            )
        }
    }
}
