package com.team10.backend.domain.auth.repository

import com.team10.backend.domain.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    // TODO: Optional 제거
    fun findByToken(token: String): Optional<RefreshToken>
}
