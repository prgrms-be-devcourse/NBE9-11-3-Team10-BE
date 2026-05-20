package com.team10.backend.domain.feed.entity

import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "feed_comments")
class FeedComment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_post_id", nullable = false)
    var feedPost: FeedPost,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_id", nullable = false)
    var writer: User,

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String,

    @Column(nullable = false)
    var likeCount: Int = 0,

    @OneToMany(mappedBy = "feedComment", cascade = [CascadeType.ALL], orphanRemoval = true)
    val commentLikes: List<FeedCommentLike> = ArrayList()
) : BaseEntity() {

    fun increaseLikeCount() {
        this.likeCount++
    }

    fun decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--
        }
    }

    fun updateContent(content: String) {
        this.content = content
    }
}
