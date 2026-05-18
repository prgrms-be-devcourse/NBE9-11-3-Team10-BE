package com.team10.backend.domain.feed.controller
import com.team10.backend.domain.feed.repository.FeedPostRepository
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.global.security.CustomUserPrincipal
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc(addFilters = false)
class FeedControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,

    private val jdbcTemplate: JdbcTemplate,

    private val feedPostRepository: FeedPostRepository
) {

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
            "서초구",
            "ACTIVE",
            "SELLER"
        )

        jdbcTemplate.update(
            "INSERT INTO users " +
                    "(id, email, password, name, nickname, phone_number, address, user_status, role, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            2L,
            "user@test.com",
            "1234",
            "일반유저",
            "user1",
            "010-9876-5432",
            "강남구",
            "ACTIVE",
            "BUYER"
        )
    }

    @Test
    @DisplayName("피드 등록 테스트")
    @Throws(Exception::class)
    fun createFeed() {
        val requestBody = """
            {
              "content": "오늘의 새로운 소식!",
              "imageUrl": "https://image.url/1"
            }
            
            """.trimIndent()

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/stores/me/feeds")
                .with(authenticatedUser(1L, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(MockMvcResultMatchers.status().isCreated()) // 201 확인
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.content").value("오늘의 새로운 소식!"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.imageUrl").value("https://image.url/1"))

        Assertions.assertThat(feedPostRepository.findAll().first().imageUrl)
            .isEqualTo("https://image.url/1")
    }

    @Test
    @DisplayName("피드 등록 시 이미지가 없으면 imageUrl은 null")
    @Throws(Exception::class)
    fun createFeed_withoutImageUrl() {
        val requestBody = """
            {
              "content": "이미지 없는 소식!"
            }
            
            """.trimIndent()

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/stores/me/feeds")
                .with(authenticatedUser(1L, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.content").value("이미지 없는 소식!"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.imageUrl").isEmpty())

        Assertions.assertThat(feedPostRepository.findAll().first().imageUrl).isNull()
    }

    @Test
    @DisplayName("피드 전체 목록 조회 (최신순)")
    @Throws(Exception::class)
    fun getFeeds() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "https://test.com/image.jpg",  // imageUrl
            "조회 테스트용 피드입니다",  // content
            1L,  // user_id
            0,  // likeCount
            0 // commentCount
        )

        jdbcTemplate.update(
            "INSERT INTO feed_posts (image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "https://test.com/image.jpg",  // imageUrl
            "조회 테스트용 피드2입니다",  // content
            1L,  // user_id
            0,  // likeCount
            0 // commentCount
        )

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v1/stores/1/feeds")
                .with(authenticatedUser(2L, Role.BUYER))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.feeds[0].content").value("조회 테스트용 피드2입니다"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.feeds[1].content").value("조회 테스트용 피드입니다"))
    }

    @Test
    @DisplayName("피드 수정 테스트")
    @Throws(Exception::class)
    fun updateFeed() {
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

        val requestBody = """
            {
              "content": "수정된 피드입니다",
              "imageUrl": "https://test.com/new-image.jpg"
            }
            
            """.trimIndent()

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/api/v1/stores/me/feeds/100")
                .with(authenticatedUser(1L, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.feedId").value(100))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.content").value("수정된 피드입니다"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.imageUrl").value("https://test.com/new-image.jpg"))
    }

    @Test
    @DisplayName("피드 삭제 테스트")
    @Throws(Exception::class)
    fun deleteFeed() {
        jdbcTemplate.update(
            "INSERT INTO feed_posts (id, image_url, content, user_id, like_count, comment_count, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            101L,
            "https://test.com/image.jpg",
            "삭제할 피드입니다",
            1L,
            0,
            0
        )

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api/v1/stores/me/feeds/101")
                .with(authenticatedUser(1L, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))

        assert(feedPostRepository.findById(101L).isEmpty)
    }

    @Test
    @DisplayName("피드 좋아요 토글 테스트")
    @Throws(Exception::class)
    fun toggleFeedLike() {
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

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/stores/me/feeds/100/like")
                .with(authenticatedUser(2L, Role.BUYER))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.liked").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.likeCount").value(1))
    }

    private fun authenticatedUser(userId: Long, role: Role): RequestPostProcessor {
        return RequestPostProcessor { request: MockHttpServletRequest ->
            val securityContext = SecurityContextHolder.createEmptyContext()
            securityContext.authentication = UsernamePasswordAuthenticationToken(
                    CustomUserPrincipal(userId, role),
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_" + role.name))
                )
            SecurityContextHolder.setContext(securityContext)
            request
        }
    }
}
