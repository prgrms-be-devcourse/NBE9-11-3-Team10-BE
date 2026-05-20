package com.team10.backend.domain.auth.unit

import com.team10.backend.domain.auth.dto.LoginRequest
import com.team10.backend.domain.auth.service.AuthService
import com.team10.backend.domain.auth.service.RefreshTokenService
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.DuplicateType
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.security.TokenProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class AuthServiceTest {
    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var passwordEncoder: PasswordEncoder

    @Mock
    lateinit var tokenProvider: TokenProvider

    @Mock
    lateinit var refreshTokenService: RefreshTokenService

    @InjectMocks
    lateinit var authService: AuthService

    @Test
    @DisplayName("회원가입 성공 - BUYER")
    fun register_success() {
        // given
        val request = UserFixture.createAuthRegisterRequest()

        whenever(userRepository.existsByEmail(request.email))
            .thenReturn(false)
        whenever(userRepository.existsByNickname(request.nickname))
            .thenReturn(false)
        whenever(passwordEncoder.encode(request.password))
            .thenReturn("encodedPassword")

        val user = User.create(request, "encodedPassword", request.role).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "createdAt", LocalDateTime.now())
            ReflectionTestUtils.setField(this, "updatedAt", LocalDateTime.now())
        }

        whenever(userRepository.save(any()))
            .thenReturn(user)

        val userCaptor = argumentCaptor<User>()

        // when
        val response = authService.register(request)

        // then
        verify(userRepository).existsByEmail(request.email)
        verify(userRepository).existsByNickname(request.nickname)
        verify(passwordEncoder).encode(request.password)
        verify(userRepository).save(userCaptor.capture())

        val savedUser = userCaptor.firstValue

        Assertions.assertEquals(request.email, savedUser.email)
        Assertions.assertEquals("encodedPassword", savedUser.password)
        Assertions.assertEquals(Role.BUYER, savedUser.role)
        assertNull(savedUser.sellerInfo)

        Assertions.assertEquals(request.email, response.email)
    }

    @Test
    @DisplayName("회원가입 성공 - SELLER")
    fun register_success_seller() {
        // given
        val request = UserFixture.createAuthRegisterRequest(
            role = Role.SELLER
        )

        whenever(userRepository.existsByEmail(request.email))
            .thenReturn(false)
        whenever(userRepository.existsByNickname(request.nickname))
            .thenReturn(false)
        whenever(passwordEncoder.encode(request.password))
            .thenReturn("encodedPassword")

        val user = User.create(request, "encodedPassword", request.role).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "createdAt", LocalDateTime.now())
            ReflectionTestUtils.setField(this, "updatedAt", LocalDateTime.now())
        }

        whenever(userRepository.save(any()))
            .thenReturn(user)

        val userCaptor = argumentCaptor<User>()

        // when
        val response = authService.register(request)

        // then
        verify(userRepository).existsByEmail(request.email)
        verify(userRepository).existsByNickname(request.nickname)
        verify(passwordEncoder).encode(request.password)
        verify(userRepository).save(userCaptor.capture())

        val savedUser = userCaptor.firstValue

        Assertions.assertEquals(Role.SELLER, savedUser.role)
        Assertions.assertNotNull(savedUser.sellerInfo)

        Assertions.assertEquals(request.email, response.email)
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    fun register_fail_duplicate_email() {
        // given
        val request = UserFixture.createAuthRegisterRequest()

        whenever(userRepository.existsByEmail(request.email))
            .thenReturn(true)

        // when & then
        val ex =
            assertThrows<BusinessException> { authService.register(request) }

        Assertions.assertEquals(ErrorCode.DUPLICATE_EMAIL, ex.errorCode)

        verify(userRepository).existsByEmail(request.email)
        verify(userRepository, never()).save(any())
        verify(passwordEncoder, never()).encode(any())
    }

    @Test
    @DisplayName("회원가입 실패 - 닉네임 중복")
    fun register_fail_duplicate_nickname() {
        // given
        val request = UserFixture.createAuthRegisterRequest()

        whenever(userRepository.existsByEmail(request.email))
            .thenReturn(false)
        whenever(userRepository.existsByNickname(request.nickname))
            .thenReturn(true)

        // when & then
        val ex =
            assertThrows<BusinessException> { authService.register(request) }

        Assertions.assertEquals(ErrorCode.DUPLICATE_NICKNAME, ex.errorCode)

        verify(userRepository, never()).save(any())
        verify(passwordEncoder, never()).encode(any())
    }

    @Test
    @DisplayName("중복 확인 - 이메일 사용 가능")
    fun checkDuplicate_email_available() {
        // given
        whenever(userRepository.existsByEmail("user@example.com"))
            .thenReturn(false)

        // when
        val response = authService.checkDuplicate(
            DuplicateType.EMAIL,
            "user@example.com"
        )

        // then
        Assertions.assertEquals(DuplicateType.EMAIL, response.type)
        Assertions.assertEquals("user@example.com", response.value)
        Assertions.assertTrue(response.available)
    }

    @Test
    @DisplayName("중복 확인 - 이메일 사용 불가")
    fun checkDuplicate_email_unavailable() {
        // given
        whenever(userRepository.existsByEmail("user@example.com"))
            .thenReturn(true)

        // when
        val response = authService.checkDuplicate(
            DuplicateType.EMAIL,
            "user@example.com"
        )

        // then
        Assertions.assertFalse(response.available)
    }

    @Test
    @DisplayName("로그인 성공")
    fun login_success() {
        // given
        val request = LoginRequest("user@example.com", "password")

        val user = UserFixture.create().apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }

        whenever(userRepository.findByEmail(request.email))
            .thenReturn(user)
        whenever(
            passwordEncoder.matches(
                request.password,
                user.password
            )
        ).thenReturn(true)

        whenever(tokenProvider.generateToken(user.id, user.role))
            .thenReturn("test-access-token")
        whenever(refreshTokenService.createRefreshToken(user))
            .thenReturn("test-refresh-token")

        // when
        val result = authService.login(request)

        // then
        Assertions.assertEquals(user.email, result.response.email)
        Assertions.assertEquals("test-access-token", result.accessToken)
        Assertions.assertEquals("test-refresh-token", result.refreshToken)
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    fun login_fail_mismatch_password() {
        // given
        val request = LoginRequest("user@example.com", "password")

        val user = UserFixture.create()

        whenever(userRepository.findByEmail(request.email))
            .thenReturn(user)
        whenever(
            passwordEncoder.matches(
                request.password,
                user.password
            )
        ).thenReturn(false)

        // when & then
        val ex = assertThrows<BusinessException> { authService.login(request) }

        Assertions.assertEquals(ErrorCode.LOGIN_FAILED, ex.errorCode)

        verify(tokenProvider, never()).generateToken(
            ArgumentMatchers.anyLong(),
            any()
        )
        verify(refreshTokenService, never()).createRefreshToken(any())
    }
}
