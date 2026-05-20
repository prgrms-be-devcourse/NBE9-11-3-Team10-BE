package com.team10.backend.e2e_test.seed.fixture

import com.team10.backend.domain.feed.entity.FeedPost
import com.team10.backend.domain.user.entity.User
import java.time.LocalDateTime

/**
 * ✅ Mock Server 의 mock-feed-data.ts 에 정의된 피드 데이터 매핑
 * - E2E 테스트용 고정 데이터 (재현성 보장)
 * - feedId 는 DB 의 IDENTITY 로 자동 생성되므로 무시
 */
object FeedSeedFixture {

    data class SeedFeedData(
        val content: String,
        val mediaUrls: List<String>,
        val likeCount: Int = 0,
        val commentCount: Int = 0,
        val createdAt: LocalDateTime = LocalDateTime.now(),
        val isNotice: Boolean = false
    )

    // Mock Server 의 feeds 배열과 1:1 매핑 (3 개 초기 피드)
    val SEED_FEEDS: List<SeedFeedData> = listOf(
        SeedFeedData(
            content = "📚 이번 주 신규 입고 도서 소개합니다! 인기 베스트셀러부터 숨은 명작까지 다양하게 준비했어요.",
            mediaUrls = listOf(
                "https://example.com/images/feed1-1.jpg",
                "https://example.com/images/feed1-2.jpg"
            ),
            likeCount = 24,
            commentCount = 5,
            createdAt = LocalDateTime.now()
        ),
        SeedFeedData(
            content = "✨ 독자 리뷰 이벤트 당첨자 발표! 축하드립니다 🎉",
            mediaUrls = emptyList(),
            likeCount = 89,
            commentCount = 12,
            createdAt = LocalDateTime.now().minusDays(1) // 어제
        ),
        SeedFeedData(
            content = "📖 독서의 계절, 가을을 맞이한 북스 스토어의 추천 리스트",
            mediaUrls = listOf("https://example.com/images/feed3-1.jpg"),
            likeCount = 156,
            commentCount = 28,
            createdAt = LocalDateTime.now().minusDays(2) // 2 일 전
        )
    )

    /**
     * SeedFeedData → 실제 FeedPost Entity 로 변환
     * - mediaUrls 는 첫 번째 이미지를 imageUrl 로 매핑 (단일 이미지 제약 시)
     * - likeCount/commentCount 는 도메인 메서드로 동기화
     */
    fun toEntity(seller: User, data: SeedFeedData): FeedPost {
        return FeedPost(
            imageUrl = data.mediaUrls.firstOrNull(), // 단일 이미지 매핑
            content = data.content,
            user = seller
        ).apply {
            // 카운터 동기화 (도메인 로직 존중)
            repeat(data.likeCount) { increaseLikeCount() }
            repeat(data.commentCount) { increaseCommentCount() }
        }
    }
}