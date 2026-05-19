package com.team10.backend.domain.user.integration

import com.team10.backend.domain.user.controller.UserController
import com.team10.backend.domain.user.dto.ProfileImageUpdateRequest
import com.team10.backend.domain.user.dto.SellerUpdateRequest
import com.team10.backend.domain.user.dto.UserUpdateRequest
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.test.AuthTestHelper
import org.hamcrest.Matchers
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
internal class UserIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    @DisplayName("유저 프로필 조회 - 성공")
    fun getUserProfile_success() {
        val user = UserFixture.create()
        saveAndSetAuth(user)

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/users/me"))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("getUserProfile"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.name").value(user.name))
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

        mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("updateMyUserProfile"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.nickname").value("새로운닉네임"))
    }

    @Test
    @DisplayName("유저 프로필 이미지 수정 - 성공")
    fun updateUserProfileImage_success() {
        val user = UserFixture.create()
        user.updateProfileImage("https://test.com/profile.jpg")
        saveAndSetAuth(user)

        val request =
            ProfileImageUpdateRequest("https://test.com/new-profile.jpg")

        mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/profile-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("updateMyProfileImage"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.imageUrl").value("https://test.com/new-profile.jpg"))
    }

    @Test
    @DisplayName("유저 프로필 이미지 삭제 - 성공")
    fun deleteUserProfileImage_success() {
        val user = UserFixture.create()
        user.updateProfileImage("https://test.com/profile.jpg")
        saveAndSetAuth(user)

        mvc.perform(MockMvcRequestBuilders.delete("/api/v1/me/profile-image"))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("deleteMyProfileImage"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.data.imageUrl")
                    .value(Matchers.nullValue()))
    }

    @Test
    @DisplayName("판매자 API 접근 실패 - BUYER")
    fun getSellerProfile_fail() {
        val user = UserFixture.create()
        saveAndSetAuth(user)

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/sellers/me"))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    @DisplayName("판매자 프로필 조회 - 성공")
    fun getSellerProfile_success() {
        val user = UserFixture.createWithSellerInfo()
        saveAndSetAuth(user)

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/sellers/me"))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("getSellerProfile"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.name").value(user.name))
    }

    @Test
    @DisplayName("판매자 프로필 수정 - 성공")
    fun updateSellerProfile_success() {
        val user = UserFixture.createWithSellerInfo()
        saveAndSetAuth(user)

        val request = SellerUpdateRequest(
            "새로운판매자",
            "010-9999-9999",
            "부산",
            "새로운 소개입니다",
            "999-99-99999"
        )

        mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/sellers/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("updateMySellerProfile"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.nickname").value("새로운판매자"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.bio").value("새로운 소개입니다"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.businessNumber").value("999-99-99999"))
    }

    @Test
    @DisplayName("판매자 프로필 이미지 수정 - 성공")
    fun updateSellerProfileImage_success() {
        val user = UserFixture.createWithSellerInfo()
        user.updateProfileImage("https://test.com/seller-profile.jpg")
        saveAndSetAuth(user)

        val request =
            ProfileImageUpdateRequest("https://test.com/new-seller-profile.jpg")

        mvc.perform(
            MockMvcRequestBuilders.put("/api/v1/me/profile-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("updateMyProfileImage"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.imageUrl").value("https://test.com/new-seller-profile.jpg"))
    }

    @Test
    @DisplayName("판매자 프로필 이미지 삭제 - 성공")
    fun deleteSellerProfileImage_success() {
        val user = UserFixture.createWithSellerInfo()
        user.updateProfileImage("https://test.com/seller-profile.jpg")
        saveAndSetAuth(user)

        mvc.perform(MockMvcRequestBuilders.delete("/api/v1/me/profile-image"))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.handler().handlerType(UserController::class.java))
            .andExpect(MockMvcResultMatchers.handler().methodName("deleteMyProfileImage"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.data.imageUrl")
                    .value(Matchers.nullValue()))
    }

    private fun saveAndSetAuth(user: User) {
        userRepository.save(user)
        AuthTestHelper.setAuth(user)
    }
}
