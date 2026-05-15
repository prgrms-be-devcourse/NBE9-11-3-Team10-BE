package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedCommentLike
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface FeedCommentLikeRepository : JpaRepository<FeedCommentLike, Long> {
    fun findByFeedComment_IdAndUser_Id(feedCommentId: Long, userId: Long): Optional<FeedCommentLike>
    fun existsByFeedComment_IdAndUser_Id(feedCommentId: Long, userId: Long): Boolean
}
