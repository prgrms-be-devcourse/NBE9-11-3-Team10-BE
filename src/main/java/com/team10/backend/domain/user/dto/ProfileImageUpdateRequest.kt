package com.team10.backend.domain.user.dto

import org.hibernate.validator.constraints.URL

data class ProfileImageUpdateRequest(
        @URL(message = "올바른 이미지 URL 형식이어야 합니다.")
        @JvmField val imageUrl:  String?
)
