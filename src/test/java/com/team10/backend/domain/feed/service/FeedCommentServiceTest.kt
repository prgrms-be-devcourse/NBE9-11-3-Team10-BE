package com.team10.backend.domain.feed.service

import com.team10.backend.domain.feed.dto.comment.CreateCommentRequestDto
import com.team10.backend.domain.feed.dto.comment.UpdateCommentRequestDto
import com.team10.backend.domain.feed.repository.FeedCommentLikeRepository
import com.team10.backend.domain.feed.repository.FeedCommentRepository
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
class FeedCommentServiceTest @Autowired constructor(
    private val feedCommentService: FeedCommentService,

    private val feedCommentRepository: FeedCommentRepository,

    private val feedCommentLikeRepository: FeedCommentLikeRepository,

    private val feedPostRepository: FeedPostRepository,

    private val userRepository: UserRepository,

    private val jdbcTemplate: JdbcTemplate,

) {
    private lateinit var seller: User

    private lateinit var buyer: User


    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM feed_comment_likes")
        jdbcTemplate.update("DELETE FROM feed_comments")
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

        seller = userRepository.findById(1L).orElseThrow()
        buyer = userRepository.findById(2L).orElseThrow()

        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            100L,
            "https://test.com/image.jpg",
            "댓글 테스트용 피드",
            seller.id,
            0,
            0
        )
    }

    @Test
    @DisplayName("피드 댓글 생성 - 성공")
    fun createComment_success() {
        val request = CreateCommentRequestDto("댓글 내용입니다.")

        val result = feedCommentService.createComment(1L, 100L, request, buyer.id)
        feedCommentRepository.flush()
        feedPostRepository.flush()

        val commentCount = jdbcTemplate.queryForObject(
            "SELECT comment_count FROM feed_posts WHERE id = ?",
            Int::class.java,
            100L
        )

        Assertions.assertThat(result.commentId).isNotNull()
        Assertions.assertThat(result.content).isEqualTo("댓글 내용입니다.")
        Assertions.assertThat(result.writer.userId).isEqualTo(2L)
        Assertions.assertThat(result.isMine).isTrue()
        Assertions.assertThat(feedCommentRepository.count()).isEqualTo(1)
        Assertions.assertThat(commentCount).isEqualTo(1)
    }

    @Test
    @DisplayName("피드 댓글 조회 - 성공")
    fun getComments_success() {
        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            200L,
            100L,
            2L,
            "조회 댓글입니다.",
            1
        )

        jdbcTemplate.update(
            "INSERT INTO feed_comment_likes (feed_comment_id, user_id, created_at, updated_at) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            200L,
            2L
        )

        val result = feedCommentService.getComments(1L, 100L, 0, 20, "createdAt,asc", buyer.id)

        Assertions.assertThat(result.comments).hasSize(1)
        Assertions.assertThat(result.comments[0].content).isEqualTo("조회 댓글입니다.")
        Assertions.assertThat(result.comments[0].isLiked).isTrue()
        Assertions.assertThat(result.comments[0].isMine).isTrue()
        Assertions.assertThat(result.pagination.currentPage).isEqualTo(0)
        Assertions.assertThat(result.pagination.totalElements).isEqualTo(1)
    }

    @Test
    @DisplayName("피드 댓글 수정 - 성공")
    fun updateComment_success() {
        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            205L,
            100L,
            2L,
            "수정 전 댓글입니다.",
            1
        )

        jdbcTemplate.update(
            "INSERT INTO feed_comment_likes (feed_comment_id, user_id, created_at, updated_at) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            205L,
            2L
        )

        val request = UpdateCommentRequestDto("수정 후 댓글입니다.")

        val result = feedCommentService.updateComment(1L, 100L, 205L, request, buyer.id)
        feedCommentRepository.flush()

        val updatedContent = jdbcTemplate.queryForObject(
            "SELECT content FROM feed_comments WHERE id = ?",
            String::class.java,
            205L
        )
        val likeCount = jdbcTemplate.queryForObject(
            "SELECT like_count FROM feed_comments WHERE id = ?",
            Int::class.java,
            205L
        )

        Assertions.assertThat(result.commentId).isEqualTo(205L)
        Assertions.assertThat(result.content).isEqualTo("수정 후 댓글입니다.")
        Assertions.assertThat(result.writer.userId).isEqualTo(2L)
        Assertions.assertThat(result.isLiked).isTrue()
        Assertions.assertThat(result.isMine).isTrue()
        Assertions.assertThat(updatedContent).isEqualTo("수정 후 댓글입니다.")
        Assertions.assertThat(likeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("피드 댓글 수정 - 작성자가 아니면 예외 발생")
    fun updateComment_accessDenied() {
        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            206L,
            100L,
            1L,
            "판매자가 작성한 댓글입니다.",
            0
        )

        val request = UpdateCommentRequestDto("수정 시도입니다.")

        Assertions.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
            feedCommentService.updateComment(
                1L,
                100L,
                206L,
                request,
                buyer.id
            )
        })
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ErrorCode.COMMENT_ACCESS_DENIED.message)
    }

    @Test
    @DisplayName("피드 댓글 삭제 - 성공")
    fun deleteComment_success() {
        jdbcTemplate.update(
            "UPDATE feed_posts SET comment_count = ? WHERE id = ?",
            1,
            100L
        )

        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            201L,
            100L,
            2L,
            "삭제 댓글입니다.",
            0
        )

        feedCommentService.deleteComment(1L, 100L, 201L, buyer.id)
        feedCommentRepository.flush()
        feedPostRepository.flush()

        val commentCount = jdbcTemplate.queryForObject(
            "SELECT comment_count FROM feed_posts WHERE id = ?",
            Int::class.java,
            100L
        )

        Assertions.assertThat(feedCommentRepository.count()).isZero()
        Assertions.assertThat(commentCount).isEqualTo(0)
    }

    @Test
    @DisplayName("피드 댓글 삭제 - 피드 소유자이면 성공")
    fun deleteComment_feedOwnerSuccess() {
        jdbcTemplate.update(
            "UPDATE feed_posts SET comment_count = ? WHERE id = ?",
            1,
            100L
        )

        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            202L,
            100L,
            2L,
            "구매자가 작성한 댓글입니다.",
            0
        )

        feedCommentService.deleteComment(1L, 100L, 202L, seller.id)
        feedCommentRepository.flush()
        feedPostRepository.flush()

        val commentCount = jdbcTemplate.queryForObject(
            "SELECT comment_count FROM feed_posts WHERE id = ?",
            Int::class.java,
            100L
        )

        Assertions.assertThat(feedCommentRepository.count()).isZero()
        Assertions.assertThat(commentCount).isEqualTo(0)
    }

    @Test
    @DisplayName("피드 댓글 삭제 - 작성자가 아니면 예외 발생")
    fun deleteComment_accessDenied() {
        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            202L,
            100L,
            1L,
            "판매자가 작성한 댓글입니다.",
            0
        )

        Assertions.assertThatThrownBy{
            feedCommentService.deleteComment(
                1L,
                100L,
                202L,
                buyer.id
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(ErrorCode.COMMENT_ACCESS_DENIED.message)
    }

    @Test
    @DisplayName("피드 댓글 좋아요 토글 - 좋아요 추가 성공")
    fun toggleCommentLike_success() {
        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            203L,
            100L,
            1L,
            "좋아요 댓글입니다.",
            0
        )

        val result = feedCommentService.toggleCommentLike(1L, 100L, 203L, buyer.id)
        feedCommentLikeRepository.flush()
        feedCommentRepository.flush()

        val likeCount = jdbcTemplate.queryForObject(
            "SELECT like_count FROM feed_comments WHERE id = ?",
            Int::class.java,
            203L
        )
        val commentLikeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed_comment_likes WHERE feed_comment_id = ? AND user_id = ?",
            Int::class.java,
            203L,
            2L
        )

        Assertions.assertThat(result.liked).isTrue()
        Assertions.assertThat(result.likeCount).isEqualTo(1)
        Assertions.assertThat(likeCount).isEqualTo(1)
        Assertions.assertThat(commentLikeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("피드 댓글 좋아요 토글 - 좋아요 취소 성공")
    fun toggleCommentLike_cancelSuccess() {
        jdbcTemplate.update(
            "INSERT INTO feed_comments (id, feed_post_id, writer_id, content, like_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            204L,
            100L,
            1L,
            "좋아요 취소 댓글입니다.",
            1
        )

        jdbcTemplate.update(
            "INSERT INTO feed_comment_likes (feed_comment_id, user_id, created_at, updated_at) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            204L,
            2L
        )

        val result = feedCommentService.toggleCommentLike(1L, 100L, 204L, buyer.id)
        feedCommentLikeRepository.flush()
        feedCommentRepository.flush()

        val likeCount = jdbcTemplate.queryForObject(
            "SELECT like_count FROM feed_comments WHERE id = ?",
            Int::class.java,
            204L
        )
        val commentLikeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feed_comment_likes WHERE feed_comment_id = ? AND user_id = ?",
            Int::class.java,
            204L,
            2L
        )

        Assertions.assertThat(result.liked).isFalse()
        Assertions.assertThat(result.likeCount).isEqualTo(0)
        Assertions.assertThat(likeCount).isEqualTo(0)
        Assertions.assertThat(commentLikeCount).isEqualTo(0)
    }
}
