package com.team10.backend.domain.user.dto

import com.team10.backend.domain.user.entity.User
import java.time.LocalDateTime

data class SellerResponse(
    val id: Long,
    val imageUrl: String?,
    val email: String,
    val name: String,
    val nickname: String,
    val phoneNumber: String,
    val address: String,
    val bio: String?,
    val businessNumber: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(user: User): SellerResponse {
            val sellerInfo = requireNotNull(user.sellerInfo)

            return SellerResponse(
                user.id,
                user.imageUrl,
                user.email,
                user.name,
                user.nickname,
                user.phoneNumber,
                user.address,
                sellerInfo.bio,
                sellerInfo.businessNumber,
                user.createdAt,
                user.updatedAt
            )
        }
    }
}
