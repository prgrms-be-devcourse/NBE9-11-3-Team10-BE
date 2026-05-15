package com.team10.backend.domain.user.dto

import com.team10.backend.domain.user.entity.User

data class SellerPublicResponse(
    val imageUrl: String?,
    val name: String,
    val nickname: String,
    val bio: String
) {
    companion object {
        @JvmStatic
        fun from(user: User): SellerPublicResponse {
            return SellerPublicResponse(
                user.imageUrl,
                user.name,
                user.nickname,
                user.sellerInfo.bio
            )
        }
    }
}
