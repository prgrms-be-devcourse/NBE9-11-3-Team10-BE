package com.team10.backend.domain.auth.dto

import com.team10.backend.domain.user.enums.DuplicateType

data class DuplicateCheckResponse(
    val type: DuplicateType,
    val value: String,
    val available: Boolean
)
