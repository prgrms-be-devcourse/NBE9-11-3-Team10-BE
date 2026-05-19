package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedCommentLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FeedCommentLikeRepository : JpaRepository<FeedCommentLike, Long> {
    fun findByFeedCommentIdAndUserId(feedCommentId: Long, userId: Long): FeedCommentLike?
    fun existsByFeedCommentIdAndUserId(feedCommentId: Long, userId: Long): Boolean

    @Query(
        """
        select fcl.feedComment.id
        from FeedCommentLike fcl
        where fcl.user.id = :userId
          and fcl.feedComment.id in :commentIds
        """
    )
    fun findLikedCommentIdsByUserId(
        @Param("userId") userId: Long,
        @Param("commentIds") commentIds: List<Long>
    ): List<Long>
}
