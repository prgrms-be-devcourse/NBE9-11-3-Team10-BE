package com.team10.backend.domain.auth.dto

data class LoginResult(
    @JvmField val response: LoginResponse,
    @JvmField val accessToken: String,
    @JvmField val refreshToken: String
)
