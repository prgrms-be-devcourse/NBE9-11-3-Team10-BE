package com.team10.backend.domain.image.dto

data class PresignedUrlResponse(
    @JvmField val uploadUrl: String,
    @JvmField val imageUrl: String
)
