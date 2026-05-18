package com.team10.backend.domain.feed.dto.comment

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCommentRequestDto @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @field:NotBlank(message = "댓글 내용은 필수입니다.")
    @field:Size(
        min = 1,
        max = 500,
        message = "댓글은 1자 이상 500자 이하로 입력해주세요.")
    @JvmField
    @param:JsonProperty("content")
    val content: String
)
