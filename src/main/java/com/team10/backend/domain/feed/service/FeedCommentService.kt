package com.team10.backend.domain.feed.service

import com.team10.backend.domain.feed.dto.comment.*
import com.team10.backend.domain.feed.dto.comment.CommentResponseDto.Companion.from
import com.team10.backend.domain.feed.entity.FeedComment
import com.team10.backend.domain.feed.entity.FeedCommentLike
import com.team10.backend.domain.feed.entity.FeedPost
import com.team10.backend.domain.feed.repository.FeedCommentLikeRepository
import com.team10.backend.domain.feed.repository.FeedCommentRepository
import com.team10.backend.domain.feed.repository.FeedPostRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.max
import kotlin.math.min

@Service
@Transactional(readOnly = true)
class FeedCommentService(
    private val feedPostRepository: FeedPostRepository,
    private val feedCommentRepository: FeedCommentRepository,
    private val feedCommentLikeRepository: FeedCommentLikeRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun createComment(
        sellerId: Long,
        feedId: Long,
        requestDto: CreateCommentRequestDto,
        currentUserId: Long
    ): CommentResponseDto {
        val currentUser = getUser(currentUserId)

        val feedPost = getFeedPost(sellerId, feedId)
        val feedComment = feedCommentRepository.save<FeedComment>(
            FeedComment(feedPost, currentUser, requestDto.content)
        )
        feedPost.increaseCommentCount()

        return from(feedComment, false, currentUser)
    }

    fun getComments(
        sellerId: Long,
        feedId: Long,
        page: Int,
        size: Int,
        sort: String,
        currentUserId: Long?
    ): CommentListResponseDto {
        getFeedPost(sellerId, feedId)
        val currentUser = getNullableUser(currentUserId)

        val pageable = createPageable(page, size, sort)
        val commentPage = feedCommentRepository.findAllByFeedPostId(feedId, pageable)
        val comments = commentPage.content.map { comment -> toCommentResponse(comment, currentUser) }

        return CommentListResponseDto(comments, toPaginationDto(commentPage))
    }


    @Transactional
    fun updateComment(
        sellerId: Long,
        feedId: Long,
        commentId: Long,
        requestDto: UpdateCommentRequestDto,
        currentUserId: Long
    ): CommentResponseDto {
        val currentUser = getUser(currentUserId)

        getFeedPost(sellerId, feedId)

        val feedComment = getComment(commentId, feedId)

        if (feedComment.writer.id != currentUser.id) {
            throw BusinessException(ErrorCode.COMMENT_ACCESS_DENIED)
        }

        feedComment.updateContent(requestDto.content)


        val liked = feedCommentLikeRepository.existsByFeedCommentIdAndUserId(
            commentId,
            currentUser.getId()
        )

        return from(feedComment, liked, currentUser)
    }

    @Transactional
    fun deleteComment(sellerId: Long, feedId: Long, commentId: Long, currentUserId: Long) {
        val currentUser = getUser(currentUserId)

        val feedPost = getFeedPost(sellerId, feedId)

        val feedComment = getComment(commentId, feedId)

        if (!canDeleteComment(feedComment, feedPost, currentUser)) {
            throw BusinessException(ErrorCode.COMMENT_ACCESS_DENIED)
        }

        feedCommentRepository.delete(feedComment)
        feedPost.decreaseCommentCount()
    }

    @Transactional
    fun toggleCommentLike(
        sellerId: Long,
        feedId: Long,
        commentId: Long,
        currentUserId: Long
    ): CommentLikeToggleResponseDto {
        val currentUser = getUser(currentUserId)

        getFeedPost(sellerId, feedId)

        val feedComment = getComment(commentId, feedId)

        val liked = toggleLike(feedComment, currentUser)

        return CommentLikeToggleResponseDto(liked, feedComment.likeCount)
    }

    private fun createPageable(page: Int, size: Int, sort: String): Pageable {
        val safePage = max(page, 0)
        val safeSize = min(max(size, 1), 50)
        val safeSort = parseSort(sort)

        return PageRequest.of(safePage, safeSize, safeSort)
    }

    private fun parseSort(sort: String): Sort {
        val sortParts = sort.split(",")
        val property = if (sortParts.firstOrNull() == "createdAt")
            sortParts[0]
        else
            "createdAt"
        val direction = if (sortParts.getOrNull(1).equals("asc", ignoreCase = true))
            Sort.Direction.ASC
        else
            Sort.Direction.DESC

        return Sort.by(direction, property)
    }

    private fun toPaginationDto(commentPage: Page<FeedComment>): PaginationResponseDto {
        return PaginationResponseDto(
            commentPage.getNumber(),
            commentPage.getTotalPages(),
            commentPage.getTotalElements()
        )
    }

    private fun getFeedPost(sellerId: Long, feedId: Long): FeedPost {
        val feedPost = feedPostRepository.findById(feedId)
            .orElseThrow { BusinessException(ErrorCode.FEED_NOT_FOUND) }

        if (feedPost.user.id != sellerId) {
            throw BusinessException(ErrorCode.FEED_NOT_FOUND)
        }

        return feedPost
    }

    private fun getUser(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
    }

    private fun getNullableUser(userId: Long?): User? {
        return if (userId == null) null else getUser(userId)
    }

    private fun toCommentResponse(comment: FeedComment, currentUser: User?): CommentResponseDto {
        val liked = currentUser != null
                && feedCommentLikeRepository.existsByFeedCommentIdAndUserId(
                    comment.getId(),
                    currentUser.getId()
                )
        return from(comment, liked, currentUser)
    }

    private fun canDeleteComment(feedComment: FeedComment, feedPost: FeedPost, currentUser: User): Boolean {
        val isCommentWriter = feedComment.writer.id == currentUser.id
        val isFeedOwner = feedPost.user.id == currentUser.id
        return isCommentWriter || isFeedOwner
    }

    private fun toggleLike(feedComment: FeedComment, currentUser: User): Boolean {
        val existingLike = feedCommentLikeRepository.findByFeedCommentIdAndUserId(
            feedComment.getId(),
            currentUser.getId()
        )

        if (existingLike != null) {
            feedCommentLikeRepository.delete(existingLike)
            feedComment.decreaseLikeCount()
            return false
        }

        feedCommentLikeRepository.save(FeedCommentLike(feedComment, currentUser))
        feedComment.increaseLikeCount()
        return true
    }

    private fun getComment(commentId: Long, feedId: Long): FeedComment {
        return feedCommentRepository.findByIdAndFeedPostId(commentId, feedId)
            ?: throw BusinessException(ErrorCode.COMMENT_NOT_FOUND)
    }
}
