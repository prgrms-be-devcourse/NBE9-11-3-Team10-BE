package com.team10.backend.domain.auth.dto

import com.team10.backend.domain.user.entity.User
import java.time.LocalDateTime

data class AuthRegisterResponse(
    val id: Long,
    val email: String,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(user: User) = AuthRegisterResponse(
                user.id,
                user.email,
                user.createdAt
        )
    }
}
