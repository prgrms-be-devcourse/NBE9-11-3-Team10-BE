package com.team10.backend.domain.feed.dto.post

data class FeedListResponseDto(
    @JvmField val feeds: List<FeedDto> //
) {
    companion object {
        fun from(feeds: List<FeedDto>): FeedListResponseDto {
            return FeedListResponseDto(feeds)
        }
    }
}