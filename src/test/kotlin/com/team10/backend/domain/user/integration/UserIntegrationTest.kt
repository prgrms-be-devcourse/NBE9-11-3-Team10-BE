package com.team10.backend.domain.user.integration

import com.team10.backend.domain.user.controller.UserController
import com.team10.backend.domain.user.dto.ProfileImageUpdateRequest
import com.team10.backend.domain.user.dto.SellerUpdateRequest
import com.team10.backend.domain.user.dto.UserUpdateRequest
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.UserFixture
import com.team10.backend.helper.AuthTestHelper
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
internal class UserIntegrationTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
) {

    @Test
    @DisplayName("유저 프로필 조회 - 성공")
    fun getUserProfile_success() {
        val user = UserFixture.create()
        saveAndSetAuth(user)

        mvc.perform(get("/api/v1/users/me"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("getUserProfile"))
            .andExpect(jsonPath("$.data.name").value(user.name))
    }

    @Test
    @DisplayName("유저 프로필 수정 - 성공")
    fun updateUserProfile_success() {
        val user = UserFixture.create()
        saveAndSetAuth(user)

        val request = UserUpdateRequest(
            "새로운닉네임",
            "010-9999-9999",
            "부산"
        )

        mvc.perform(put("/api/v1/users/me")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("updateMyUserProfile"))
            .andExpect(jsonPath("$.data.nickname").value("새로운닉네임"))
    }

    @Test
    @DisplayName("유저 프로필 이미지 수정 - 성공")
    fun updateUserProfileImage_success() {
        val user = UserFixture.create()
        user.updateProfileImage("https://test.com/profile.jpg")
        saveAndSetAuth(user)

        val request =
            ProfileImageUpdateRequest("https://test.com/new-profile.jpg")

        mvc.perform(put("/api/v1/me/profile-image")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("updateMyProfileImage"))
            .andExpect(jsonPath("$.data.imageUrl").value("https://test.com/new-profile.jpg"))
    }

    @Test
    @DisplayName("유저 프로필 이미지 삭제 - 성공")
    fun deleteUserProfileImage_success() {
        val user = UserFixture.create()
        user.updateProfileImage("https://test.com/profile.jpg")
        saveAndSetAuth(user)

        mvc.perform(delete("/api/v1/me/profile-image"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("deleteMyProfileImage"))
            .andExpect(jsonPath("$.data.imageUrl").value(nullValue()))
    }

    @Test
    @DisplayName("판매자 API 접근 실패 - BUYER")
    fun getSellerProfile_fail() {
        val user = UserFixture.create()
        saveAndSetAuth(user)

        mvc.perform(get("/api/v1/sellers/me"))
            .andDo(print())
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("판매자 프로필 조회 - 성공")
    fun getSellerProfile_success() {
        val user = UserFixture.createWithSellerInfo()
        saveAndSetAuth(user)

        mvc.perform(get("/api/v1/sellers/me"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("getSellerProfile"))
            .andExpect(jsonPath("$.data.name").value(user.name))
    }

    @Test
    @DisplayName("판매자 프로필 수정 - 성공")
    fun updateSellerProfile_success() {
        val user = UserFixture.createWithSellerInfo()
        saveAndSetAuth(user)

        val request = SellerUpdateRequest(
            nickname = "새로운판매자",
            phoneNumber = "010-9999-9999",
            address = "부산",
            bio = "새로운 소개입니다",
            businessNumber = "999-99-99999"
        )

        mvc.perform(put("/api/v1/sellers/me")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("updateMySellerProfile"))
            .andExpect(jsonPath("$.data.nickname").value("새로운판매자"))
            .andExpect(jsonPath("$.data.bio").value("새로운 소개입니다"))
            .andExpect(jsonPath("$.data.businessNumber").value("999-99-99999"))
    }

    @Test
    @DisplayName("판매자 프로필 이미지 수정 - 성공")
    fun updateSellerProfileImage_success() {
        val user = UserFixture.createWithSellerInfo()
        user.updateProfileImage("https://test.com/seller-profile.jpg")
        saveAndSetAuth(user)

        val request =
            ProfileImageUpdateRequest("https://test.com/new-seller-profile.jpg")

        mvc.perform(put("/api/v1/me/profile-image")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("updateMyProfileImage"))
            .andExpect(jsonPath("$.data.imageUrl").value("https://test.com/new-seller-profile.jpg"))
    }

    @Test
    @DisplayName("판매자 프로필 이미지 삭제 - 성공")
    fun deleteSellerProfileImage_success() {
        val user = UserFixture.createWithSellerInfo()
        user.updateProfileImage("https://test.com/seller-profile.jpg")
        saveAndSetAuth(user)

        mvc.perform(delete("/api/v1/me/profile-image"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(UserController::class.java))
            .andExpect(handler().methodName("deleteMyProfileImage"))
            .andExpect(jsonPath("$.data.imageUrl").value(nullValue()))
    }

    private fun saveAndSetAuth(user: User) {
        userRepository.save(user)
        AuthTestHelper.setAuth(user)
    }
}
