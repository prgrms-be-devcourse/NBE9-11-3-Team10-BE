package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedComment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface FeedCommentRepository : JpaRepository<FeedComment, Long> {
    fun findAllByFeedPostId(feedPostId: Long, pageable: Pageable): Page<FeedComment>
    fun findByIdAndFeedPostId(id: Long, feedPostId: Long): Optional<FeedComment>
}
