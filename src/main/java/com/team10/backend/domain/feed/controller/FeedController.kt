package com.team10.backend.domain.feed.controller

import com.team10.backend.domain.feed.dto.post.*
import com.team10.backend.domain.feed.service.FeedPostService
import com.team10.backend.global.dto.ApiResponse
import com.team10.backend.global.security.CustomUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/stores")
@Tag(name = "Feed", description = "피드 관리 API")
class FeedController(
    private val feedPostService: FeedPostService
) {

    @GetMapping("/{sellerId}/feeds")
    @Operation(summary = "피드 조회", description = "피드 전체 조회 합니다.")
    fun getStoreFeeds(
        @PathVariable sellerId: Long,
        @AuthenticationPrincipal currentUser: CustomUserPrincipal?
    ): ApiResponse<FeedListResponseDto> {
        val currentUserId = currentUser?.userId

        val response = feedPostService.getFeedsList(sellerId, currentUserId)

        return ApiResponse.ok(response)
    }

    @PostMapping("me/feeds")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "피드 생성", description = "판매자가 피드를 생성 합니다.")
    fun createFeed(
        @RequestBody @Valid requestDto: CreateFeedRequestDto,
        @AuthenticationPrincipal seller: CustomUserPrincipal
    ): ApiResponse<FeedResponseDto> {
        val responseDto = feedPostService.createFeed(requestDto, seller.userId)
        return ApiResponse.ok(responseDto)
    }

    @PatchMapping("me/feeds/{feedId}")
    @Operation(summary = "피드 수정", description = "판매자가 피드를 수정합니다.")
    fun updateFeed(
        @PathVariable feedId: Long,
        @RequestBody @Valid requestDto: UpdateFeedRequestDto,
        @AuthenticationPrincipal seller: CustomUserPrincipal
    ): ApiResponse<UpdateFeedResponseDto> {
        val responseDto =
            feedPostService.updateFeed(feedId, requestDto, seller.userId)

        return ApiResponse.ok(responseDto)
    }

    @DeleteMapping("me/feeds/{feedId}")
    @Operation(summary = "피드 삭제", description = "판매자가 피드를 삭제합니다.")
    fun deleteFeed(
        @PathVariable feedId: Long,
        @AuthenticationPrincipal seller: CustomUserPrincipal
    ): ApiResponse<Void> {
        feedPostService.deleteFeed(feedId, seller.userId)

        return ApiResponse.ok()
    }

    @PostMapping("me/feeds/{feedId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "피드 좋아요", description = "사용자가 피드에 좋아요를 누릅니다.")
    fun toggleFeedLike(
        @PathVariable feedId: Long,
        @AuthenticationPrincipal currentUser: CustomUserPrincipal
    ): ApiResponse<FeedLikeToggleResponseDto> {
        val responseDto =
            feedPostService.toggleFeedLike(feedId, currentUser.userId)

        return ApiResponse.ok(responseDto)
    }
}
