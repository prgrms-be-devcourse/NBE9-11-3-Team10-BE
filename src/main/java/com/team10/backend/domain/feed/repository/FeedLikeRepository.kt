package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedLike
import org.springframework.data.jpa.repository.JpaRepository

interface FeedLikeRepository : JpaRepository<FeedLike, Long> {
    fun findByFeedPostIdAndUserId(feedPostId: Long, userId: Long): FeedLike?
}
