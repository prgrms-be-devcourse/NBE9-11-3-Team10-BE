package com.team10.backend.domain.auth.dto

data class RefreshResult(
    val accessToken: String,
    val refreshToken: String
)
