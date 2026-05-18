package com.team10.backend.domain.user.service

import com.team10.backend.domain.image.service.ImageUploadService
import com.team10.backend.domain.user.dto.ProfileImageUpdateRequest
import com.team10.backend.domain.user.dto.SellerPublicResponse
import com.team10.backend.domain.user.dto.SellerResponse
import com.team10.backend.domain.user.dto.SellerUpdateRequest
import com.team10.backend.domain.user.dto.UserResponse
import com.team10.backend.domain.user.dto.UserUpdateRequest
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val imageUploadService: ImageUploadService
) {
    @Transactional(readOnly = true)
    fun getUserProfile(userId: Long): UserResponse =
        UserResponse.from(getUserEntity(userId))

    @Transactional(readOnly = true)
    fun getSellerProfile(userId: Long): SellerResponse {
        val user = getUserEntity(userId)
        validateSellerRole(user)
        return SellerResponse.from(user)
    }

    @Transactional(readOnly = true)
    fun getSellerPublicProfile(id: Long): SellerPublicResponse =
        SellerPublicResponse.from(getUserEntity(id))

    @Transactional
    fun updateMyUserProfile(id: Long, request: UserUpdateRequest): UserResponse {
        val user = getUserEntity(id)

        user.updateUserInfo(
            request.nickname,
            request.phoneNumber,
            request.address
        )

        return UserResponse.from(user)
    }

    @Transactional
    fun updateMySellerProfile(id: Long, request: SellerUpdateRequest): SellerResponse {
        val user = getUserEntity(id)
        validateSellerRole(user)

        user.updateSellerProfile(
            request.nickname,
            request.phoneNumber,
            request.address,
            request.bio,
            request.businessNumber
        )

        return SellerResponse.from(user)
    }

    @Transactional
    fun updateMyProfileImage(id: Long, request: ProfileImageUpdateRequest): UserResponse {
        val user = getUserEntity(id)
        updateProfileImage(user, request.imageUrl)

        return UserResponse.from(user)
    }

    @Transactional
    fun deleteMyProfileImage(id: Long): UserResponse {
        val user = getUserEntity(id)
        updateProfileImage(user, null)

        return UserResponse.from(user)
    }

    private fun updateProfileImage(user: User, imageUrl: String?) {
        val oldImageUrl = user.imageUrl
        if (oldImageUrl != imageUrl) {
            imageUploadService.deleteIfManaged(oldImageUrl)
            user.updateProfileImage(imageUrl)
        }
    }

    private fun getUserEntity(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

    private fun validateSellerRole(user: User) {
        if (user.role != Role.SELLER) {
            throw BusinessException(ErrorCode.NOT_SELLER)
        }
    }
}
