package com.team10.backend.domain.feed.dto.post

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateFeedRequestDto(
        @JvmField val content: @NotBlank(message = "피드 내용은 필수입니다.") @Size(
        min = 1,
        max = 2000,
        message = "내용은 1자 이상 2,000자 이하로 입력해주세요."
    ) String?,

        @JvmField val imageUrl: String?
)
