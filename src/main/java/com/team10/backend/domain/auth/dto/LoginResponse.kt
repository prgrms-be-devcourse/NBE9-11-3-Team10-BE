package com.team10.backend.domain.auth.dto

import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role

data class LoginResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val role: Role
) {
    companion object {
        fun from(user: User) = LoginResponse(
                user.id,
                user.email,
                user.nickname,
                user.role
        )
    }
}
