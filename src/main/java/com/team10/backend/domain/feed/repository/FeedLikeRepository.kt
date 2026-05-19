package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FeedLikeRepository : JpaRepository<FeedLike, Long> {
    fun findByFeedPostIdAndUserId(feedPostId: Long, userId: Long): FeedLike?

    @Query(
        """
        select fl.feedPost.id
        from FeedLike fl
        where fl.user.id = :userId
          and fl.feedPost.id in :feedPostIds
        """
    )
    fun findLikedFeedPostIdsByUserId(
        @Param("userId") userId: Long,
        @Param("feedPostIds") feedPostIds: List<Long>
    ): List<Long>
}
