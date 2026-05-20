package com.team10.backend.domain.user.dto

import com.team10.backend.domain.user.entity.User

data class UserResponse(
    val id: Long,
    val imageUrl: String?,
    val email: String,
    val name: String,
    val nickname: String,
    val phoneNumber: String,
    val address: String
) {
    companion object {
        fun from(user: User) = UserResponse(
                user.id,
                user.imageUrl,
                user.email,
                user.name,
                user.nickname,
                user.phoneNumber,
                user.address
        )
    }
}
