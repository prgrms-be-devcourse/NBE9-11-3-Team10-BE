package com.team10.backend.domain.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class LoginRequest(
        @JvmField
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
         val email: String,

        @JvmField
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$",
            message = "비밀번호는 8~20자이며, 영문과 숫자를 포함해야 합니다."
        )
        val password: String
)
