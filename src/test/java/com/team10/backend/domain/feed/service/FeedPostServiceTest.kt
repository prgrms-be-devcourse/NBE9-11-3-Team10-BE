package com.team10.backend.domain.feed.service

import com.team10.backend.domain.feed.dto.post.CreateFeedRequestDto
import com.team10.backend.domain.feed.dto.post.UpdateFeedRequestDto
import com.team10.backend.domain.feed.repository.FeedLikeRepository
import com.team10.backend.domain.feed.repository.FeedPostRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.assertj.core.api.Assertions
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc(addFilters = false)
class FeedPostServiceTest @Autowired constructor(

    private val feedPostRepository: FeedPostRepository,

    private val feedLikeRepository: FeedLikeRepository,

    private val feedPostService: FeedPostService,

    private val userRepository: UserRepository,

    private val jdbcTemplate: JdbcTemplate


) {

    private lateinit var testUser: User
    private lateinit var buyerUser: User

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM feed_likes")
        jdbcTemplate.update("DELETE FROM feed_posts")
        jdbcTemplate.update("DELETE FROM users")

        jdbcTemplate.update(
            "INSERT INTO users " +
                    "(id, email, password, name, nickname, phone_number, address, user_status, role, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            1L,
            "seller@test.com",
            "1234",
            "테스트판매자",
            "seller1",
            "010-1234-5678",
            "서울시",
            "ACTIVE",
            "SELLER"
        )

        jdbcTemplate.update(
            "INSERT INTO users " +
                    "(id, email, password, name, nickname, phone_number, address, user_status, role, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            2L,
            "buyer@test.com",
            "1234",
            "테스트구매자",
            "buyer1",
            "010-9999-9999",
            "서울시",
            "ACTIVE",
            "BUYER"
        )

        testUser = userRepository.findById(1L).orElseThrow()
        buyerUser = userRepository.findById(2L).orElseThrow()
    }

    @Test
    @DisplayName("피드 목록 조회 - 성공 (좋아요 여부 포함)")
    fun getFeedsList_success() {
        // given
        val sellerId = 1L
        jdbcTemplate.update(
            "INSERT INTO feed_posts (image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "https://test.com/image.jpg",
            "테스트 내용",
            sellerId,
            0,
            0
        )

        // when
        val result = feedPostService.getFeedsList(sellerId, testUser.id)

        // then
        Assertions.assertThat(result.feeds).hasSize(1)
        Assertions.assertThat(result.feeds[0].content).isEqualTo("테스트 내용")
    }

    @Test
    @DisplayName("피드 목록 조회 - 데이터 없음 (예외 발생)")
    fun getFeedsList_notFound() {
        val sellerId = 1L

        Assertions.assertThatThrownBy{
            feedPostService.getFeedsList(
                sellerId,
                testUser.id
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ErrorCode.FEED_NOT_FOUND.message)
    }

    @Test
    @DisplayName("피드 생성 - 성공")
    fun createFeed_success() {
        val requestDto = CreateFeedRequestDto(
            "테스트 피드 내용입니다.",
            "https://test-image.com"
        )


        val result = feedPostService.createFeed(requestDto, testUser.id)


        Assertions.assertThat(result.feedId).isNotNull()
        Assertions.assertThat(result.content).isEqualTo("테스트 피드 내용입니다.")
        Assertions.assertThat(result.imageUrl).isEqualTo("https://test-image.com")
        Assertions.assertThat(feedPostRepository.count()).isEqualTo(1)
    }

    @Test
    @DisplayName("피드 수정 - 성공")
    fun updateFeed_success() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            100L,
            "https://test.com/old-image.jpg",
            "수정 전 피드입니다",
            1L,
            0,
            0
        )

        val requestDto = UpdateFeedRequestDto(
            "수정된 피드입니다",
            "https://test.com/new-image.jpg"
        )

        val result = feedPostService.updateFeed(100L, requestDto, testUser.id)

        Assertions.assertThat(result.feedId).isEqualTo(100L)
        Assertions.assertThat(result.content).isEqualTo("수정된 피드입니다")
        Assertions.assertThat(result.imageUrl).isEqualTo("https://test.com/new-image.jpg")

        val updatedFeed = feedPostRepository.findById(100L).orElseThrow()
        Assertions.assertThat(updatedFeed.content).isEqualTo("수정된 피드입니다")
        Assertions.assertThat(updatedFeed.imageUrl).isEqualTo("https://test.com/new-image.jpg")
    }

    @Test
    @DisplayName("피드 수정 - 작성자가 아니면 예외 발생")
    fun updateFeed_accessDenied() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            101L,
            "https://test.com/old-image.jpg",
            "수정 전 피드입니다",
            1L,
            0,
            0
        )

        val requestDto = UpdateFeedRequestDto(
            "권한 없는 수정입니다",
            "https://test.com/new-image.jpg"
        )

        Assertions.assertThatThrownBy {
            feedPostService.updateFeed(
                101L,
                requestDto,
                buyerUser.id
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ErrorCode.ACCESS_DENIED.message)
    }

    @Test
    @DisplayName("피드 삭제 - 성공")
    fun deleteFeed_success() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            102L,
            "https://test.com/image.jpg",
            "삭제할 피드입니다",
            1L,
            0,
            0
        )

        feedPostService.deleteFeed(102L, testUser.id)

        Assertions.assertThat(feedPostRepository.findById(102L)).isEmpty()
    }

    @Test
    @DisplayName("피드 삭제 - 작성자가 아니면 예외 발생")
    fun deleteFeed_accessDenied() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            103L,
            "https://test.com/image.jpg",
            "삭제 권한 없는 피드입니다",
            1L,
            0,
            0
        )

        Assertions.assertThatThrownBy {
            feedPostService.deleteFeed(
                103L,
                buyerUser.id
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ErrorCode.ACCESS_DENIED.message)
        Assertions.assertThat(feedPostRepository.findById(103L)).isPresent()
    }

    @Test
    @DisplayName("피드 좋아요 토글 - 좋아요 추가 성공")
    fun toggleFeedLike_success() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            100L,
            "https://test.com/image.jpg",
            "좋아요 테스트용 피드",
            1L,
            0,
            0
        )

        val result = feedPostService.toggleFeedLike(100L, buyerUser.id)
        feedLikeRepository.flush()
        feedPostRepository.flush()

        val likeCount = jdbcTemplate.queryForObject(
            "SELECT like_count FROM feed_posts WHERE id = ?",
            Int::class.java,
            100L
        )
        val feedLikeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed_likes WHERE feed_post_id = ? AND user_id = ?",
            Int::class.java,
            100L,
            2L
        )

        Assertions.assertThat(result.liked).isTrue()
        Assertions.assertThat(result.likeCount).isEqualTo(1)
        Assertions.assertThat(likeCount).isEqualTo(1)
        Assertions.assertThat(feedLikeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("피드 좋아요 토글 - 좋아요 취소 성공")
    fun toggleFeedLike_cancelSuccess() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            101L,
            "https://test.com/image.jpg",
            "좋아요 취소 테스트용 피드",
            1L,
            1,
            0
        )

        jdbcTemplate.update(
            "INSERT INTO feed_likes (feed_post_id, user_id, created_at, updated_at) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            101L,
            2L
        )

        val result = feedPostService.toggleFeedLike(101L, buyerUser.id)
        feedLikeRepository.flush()
        feedPostRepository.flush()

        val likeCount = jdbcTemplate.queryForObject(
            "SELECT like_count FROM feed_posts WHERE id = ?",
            Int::class.java,
            101L
        )
        val feedLikeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed_likes WHERE feed_post_id = ? AND user_id = ?",
            Int::class.java,
            101L,
            2L
        )

        Assertions.assertThat(result.liked).isFalse()
        Assertions.assertThat(result.likeCount).isEqualTo(0)
        Assertions.assertThat(likeCount).isEqualTo(0)
        Assertions.assertThat(feedLikeCount).isEqualTo(0)
    }
}
