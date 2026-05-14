package com.team10.backend.domain.user.dto

import com.team10.backend.domain.user.entity.User
import java.time.LocalDateTime

data class SellerResponse(
    val id: Long,
    val imageUrl: String?,
    val email: String,
    @JvmField val name: String,
    @JvmField val nickname: String,
    val phoneNumber: String,
    @JvmField val address: String,
    @JvmField val bio: String?,
    @JvmField val businessNumber: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        @JvmStatic
        fun from(user: User) = SellerResponse(
                user.id,
                user.imageUrl,
                user.email,
                user.name,
                user.nickname,
                user.phoneNumber,
                user.address,
                user.sellerInfo.bio,
                user.sellerInfo.businessNumber,
                user.createdAt,
                user.updatedAt
        )
    }
}
