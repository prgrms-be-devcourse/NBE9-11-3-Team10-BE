package com.team10.backend.domain.user.controller

import com.team10.backend.domain.user.dto.ProfileImageUpdateRequest
import com.team10.backend.domain.user.dto.SellerPublicResponse
import com.team10.backend.domain.user.dto.SellerResponse
import com.team10.backend.domain.user.dto.SellerUpdateRequest
import com.team10.backend.domain.user.dto.UserResponse
import com.team10.backend.domain.user.dto.UserUpdateRequest
import com.team10.backend.domain.user.service.UserService
import com.team10.backend.global.dto.ApiResponse
import com.team10.backend.global.security.CustomUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "User/Seller Profile", description = "사용자 및 판매자 프로필 API")
class UserController(private val userService: UserService) {
    @GetMapping("/users/me")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(
        summary = "내 사용자 프로필 조회",
        description = "로그인한 BUYER 본인의 프로필 정보를 조회합니다."
    )
    fun getUserProfile(
        @AuthenticationPrincipal principal: CustomUserPrincipal
    ): ApiResponse<UserResponse> =
        ApiResponse.ok(userService.getUserProfile(principal.userId))

    @GetMapping("/sellers/me")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(
        summary = "내 판매자 프로필 조회",
        description = "로그인한 SELLER 본인의 프로필 정보를 조회합니다."
    )
    fun getSellerProfile(
        @AuthenticationPrincipal principal: CustomUserPrincipal
    ): ApiResponse<SellerResponse> =
        ApiResponse.ok(userService.getSellerProfile(principal.userId))

    @GetMapping("/sellers/{id}")
    @Operation(
        summary = "판매자 공개 프로필 조회",
        description = "특정 판매자의 공개 프로필 정보를 조회합니다."
    )
    fun getSellerPublicProfile(
        @PathVariable id: Long
    ): ApiResponse<SellerPublicResponse> =
        ApiResponse.ok(userService.getSellerPublicProfile(id))

    @PutMapping("/users/me")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(
        summary = "내 사용자 프로필 수정",
        description = "로그인한 BUYER 본인의 프로필 정보를 수정합니다."
    )
    fun updateMyUserProfile(
        @AuthenticationPrincipal principal: CustomUserPrincipal,
        @Valid @RequestBody request: UserUpdateRequest
    ): ApiResponse<UserResponse> =
        ApiResponse.ok(userService.updateMyUserProfile(principal.userId, request))

    @PutMapping("/sellers/me")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(
        summary = "내 판매자 프로필 수정",
        description = "로그인한 SELLER 본인의 프로필 정보를 수정합니다."
    )
    fun updateMySellerProfile(
        @AuthenticationPrincipal principal: CustomUserPrincipal,
        @Valid @RequestBody request: SellerUpdateRequest
    ): ApiResponse<SellerResponse> =
        ApiResponse.ok(userService.updateMySellerProfile(principal.userId, request))

    @PutMapping("/me/profile-image")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "내 프로필 이미지 수정",
        description = "로그인한 본인의 프로필 이미지를 수정합니다."
    )
    fun updateMyProfileImage(
        @AuthenticationPrincipal principal: CustomUserPrincipal,
        @Valid @RequestBody request: ProfileImageUpdateRequest
    ): ApiResponse<UserResponse> =
        ApiResponse.ok(userService.updateMyProfileImage(principal.userId, request))

    @DeleteMapping("/me/profile-image")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "내 프로필 이미지 삭제",
        description = "로그인한 본인의 프로필 이미지를 삭제합니다."
    )
    fun deleteMyProfileImage(
        @AuthenticationPrincipal principal: CustomUserPrincipal
    ): ApiResponse<UserResponse> =
        ApiResponse.ok(userService.deleteMyProfileImage(principal.userId))
}
