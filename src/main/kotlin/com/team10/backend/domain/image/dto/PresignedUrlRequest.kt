package com.team10.backend.domain.image.dto

import jakarta.validation.constraints.NotBlank

data class PresignedUrlRequest(
    @field:NotBlank(message = "파일명은 필수입니다.")
    val fileName: String,

    @field:NotBlank(message = "파일 타입은 필수입니다.")
    val contentType: String,

    val directory: String?
)
