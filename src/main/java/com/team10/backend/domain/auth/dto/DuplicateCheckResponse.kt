package com.team10.backend.domain.auth.dto

import com.team10.backend.domain.user.enums.DuplicateType

data class DuplicateCheckResponse(
    @JvmField val type: DuplicateType,
    @JvmField val value: String,
    @JvmField val available: Boolean
)
