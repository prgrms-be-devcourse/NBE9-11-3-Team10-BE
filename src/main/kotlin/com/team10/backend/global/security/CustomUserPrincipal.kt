package com.team10.backend.global.security

import com.team10.backend.domain.user.enums.Role

data class CustomUserPrincipal(
    val userId: Long,
    val role: Role
)
