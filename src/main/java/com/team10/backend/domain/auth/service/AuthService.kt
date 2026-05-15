package com.team10.backend.domain.auth.service

import com.team10.backend.domain.auth.dto.AuthRegisterRequest
import com.team10.backend.domain.auth.dto.AuthRegisterResponse
import com.team10.backend.domain.auth.dto.DuplicateCheckResponse
import com.team10.backend.domain.auth.dto.LoginRequest
import com.team10.backend.domain.auth.dto.LoginResponse
import com.team10.backend.domain.auth.dto.LoginResult
import com.team10.backend.domain.user.entity.SellerInfo
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.DuplicateType
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.security.TokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: TokenProvider,
    private val refreshTokenService: RefreshTokenService
) {
    @Transactional
    fun register(request: AuthRegisterRequest): AuthRegisterResponse {
        validateDuplicateUser(request)

        val encodedPassword = passwordEncoder.encode(request.password)
        val role = request.role
        val user = User.create(request, encodedPassword, role)

        if (role == Role.SELLER) {
            user.attachSellerInfo(SellerInfo())
        }

        return AuthRegisterResponse.from(userRepository.save(user))
    }

    private fun validateDuplicateUser(request: AuthRegisterRequest) {
        if (userRepository.existsByEmail(request.email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }
        if (userRepository.existsByNickname(request.nickname)) {
            throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
        }
    }

    @Transactional(readOnly = true)
    fun checkDuplicate(type: DuplicateType, value: String): DuplicateCheckResponse {
        val value = value.trim().lowercase()

        if (value.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        val available = when (type) {
            DuplicateType.EMAIL -> !userRepository.existsByEmail(value)
            DuplicateType.NICKNAME -> !userRepository.existsByNickname(value)
        }

        return DuplicateCheckResponse(type, value, available)
    }

    fun login(request: LoginRequest): LoginResult {
        val user = authenticate(request)

        val accessToken = tokenProvider.generateToken(user.id, user.role)
        val refreshToken = refreshTokenService.createRefreshToken(user)
        val response = LoginResponse.from(user)

        return LoginResult(response, accessToken, refreshToken)
    }

    private fun authenticate(request: LoginRequest): User {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { BusinessException(ErrorCode.LOGIN_FAILED) }

        if (!passwordEncoder.matches(request.password,user.password)) {
            throw BusinessException(ErrorCode.LOGIN_FAILED)
        }

        return user
    }
}
