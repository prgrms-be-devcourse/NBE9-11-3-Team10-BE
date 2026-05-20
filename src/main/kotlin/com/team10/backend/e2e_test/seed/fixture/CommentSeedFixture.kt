package com.team10.backend.e2e_test.seed.fixture

import com.team10.backend.domain.feed.entity.FeedComment
import com.team10.backend.domain.feed.entity.FeedPost
import com.team10.backend.domain.user.entity.User

/**
 * ✅ Mock Server 의 mock-feed-data.ts 에 정의된 댓글 데이터 매핑
 * - commentId 는 DB 의 IDENTITY 로 자동 생성
 */
object CommentSeedFixture {

    data class SeedCommentData(
        val feedContentKeyword: String,  // 대상 피드를 식별할 키워드
        val writer: User,
        val content: String,
        val likeCount: Int = 0,
        val writerNickname: String = writer.nickname,
        val writerProfileImageUrl: String? = "https://example.com/images/avatars/default.png"
    )

    // Mock Server 의 initFeedData() 내 addComment() 호출과 매핑
    // ⚠️ 실제 저장 시 FeedSeedHelper 에서 피드 참조 후 생성되므로,
    // 여기서는 "어떤 피드에 어떤 댓글을 달지"에 대한 메타데이터만 정의
    val SEED_COMMENTS: List<SeedCommentData> = listOf(
        // feed-001 ("신규 입고 도서") 에 대한 댓글
        SeedCommentData(
            feedContentKeyword = "신규 입고",
            writer = UserSeedFixture.TEST_BUYER!!, // Buyer fixture 참조
            content = "기대됩니다! 언제 배송되나요?",
            likeCount = 3
        ),
        SeedCommentData(
            feedContentKeyword = "신규 입고",
            writer = UserSeedFixture.TEST_BUYER!!,
            content = "이번 주 특가 정말 좋네요 👍",
            likeCount = 7
        ),
        // feed-002 ("당첨자 발표") 에 대한 댓글
        SeedCommentData(
            feedContentKeyword = "당첨자 발표",
            writer = UserSeedFixture.TEST_BUYER!!,
            content = "와 당첨됐어요! 감사합니다 🙏",
            likeCount = 12
        )
    )

    /**
     * SeedCommentData → 실제 FeedComment Entity 로 변환
     */
    fun toEntity(feedPost: FeedPost, writer: User, data: SeedCommentData): FeedComment {
        return FeedComment(
            feedPost = feedPost,
            writer = writer,
            content = data.content
        ).apply {
            // 좋아요 카운터 동기화
            repeat(data.likeCount) { increaseLikeCount() }
        }
    }
}