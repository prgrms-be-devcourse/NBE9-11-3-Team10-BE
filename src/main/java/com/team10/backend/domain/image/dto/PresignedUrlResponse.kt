package com.team10.backend.domain.image.dto

data class PresignedUrlResponse(
    val uploadUrl: String,
    val imageUrl: String
)
