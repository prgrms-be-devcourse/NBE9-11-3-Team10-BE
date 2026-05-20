package com.team10.backend.domain.feed.entity

import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "feed_posts")
class FeedPost(
    @Column
    var imageUrl: String? = null,

    @Column(columnDefinition = "TEXT")
    var content: String,

    @ManyToOne
    @JoinColumn(name = "user_id")
    var user: User,

    @OneToMany(mappedBy = "feedPost", cascade = [CascadeType.ALL], orphanRemoval = true)
    val feedLikes: List<FeedLike> = ArrayList(),

    @OneToMany(mappedBy = "feedPost", cascade = [CascadeType.ALL], orphanRemoval = true)
    val comments: List<FeedComment> = ArrayList()
) : BaseEntity() {

    var likeCount: Int = 0
    var commentCount: Int = 0

    fun update(imageUrl: String?, content: String) {
        this.imageUrl = imageUrl
        this.content = content
    }

    fun increaseLikeCount() {
        this.likeCount++
    }

    fun decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--
        }
    }

    fun increaseCommentCount() {
        this.commentCount++
    }

    fun decreaseCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--
        }
    }
}
