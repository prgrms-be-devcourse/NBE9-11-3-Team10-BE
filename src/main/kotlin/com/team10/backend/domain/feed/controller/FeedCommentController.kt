package com.team10.backend.domain.feed.controller

import com.team10.backend.domain.feed.dto.comment.*
import com.team10.backend.domain.feed.service.FeedCommentService
import com.team10.backend.global.dto.ApiResponse
import com.team10.backend.global.security.CustomUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/stores/{sellerId}/feeds/{feedId}/comments")
@Tag(name = "Feed_Comment", description = "피드 댓글 관리 API")
class FeedCommentController(
    private val feedCommentService: FeedCommentService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "댓글 생성", description = "피드에 댓글을 생성 합니다.")
    fun createComment(
        @PathVariable sellerId: Long,
        @PathVariable feedId: Long,
        @RequestBody @Valid requestDto: CreateCommentRequestDto,
        @AuthenticationPrincipal currentUser: CustomUserPrincipal
    ): ApiResponse<CommentResponseDto> {
        return ApiResponse.ok(
            feedCommentService.createComment(
                sellerId,
                feedId,
                requestDto,
                currentUser.userId
            )
        )
    }

    @GetMapping
    @Operation(summary = "댓글 조회", description = "피드의 댓글을 조회 합니다.")
    fun getComments(
        @PathVariable sellerId: Long,
        @PathVariable feedId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "createdAt,desc") sort: String,
        @AuthenticationPrincipal currentUser: CustomUserPrincipal?
    ): ApiResponse<CommentListResponseDto> {
        val currentUserId = currentUser?.userId

        return ApiResponse.ok(
            feedCommentService.getComments(
                sellerId,
                feedId,
                page,
                size,
                sort,
                currentUserId
            )
        )
    }

    @PatchMapping("/{commentId}")
    @Operation(summary = "댓글 수정", description = "피드의 댓글을 수정합니다.")
    fun updateComment(
        @PathVariable sellerId: Long,
        @PathVariable feedId: Long,
        @PathVariable commentId: Long,
        @RequestBody @Valid requestDto: UpdateCommentRequestDto,
        @AuthenticationPrincipal currentUser: CustomUserPrincipal
    ): ApiResponse<CommentResponseDto> {
        return ApiResponse.ok(
            feedCommentService.updateComment(
                sellerId,
                feedId,
                commentId,
                requestDto,
                currentUser.userId
            )
        )
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제", description = "피드의 댓글을 삭제 합니다.")
    fun deleteComment(
        @PathVariable sellerId: Long,
        @PathVariable feedId: Long,
        @PathVariable commentId: Long,
        @AuthenticationPrincipal currentUser: CustomUserPrincipal
    ): ApiResponse<Void> {
        feedCommentService.deleteComment(sellerId, feedId, commentId, currentUser.userId)
        return ApiResponse.ok()
    }


    @PostMapping("/{commentId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "댓글 좋아요", description = "사용자가 피드 댓글에 좋아요를 누릅니다.")
    fun toggleCommentLike(
        @PathVariable sellerId: Long,
        @PathVariable feedId: Long,
        @PathVariable commentId: Long,
        @AuthenticationPrincipal currentUser: CustomUserPrincipal
    ): ApiResponse<CommentLikeToggleResponseDto> {
        return ApiResponse.ok(
            feedCommentService.toggleCommentLike(
                sellerId,
                feedId,
                commentId,
                currentUser.userId
            )
        )
    }
}
