package com.team10.backend.e2e_test.seed.helper

import com.team10.backend.domain.feed.entity.FeedPost
import com.team10.backend.domain.feed.repository.FeedCommentRepository
import com.team10.backend.domain.feed.repository.FeedPostRepository
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.e2e_test.seed.fixture.CommentSeedFixture
import com.team10.backend.e2e_test.seed.fixture.FeedSeedFixture
import com.team10.backend.e2e_test.seed.fixture.UserSeedFixture
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class FeedSeedHelper(
    private val userRepository: UserRepository,
    private val feedPostRepository: FeedPostRepository,
    private val feedCommentRepository: FeedCommentRepository
) {

    /**
     * ✅ Mock Server 의 FeedStore.reset() 이식
     * - SELLER 사용자 조회 → 시드 피드 일괄 저장 → 댓글 연동 저장
     */
    @Transactional
    fun seedSellerFeeds() {
        // 1. 판매자 계정 조회
        val seller = userRepository.findByEmailAndRole("seller@example.com", Role.SELLER)
            ?: throw IllegalStateException("SELLER user not found.")

        // 2. 이미 시딩된 피드 확인 (재실행 방지)
        val existingCount = feedPostRepository.countByUserAndContentIn(
            seller,
            FeedSeedFixture.SEED_FEEDS.map { it.content }
        )
        if (existingCount >= FeedSeedFixture.SEED_FEEDS.size) {
            return  // 이미 시딩됨
        }

        // 3. 피드 일괄 생성 및 저장
        val savedFeeds = FeedSeedFixture.SEED_FEEDS.map { data ->
            val feed = FeedSeedFixture.toEntity(seller, data)
            feedPostRepository.save(feed)
        }

        // 4. 댓글 연동 저장 (피드 내용 키워드로 매칭)
        seedCommentsForFeeds(savedFeeds)
    }

    /**
     * 피드별 댓글 매핑 및 저장
     */
    private fun seedCommentsForFeeds(feeds: List<FeedPost>) {
        val buyer = userRepository.findByEmailAndRole("buyer@example.com", Role.BUYER)
            ?: return

        // UserSeedFixture 에 테스트 사용자 참조 보관 (CommentSeedHelper 에서 활용)
        UserSeedFixture.TEST_BUYER = buyer

        CommentSeedFixture.SEED_COMMENTS.forEach { commentData ->
            // 키워드로 대상 피드 찾기 (contains 매칭)
            val targetFeed = feeds.find { feed ->
                feed.content.contains(commentData.feedContentKeyword)
            } ?: return@forEach

            val comment = CommentSeedFixture.toEntity(targetFeed, commentData.writer, commentData)
            feedCommentRepository.save(comment)
        }
    }

    /**
     * [유틸] 특정 키워드로 피드 조회 (테스트 코드에서 활용)
     */
    fun findByContentKeyword(keyword: String): FeedPost? {
        return feedPostRepository.findByContentContaining(keyword)
    }
}