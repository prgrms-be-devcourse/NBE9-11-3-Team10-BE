package com.team10.backend.domain.auth.service

import com.team10.backend.domain.auth.dto.RefreshResult
import com.team10.backend.domain.auth.entity.RefreshToken
import com.team10.backend.domain.auth.repository.RefreshTokenRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.security.TokenProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenProvider: TokenProvider
) {
    @Transactional
    fun createRefreshToken(user: User): String {
        val token = UUID.randomUUID().toString()
        refreshTokenRepository.save(RefreshToken.create(token, user))

        return token
    }

    @Transactional
    fun refresh(token: String?): RefreshResult {
        val oldRefreshToken = validateRefreshToken(token)
        oldRefreshToken.revoke()

        val user = oldRefreshToken.user

        val newAccessToken = tokenProvider.generateToken(user.id, user.role)
        val newRefreshToken = createRefreshToken(user)

        return RefreshResult(newAccessToken, newRefreshToken)
    }

    private fun validateRefreshToken(token: String?): RefreshToken {
        if (token.isNullOrBlank()) {
            throw BusinessException(ErrorCode.MISSING_REFRESH_TOKEN)
        }

        val refreshToken = refreshTokenRepository.findByToken(token)
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)

        // 로그아웃 상태 || 이미 사용한 토큰 || 만료일이 지난 토큰
        if (refreshToken.revoked || refreshToken.expiresAt.isBefore(LocalDateTime.now())) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        return refreshToken
    }

    @Transactional
    fun revoke(token: String?) {
        if (token.isNullOrBlank()) {
            return
        }
        refreshTokenRepository.findByToken(token)?.revoke()
    }
}
