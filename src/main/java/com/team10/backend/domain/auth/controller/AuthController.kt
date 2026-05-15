package com.team10.backend.domain.auth.controller

import com.team10.backend.domain.auth.dto.AuthRegisterRequest
import com.team10.backend.domain.auth.dto.AuthRegisterResponse
import com.team10.backend.domain.auth.dto.DuplicateCheckResponse
import com.team10.backend.domain.auth.dto.LoginRequest
import com.team10.backend.domain.auth.dto.LoginResponse
import com.team10.backend.domain.auth.service.AuthService
import com.team10.backend.domain.auth.service.RefreshTokenService
import com.team10.backend.domain.user.enums.DuplicateType
import com.team10.backend.global.constant.CookieConstants
import com.team10.backend.global.dto.ApiResponse
import com.team10.backend.global.util.CookieUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@Tag(name = "Auth", description = "회원가입 및 인증 API")
class AuthController(
    private val authService: AuthService,
    private val refreshTokenService: RefreshTokenService,
    private val cookieUtil: CookieUtil
) {
    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "사용자 회원가입을 진행합니다.")
    fun register(
        @RequestBody @Valid request: AuthRegisterRequest
    ): ApiResponse<AuthRegisterResponse> =
        ApiResponse.ok(authService.register(request))

    @GetMapping("/check-duplicate")
    @Operation(
        summary = "중복 확인",
        description = "type(email, nickname)에 따라 값의 중복 여부를 확인합니다."
    )
    fun checkDuplicate(
        @RequestParam @NotNull type: DuplicateType,
        @RequestParam @NotBlank value: String
    ): ApiResponse<DuplicateCheckResponse> =
        ApiResponse.ok(authService.checkDuplicate(type, value))

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 로그인을 진행합니다.")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        response: HttpServletResponse
    ): ApiResponse<LoginResponse> {
        val result = authService.login(request).also {
            cookieUtil.addCookie(
                response,
                CookieConstants.ACCESS_TOKEN,
                it.accessToken
            )
            cookieUtil.addCookie(
                response,
                CookieConstants.REFRESH_TOKEN,
                it.refreshToken
            )
        }

        return ApiResponse.ok(result.response)
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "401에러 시 토큰을 재발급 받습니다.")
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ApiResponse<Void> {
        val refreshToken =
            cookieUtil.getCookieValue(request, CookieConstants.REFRESH_TOKEN)

        val result = refreshTokenService.refresh(refreshToken)
        cookieUtil.addCookie(
            response,
            CookieConstants.ACCESS_TOKEN,
            result.accessToken
        )
        cookieUtil.addCookie(
            response,
            CookieConstants.REFRESH_TOKEN,
            result.refreshToken
        )

        return ApiResponse.ok()
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자 로그아웃을 진행합니다.")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ApiResponse<Void> {
        val refreshToken =
            cookieUtil.getCookieValue(request, CookieConstants.REFRESH_TOKEN)
        refreshTokenService.revoke(refreshToken)

        cookieUtil.deleteCookie(response, CookieConstants.ACCESS_TOKEN)
        cookieUtil.deleteCookie(response, CookieConstants.REFRESH_TOKEN)

        return ApiResponse.ok()
    }
}
