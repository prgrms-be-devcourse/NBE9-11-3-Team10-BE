package com.team10.backend.domain.image.controller

import com.team10.backend.domain.image.dto.PresignedUrlRequest
import com.team10.backend.domain.image.dto.PresignedUrlResponse
import com.team10.backend.domain.image.service.ImageUploadService
import com.team10.backend.global.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "이미지", description = "이미지 업로드 API")
class ImageUploadController(private val imageUploadService: ImageUploadService) {
    @PostMapping("/presigned-url")
    @Operation(summary = "Presigned URL 발급", description = "프론트가 S3에 직접 업로드할 수 있는 URL과 저장용 이미지 URL을 반환합니다.")
    fun createPresignedUrl(
        @RequestBody @Valid request: @Valid PresignedUrlRequest
    ): ApiResponse<PresignedUrlResponse> {
        return ApiResponse.ok(imageUploadService.createPresignedUrl(request))
    }
}
