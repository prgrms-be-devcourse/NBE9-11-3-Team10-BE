package com.team10.backend.domain.feed.service

import com.team10.backend.domain.feed.dto.post.CreateFeedRequestDto
import com.team10.backend.domain.feed.dto.post.FeedDto
import com.team10.backend.domain.feed.dto.post.FeedDto.Companion.from
import com.team10.backend.domain.feed.dto.post.FeedLikeToggleResponseDto
import com.team10.backend.domain.feed.dto.post.FeedListResponseDto
import com.team10.backend.domain.feed.dto.post.FeedResponseDto
import com.team10.backend.domain.feed.dto.post.UpdateFeedRequestDto
import com.team10.backend.domain.feed.dto.post.UpdateFeedResponseDto
import com.team10.backend.domain.feed.entity.FeedLike
import com.team10.backend.domain.feed.entity.FeedPost
import com.team10.backend.domain.feed.repository.FeedLikeRepository
import com.team10.backend.domain.feed.repository.FeedPostRepository
import com.team10.backend.domain.image.service.ImageUploadService
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class FeedPostService(
    private val feedPostRepository: FeedPostRepository,
    private val feedLikeRepository: FeedLikeRepository,
    private val userRepository: UserRepository,
    private val imageUploadService: ImageUploadService
) {
    // 비로그인 조회도 허용하되, 로그인 유저라면 좋아요 여부를 함께 계산한다.
    fun getFeedsList(sellerId: Long, currentUserId: Long?): FeedListResponseDto {
        val feedPosts: List<FeedPost> =
            feedPostRepository.findAllByUserIdOrderByCreatedAtDesc(sellerId)
        if (feedPosts.isEmpty()) {
            throw BusinessException(ErrorCode.FEED_NOT_FOUND)
        }

        val currentUser = getNullableUser(currentUserId)

        val feedDtos = feedPosts.map { feed -> toFeedDto(feed, currentUser) }

        return FeedListResponseDto(feedDtos)
    }

    @Transactional // 피드는 판매자만 생성할 수 있다.
    fun createFeed(requestDto: CreateFeedRequestDto, currentUserId: Long): FeedResponseDto {
        val currentUser = getUser(currentUserId)
        validateSeller(currentUser)

        val feedPost = FeedPost(
            requestDto.imageUrl,
            requestDto.content,
            currentUser
        )

        val savedFeed = feedPostRepository.save(feedPost)

        return FeedResponseDto.from(savedFeed)
    }

    @Transactional // 작성자 본인의 피드만 수정할 수 있다.
    fun updateFeed(
        feedId: Long,
        requestDto: UpdateFeedRequestDto,
        currentUserId: Long
    ): UpdateFeedResponseDto {
        val feedPost = getAuthorizedFeedPost(currentUserId, feedId)

        val oldImageUrl = feedPost.imageUrl

        val newImageUrl = requestDto.imageUrl

        if (oldImageUrl != newImageUrl) {
            imageUploadService.deleteIfManaged(oldImageUrl)
        }

        feedPost.update(newImageUrl, requestDto.content)

        return UpdateFeedResponseDto.from(feedPost)
    }

    @Transactional
    fun toggleFeedLike(feedId: Long, currentUserId: Long): FeedLikeToggleResponseDto {
        val currentUser = getUser(currentUserId)
        val feedPost = getFeedPost(feedId)

        val liked = toggleLike(feedPost, currentUser)

        return FeedLikeToggleResponseDto(liked, feedPost.likeCount)
    }

    @Transactional // 작성자 본인의 피드만 삭제할 수 있다.
    fun deleteFeed(feedId: Long, currentUserId: Long) {
        val feedPost = getAuthorizedFeedPost(currentUserId, feedId)

        imageUploadService.deleteIfManaged(feedPost.imageUrl)
        feedPostRepository.delete(feedPost)
    }

    // 피드를 조회하고 없으면 예외를 던진다.
    private fun getFeedPost(feedId: Long): FeedPost {
        return feedPostRepository.findById(feedId)
            .orElseThrow{ BusinessException(ErrorCode.FEED_NOT_FOUND) }
    }

    // 피드 조회와 작성자 권한 검증을 함께 수행한다.
    private fun getAuthorizedFeedPost(userId: Long, feedId: Long): FeedPost {
        val feedPost = getFeedPost(feedId)

        if (feedPost.user.id != userId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        return feedPost
    }

    // 인증 사용자 ID로 도메인 유저를 조회한다.
    private fun getUser(userId: Long): User {

        return userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
    }

    // 선택 로그인 API에서만 사용한다.
    private fun getNullableUser(userId: Long?): User? {
        return if (userId == null) null else getUser(userId)
    }

    // 피드 생성은 판매자 권한이 필요하다.
    private fun validateSeller(user: User) {

        if (user.role != Role.SELLER) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }
    }

    // 로그인 사용자가 있으면 좋아요 여부를 포함해 FeedDto로 변환한다.
    private fun toFeedDto(feed: FeedPost, currentUser: User?): FeedDto {
        val liked = isLiked(feed, currentUser)
        return from(feed, liked)
    }

    // 비로그인 사용자는 false, 로그인 사용자는 좋아요 여부를 계산한다.
    private fun isLiked(feed: FeedPost, currentUser: User?): Boolean {
        if (currentUser == null) {
            return false
        }

        return feed.feedLikes.any { like ->
            like.user.id == currentUser.id
        }
    }

    // 좋아요가 이미 있으면 취소하고, 없으면 새로 생성한다.
    private fun toggleLike(feedPost: FeedPost, currentUser: User): Boolean {
        val existingLike = feedLikeRepository.findByFeedPostIdAndUserId(
            feedPost.getId(),
            currentUser.getId()
        )

        if (existingLike != null) {
            feedLikeRepository.delete(existingLike)
            feedPost.decreaseLikeCount()
            return false
        }

        feedLikeRepository.save(FeedLike(feedPost, currentUser))
        feedPost.increaseLikeCount()
        return true
    }
}
