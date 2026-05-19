package com.team10.backend.domain.feed.dto.post

import com.team10.backend.domain.feed.entity.FeedPost

data class FeedResponseDto(
    val feedId: Long,
    val content: String,
    val imageUrl: String?,
    val createdAt: String
) {
    companion object {
        fun from(feedPost: FeedPost): FeedResponseDto {
            return FeedResponseDto(
                feedPost.getId(),
                feedPost.content,
                feedPost.imageUrl,
                feedPost.getCreatedAt().toString()
            )
        }
    }
}
