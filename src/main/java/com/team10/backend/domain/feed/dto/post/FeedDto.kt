package com.team10.backend.domain.feed.dto.post

import com.team10.backend.domain.feed.entity.FeedPost
import java.time.LocalDateTime

data class FeedDto(
    val id: Long,
    val imageUrl: String?,
    val content: String,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean,  // 현재 로그인한 유저가 좋아요를 눌렀는지 여부
    val isNotice: Boolean,  // 공지사항 여부
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(feed: FeedPost, isLiked: Boolean): FeedDto {
            return FeedDto(
                feed.getId(),
                feed.imageUrl,
                feed.content,
                feed.likeCount,
                feed.commentCount,
                isLiked,
                false,  // 엔티티에 isNotice 필드가 있다면 feed.isNotice()로 변경
                feed.getCreatedAt()
            )
        }
    }
}
