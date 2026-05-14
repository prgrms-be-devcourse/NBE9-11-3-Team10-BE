package com.team10.backend.domain.user.dto

import com.team10.backend.domain.user.entity.User

data class UserResponse(
    val id: Long,
    @JvmField val imageUrl: String?,
    val email: String,
    @JvmField val name: String,
    @JvmField val nickname: String,
    @JvmField val phoneNumber: String,
    @JvmField val address: String
) {
    companion object {
        @JvmStatic
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
