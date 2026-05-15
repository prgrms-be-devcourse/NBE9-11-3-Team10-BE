package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedLike
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface FeedLikeRepository : JpaRepository<FeedLike, Long> {
    fun findByFeedPostId_AndUser_Id(feedPostId: Long, userId: Long): Optional<FeedLike>
}
