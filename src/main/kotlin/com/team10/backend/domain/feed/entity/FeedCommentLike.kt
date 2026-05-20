package com.team10.backend.domain.feed.entity

import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "feed_comment_likes")
class FeedCommentLike(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_comment_id", nullable = false)
    var feedComment: FeedComment,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User

) : BaseEntity()
