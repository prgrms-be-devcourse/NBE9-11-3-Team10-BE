package com.team10.backend.domain.auth.dto

data class LoginResult(
    val response: LoginResponse,
    val accessToken: String,
    val refreshToken: String
)
