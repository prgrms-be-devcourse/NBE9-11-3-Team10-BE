package com.team10.backend.domain.feed.entity

import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "feed_likes",
    uniqueConstraints = [UniqueConstraint(name = "uk_feed_like_post_user", columnNames = ["feed_post_id", "user_id"])]
)
class FeedLike(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_post_id", nullable = false)
    var feedPost: FeedPost,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User

) : BaseEntity()
