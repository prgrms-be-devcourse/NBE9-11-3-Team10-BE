package com.team10.backend.domain.user.unit

import com.team10.backend.domain.image.service.ImageUploadService
import com.team10.backend.domain.user.dto.ProfileImageUpdateRequest
import com.team10.backend.domain.user.dto.SellerUpdateRequest
import com.team10.backend.domain.user.dto.UserUpdateRequest
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.domain.user.service.UserService
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    @Mock
    lateinit  var userRepository: UserRepository

    @Mock
    lateinit  var imageUploadService: ImageUploadService

    @InjectMocks
    lateinit  var userService: UserService

    @Test
    @DisplayName("사용자 개인정보 조회 - 성공")
    fun getUserProfile_success() {
        // given
        val user = UserFixture.create()
        ReflectionTestUtils.setField(user, "id", 1L)

        whenever(userRepository.findById(1L))
            .thenReturn(Optional.of(user))

        // when
        val response = userService.getUserProfile(1L)

        // then
        Assertions.assertNotNull(response)
        Assertions.assertEquals(user.name, response.name)
    }

    @Test
    @DisplayName("판매자 개인정보 조회 - 성공")
    fun getSellerProfile_success() {
        // given
        val user = UserFixture.createWithSellerInfo()
        ReflectionTestUtils.setField(user, "id", 1L)
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now())
        ReflectionTestUtils.setField(user, "updatedAt", LocalDateTime.now())

        whenever(userRepository.findById(1L))
            .thenReturn(Optional.of(user))

        // when
        val response = userService.getSellerProfile(1L)

        // then
        Assertions.assertNotNull(response)
        Assertions.assertEquals(user.name, response.name)
    }

    @Test
    @DisplayName("판매자 정보 조회 - 실패 (판매자가 아닌 경우)")
    fun getSellerProfile_fail_notSeller() {
        // given
        val user = UserFixture.create()

        whenever(userRepository.findById(1L))
            .thenReturn(Optional.of(user))

        // when & then
        val ex = assertThrows<BusinessException> {
            userService.getSellerProfile(1L)
        }

        Assertions.assertEquals(ErrorCode.NOT_SELLER, ex.errorCode)
    }

    @Test
    @DisplayName("사용자 개인정보 수정 - 성공")
    fun updateMyUserProfile_success() {
        // given
        val user = UserFixture.create()
        ReflectionTestUtils.setField(user, "id", 1L)

        user.updateProfileImage("https://old-image.test/profile.jpg")

        val request = UserUpdateRequest(
            "새로운닉네임",
            "010-9999-9999",
            "부산"
        )

        whenever(userRepository.findById(1L))
            .thenReturn(Optional.of(user))

        // when
        val response = userService.updateMyUserProfile(1L, request)

        // then
        Assertions.assertNotNull(response)
        Assertions.assertEquals("새로운닉네임", response.nickname)
        Assertions.assertEquals("010-9999-9999", response.phoneNumber)
        Assertions.assertEquals("부산", response.address)
    }

    @Test
    @DisplayName("사용자 프로필 이미지 수정 - 성공")
    fun updateMyUserProfileImage_success() {
        // given
        val user = UserFixture.create()
        ReflectionTestUtils.setField(user, "id", 1L)

        user.updateProfileImage("https://old-image.test/profile.jpg")

        val request =
            ProfileImageUpdateRequest("https://new-image.test/profile.jpg")

        whenever(userRepository.findById(1L))
            .thenReturn(Optional.of(user))

        // when
        val response = userService.updateMyProfileImage(1L, request)

        // then
        Assertions.assertEquals(
            "https://new-image.test/profile.jpg",
            response.imageUrl
        )
        verify(imageUploadService)
            .deleteIfManaged("https://old-image.test/profile.jpg")
    }

    @Test
    @DisplayName("사용자 프로필 이미지 삭제 - 성공")
    fun deleteMyUserProfileImage_success() {
        val user = UserFixture.create()
        ReflectionTestUtils.setField(user, "id", 1L)

        user.updateProfileImage("https://old-image.test/profile.jpg")

        whenever(userRepository.findById(1L))
            .thenReturn(Optional.of(user))

        val response = userService.deleteMyProfileImage(1L)

        Assertions.assertNull(response.imageUrl)
        verify(imageUploadService)
            .deleteIfManaged("https://old-image.test/profile.jpg")
    }

    @Test
    @DisplayName("판매자 개인정보 수정 - 성공")
    fun updateMySellerProfile_success() {
        // given
        val user = UserFixture.createWithSellerInfo()
        ReflectionTestUtils.setField(user, "id", 1L)
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now())
        ReflectionTestUtils.setField(user, "updatedAt", LocalDateTime.now())

        val request = SellerUpdateRequest(
            "새로운판매자",
            "010-8888-8888",
            "대구",
            "새로운 인사말입니다.",
            "999-999-99999"
        )

        whenever(userRepository.findById(1L))
            .thenReturn(Optional.of(user))

        // when
        val response = userService.updateMySellerProfile(1L, request)

        // then
        Assertions.assertNotNull(response)
        Assertions.assertEquals("새로운판매자", response.nickname)
        Assertions.assertEquals("대구", response.address)
        Assertions.assertEquals("새로운 인사말입니다.", response.bio)
        Assertions.assertEquals("999-999-99999", response.businessNumber)
    }

    @Test
    @DisplayName("존재하지 않는 사용자인 경우")
    fun getUserEntity_fail_notFound() {
        whenever(userRepository.findById(1L))
            .thenReturn(Optional.empty())

        val ex = assertThrows<BusinessException> {
            userService.getUserProfile(1L)
        }

        Assertions.assertEquals(ErrorCode.USER_NOT_FOUND, ex.errorCode)
    }
}
