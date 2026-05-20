package com.team10.backend.domain.auth.unit

import com.team10.backend.domain.auth.entity.RefreshToken
import com.team10.backend.domain.auth.repository.RefreshTokenRepository
import com.team10.backend.domain.auth.service.RefreshTokenService
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.security.TokenProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class RefreshTokenServiceTest {
    @Mock
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Mock
    lateinit var tokenProvider: TokenProvider

    @InjectMocks
    lateinit var refreshTokenService: RefreshTokenService

    @Test
    @DisplayName("refreshToken 생성 성공")
    fun createRefreshToken_success() {
        // given
        val user = UserFixture.create()

        val savedToken = Mockito.mock<RefreshToken>()

        whenever(refreshTokenRepository.save(any())).thenReturn(savedToken)

        // when
        val result = refreshTokenService.createRefreshToken(user)

        // then
        Assertions.assertNotNull(result)
        Assertions.assertFalse(result.isBlank())

        verify(refreshTokenRepository).save(any())
    }


    @Test
    @DisplayName("토큰 재발급 성공")
    fun refresh_success() {
        // given
        val user = UserFixture.create().apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }

        val refreshToken = Mockito.mock<RefreshToken>()

        whenever(refreshToken.user).thenReturn(user)
        whenever(refreshToken.revoked).thenReturn(false)
        whenever(refreshToken.expiresAt).thenReturn(
            LocalDateTime.now().plusDays(1)
        )

        whenever(refreshTokenRepository.findByToken("old-token"))
            .thenReturn(refreshToken)

        whenever(tokenProvider.generateToken(user.id, user.role))
            .thenReturn("new-access-token")

        whenever(refreshTokenRepository.save(any()))
            .thenReturn(Mockito.mock<RefreshToken>())

        // when
        val result = refreshTokenService.refresh("old-token")

        // then
        Assertions.assertEquals("new-access-token", result.accessToken)
        Assertions.assertNotNull(result.refreshToken)

        verify(refreshToken).revoke()
        verify(refreshTokenRepository).save(any())
    }

    @Test
    @DisplayName("refreshToken이 null인 경우")
    fun refresh_fail_null_token() {
        // when & then
        val ex =
            assertThrows<BusinessException> { refreshTokenService.refresh(null) }

        Assertions.assertEquals(ErrorCode.MISSING_REFRESH_TOKEN, ex.errorCode)
    }

    @Test
    @DisplayName("refreshToken이 없는 값인 경우")
    fun refresh_fail_invalid_token() {
        // given
        whenever(refreshTokenRepository.findByToken("bad-token"))
            .thenReturn(null)

        // when & then
        val ex =
            assertThrows<BusinessException> { refreshTokenService.refresh("bad-token") }

        Assertions.assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.errorCode)
    }

    @Test
    @DisplayName("폐기된 토큰인 경우(isRevoked = true)")
    fun refresh_fail_revoked_token() {
        // given
        val user = UserFixture.create()

        val refreshToken = RefreshToken.create("token", user)
        refreshToken.revoke()

        whenever(refreshTokenRepository.findByToken("token"))
            .thenReturn(refreshToken)

        // when & then
        val ex =
            assertThrows<BusinessException> { refreshTokenService.refresh("token") }

        Assertions.assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.errorCode)
    }

    @Test
    @DisplayName("만료기한이 지난 경우")
    fun refresh_fail_expired_token() {
        // given
        val refreshToken = Mockito.mock<RefreshToken>()

        whenever(refreshToken.revoked).thenReturn(false)
        whenever(refreshToken.expiresAt).thenReturn(
            LocalDateTime.now().minusDays(1)
        )

        whenever(refreshTokenRepository.findByToken("token"))
            .thenReturn(refreshToken)

        // when & then
        val ex = assertThrows<BusinessException> {
            refreshTokenService.refresh("token")
        }

        Assertions.assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.errorCode)
    }
}
