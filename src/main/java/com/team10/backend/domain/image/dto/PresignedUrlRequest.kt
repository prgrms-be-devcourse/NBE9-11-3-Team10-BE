package com.team10.backend.domain.image.dto

import jakarta.validation.constraints.NotBlank

data class PresignedUrlRequest(
    @JvmField val fileName: @NotBlank(message = "파일명은 필수입니다.") String,

    @JvmField val contentType: @NotBlank(message = "파일 타입은 필수입니다.") String,

    @JvmField val directory: String?
)
