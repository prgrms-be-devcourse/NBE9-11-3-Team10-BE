package com.team10.backend.fixture

import com.team10.backend.domain.feed.entity.FeedComment
import com.team10.backend.domain.feed.entity.FeedCommentLike
import com.team10.backend.domain.feed.entity.FeedLike
import com.team10.backend.domain.feed.entity.FeedPost
import com.team10.backend.domain.user.entity.User
import net.datafaker.Faker

object FeedFixture {
    private val faker = Faker()

    // ─────────────────────────────────────────────
    // ✅ FeedPost 생성 팩토리
    // ─────────────────────────────────────────────
    fun createPost(
        user: User,
        content: String = faker.lorem().paragraph(2),
        imageUrl: String? = faker.internet().url()
    ): FeedPost {
        return FeedPost(
            imageUrl = imageUrl,
            content = content,
            user = user
        )
    }

    /**
     * [시나리오] 이미지가 없는 텍스트 전용 피드
     */
    fun createTextOnlyPost(
        user: User,
        content: String = faker.lorem().paragraph(3)
    ): FeedPost = createPost(user = user, imageUrl = null, content = content)

    // ─────────────────────────────────────────────
    // ✅ FeedComment 생성 팩토리
    // ─────────────────────────────────────────────
    fun createComment(
        feedPost: FeedPost,
        writer: User,
        content: String = faker.lorem().sentence()
    ): FeedComment {
        return FeedComment(
            feedPost = feedPost,
            writer = writer,
            content = content
        )
    }

    // ─────────────────────────────────────────────
    // ✅ FeedLike 생성 팩토리
    // ─────────────────────────────────────────────
    fun createLike(
        feedPost: FeedPost,
        user: User
    ): FeedLike {
        return FeedLike(
            feedPost = feedPost,
            user = user
        )
    }

    // ─────────────────────────────────────────────
    // ✅ FeedCommentLike 생성 팩토리
    // ─────────────────────────────────────────────
    fun createCommentLike(
        feedComment: FeedComment,
        user: User
    ): FeedCommentLike {
        return FeedCommentLike(
            feedComment = feedComment,
            user = user
        )
    }
}