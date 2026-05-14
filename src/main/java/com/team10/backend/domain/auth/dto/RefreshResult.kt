package com.team10.backend.domain.auth.dto

data class RefreshResult(
    @JvmField val accessToken: String,
    @JvmField val refreshToken: String
)
