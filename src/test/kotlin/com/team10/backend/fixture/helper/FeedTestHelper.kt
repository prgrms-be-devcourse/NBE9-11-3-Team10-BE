package com.team10.backend.fixture.helper

import com.team10.backend.domain.feed.entity.FeedComment
import com.team10.backend.domain.feed.entity.FeedCommentLike
import com.team10.backend.domain.feed.entity.FeedLike
import com.team10.backend.domain.feed.entity.FeedPost
import com.team10.backend.domain.feed.repository.FeedCommentLikeRepository
import com.team10.backend.domain.feed.repository.FeedCommentRepository
import com.team10.backend.domain.feed.repository.FeedLikeRepository
import com.team10.backend.domain.feed.repository.FeedPostRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.FeedFixture
import com.team10.backend.fixture.UserFixture
import net.datafaker.Faker

/**
 * 통합 테스트용 Feed 생성 헬퍼
 * - User → Post → Comment/Like → CommentLike 순서로 저장 및 관계 동기화
 * - 반환된 [FeedContext] 를 통해 테스트에서 필요한 엔티티 접근 가능
 */
class FeedTestHelper(
    private val userRepository: UserRepository,
    private val feedPostRepository: FeedPostRepository,
    private val feedCommentRepository: FeedCommentRepository,
    private val feedLikeRepository: FeedLikeRepository,
    private val feedCommentLikeRepository: FeedCommentLikeRepository,
    private val faker: Faker = Faker()
) {
    /**
     * 테스트 결과 컨텍스트: 저장된 피드 관련 엔티티들을 묶어서 반환
     */
    data class FeedContext(
        val author: User,
        val post: FeedPost,
        val comments: List<FeedComment> = emptyList(),
        val likes: List<FeedLike> = emptyList(),
        val commentLikes: List<FeedCommentLike> = emptyList()
    )

    /**
     * [기본] 완전한 피드 시나리오 생성
     * - 작성자, 피드, 댓글, 좋아요, 댓글좋아요 전체 생성 및 DB 저장
     * - 도메인 카운터(likeCount, commentCount) 자동 동기화
     */
    fun createCompleteFeed(
        author: User? = null,
        writers: List<User>? = null, // 댓글 작성자 풀 (없으면 자동 생성)
        commentCount: Int = 2,
        likeCount: Int = 3,
        commentLikeCount: Int = 1
    ): FeedContext {
        // 1. Author 생성 (없으면)
        val savedAuthor = author ?: userRepository.save(UserFixture.create(role = Role.BUYER))

        // 2. FeedPost 생성 및 저장
        val post = FeedFixture.createPost(user = savedAuthor)
        val savedPost = feedPostRepository.save(post)

        // 3. Comments 생성 및 저장
        val comments = (1..commentCount).map { idx ->
            val writer = writers?.getOrNull(idx) ?: userRepository.save(UserFixture.create(role = Role.BUYER))
            val comment = FeedFixture.createComment(feedPost = savedPost, writer = writer)

            savedPost.increaseCommentCount() // 카운터 도메인 로직 동기화
            feedCommentRepository.save(comment)
            comment
        }

        // 4. FeedLikes 생성 및 저장
        val likes = (1..likeCount).map {
            val user = userRepository.save(UserFixture.create(role = Role.BUYER))
            val like = FeedFixture.createLike(feedPost = savedPost, user = user)

            savedPost.increaseLikeCount() // 카운터 도메인 로직 동기화
            feedLikeRepository.save(like)
            like
        }

        // 5. FeedCommentLikes 생성 및 저장
        val commentLikes = comments.flatMap { comment ->
            (1..commentLikeCount).map {
                val user = userRepository.save(UserFixture.create(role = Role.BUYER))
                val cLike = FeedFixture.createCommentLike(feedComment = comment, user = user)

                comment.increaseLikeCount() // 댓글 좋아요 카운터 동기화
                feedCommentLikeRepository.save(cLike)
                cLike
            }
        }

        // 카운터 변경사항 DB 반영 (Dirty Checking 또는 명시적 save)
        feedPostRepository.save(savedPost)

        return FeedContext(
            author = savedAuthor,
            post = savedPost,
            comments = comments,
            likes = likes,
            commentLikes = commentLikes
        )
    }

    /**
     * [간소화] 최소 정보만으로 피드 생성 (댓글/좋아요 없음)
     */
    fun createMinimalPost(author: User? = null): FeedContext {
        return createCompleteFeed(
            author = author,
            commentCount = 0,
            likeCount = 0,
            commentLikeCount = 0
        )
    }

    /**
     * [유연성] 커스텀 빌더 스타일 생성 (택사항)
     */
    fun feedBuilder() = FeedBuilderHelper(this)
}

/**
 * 플루언트 빌더 스타일 헬퍼 (OrderTestHelper 패턴 준수)
 */
class FeedBuilderHelper(
    private val helper: FeedTestHelper
) {
    private var author: com.team10.backend.domain.user.entity.User? = null
    private var commentCount = 2
    private var likeCount = 3
    private var commentLikeCount = 1

    fun author(author: com.team10.backend.domain.user.entity.User) = apply { this.author = author }
    fun comments(count: Int) = apply { this.commentCount = count }
    fun likes(count: Int) = apply { this.likeCount = count }
    fun commentLikes(count: Int) = apply { this.commentLikeCount = count }

    fun build(): FeedTestHelper.FeedContext {
        return helper.createCompleteFeed(
            author = author,
            commentCount = commentCount,
            likeCount = likeCount,
            commentLikeCount = commentLikeCount
        )
    }
}