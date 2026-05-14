package com.team10.backend.domain.auth.dto

import com.team10.backend.domain.user.entity.User
import java.time.LocalDateTime

data class AuthRegisterResponse(
    val id: Long,
    @JvmField val email: String,
    val createdAt: LocalDateTime
) {
    companion object {
        @JvmStatic
        fun from(user: User) = AuthRegisterResponse(
                user.id,
                user.email,
                user.createdAt
        )
    }
}
