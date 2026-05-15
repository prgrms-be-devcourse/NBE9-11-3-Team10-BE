package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedCommentLike
import org.springframework.data.jpa.repository.JpaRepository

interface FeedCommentLikeRepository : JpaRepository<FeedCommentLike, Long> {
    fun findByFeedCommentIdAndUserId(feedCommentId: Long, userId: Long): FeedCommentLike?
    fun existsByFeedCommentIdAndUserId(feedCommentId: Long, userId: Long): Boolean
}
