package com.team10.backend.domain.feed.repository

import com.team10.backend.domain.feed.entity.FeedPost
import org.springframework.data.jpa.repository.JpaRepository

interface FeedPostRepository : JpaRepository<FeedPost, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(sellerId: Long): List<FeedPost>
}