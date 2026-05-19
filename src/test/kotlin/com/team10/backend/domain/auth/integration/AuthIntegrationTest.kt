package com.team10.backend.domain.auth.integration

import com.team10.backend.domain.auth.controller.AuthController
import com.team10.backend.domain.auth.dto.AuthRegisterRequest
import com.team10.backend.domain.auth.dto.LoginRequest
import com.team10.backend.fixture.UserFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
internal class AuthIntegrationTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper
) {

    @Test
    @DisplayName("회원가입 성공")
    fun register_success() {
        val request = UserFixture.createAuthRegisterRequest(
            email = "user@example.com"
        )

        register(request)
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(AuthController::class.java))
            .andExpect(handler().methodName("register"))
            .andExpect(jsonPath("$.data.email").value("user@example.com"))
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    fun register_fail_duplicatedEmail() {
        val request = UserFixture.createAuthRegisterRequest(
            email = "user@example.com"
        )

        register(request)
            .andExpect(status().isOk)

        val duplicateRequest = UserFixture.createAuthRegisterRequest(
            email = "user@example.com"
        )

       register(duplicateRequest)
            .andDo(print())
            .andExpect(status().isConflict)
    }

    @Test
    @DisplayName("회원가입 실패 - 닉네임 중복")
    fun register_fail_duplicatedNickname() {
        val request = UserFixture.createAuthRegisterRequest(
            nickname = "길동이"
        )

       register(request)
           .andExpect(status().isOk)

        val duplicateNicknameRequest = UserFixture.createAuthRegisterRequest(
            nickname = "길동이"
        )

        register(duplicateNicknameRequest)
            .andDo(print())
            .andExpect(status().isConflict)
    }

    @Test
    @DisplayName("중복 확인 (이메일) - 사용 가능")
    fun checkDuplicate_available() {
        mvc.perform(get("/api/v1/auth/check-duplicate")
                    .param("type", "EMAIL")
                    .param("value", "test@example.com")
            )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(handler().handlerType(AuthController::class.java))
            .andExpect(handler().methodName("checkDuplicate"))
            .andExpect(jsonPath("$.data.type").value("EMAIL"))
            .andExpect(jsonPath("$.data.value").value("test@example.com"))
            .andExpect(jsonPath("$.data.available").value(true))
    }

    @Test
    @DisplayName("중복 확인 (이메일) - 사용 불가")
    fun checkDuplicate_unavailable() {
        val request = UserFixture.createAuthRegisterRequest(
            email = "user@example.com"
        )

        register(request)
            .andExpect(status().isOk)

        mvc.perform(get("/api/v1/auth/check-duplicate")
                    .param("type", "EMAIL")
                    .param("value", "user@example.com")
            )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.available").value(false))
    }

    @Test
    @DisplayName("중복 확인 실패 - type 파라미터 누락")
    fun checkDuplicate_fail_missingType() {
        mvc.perform(get("/api/v1/auth/check-duplicate")
                .param("value", "test@example.com")
            )
            .andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("중복 확인 실패 - value 파라미터 누락")
    fun checkDuplicate_fail_missingValue() {
        mvc.perform(get("/api/v1/auth/check-duplicate")
                .param("type", "EMAIL")
            )
            .andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("중복 확인 실패 - 존재하지 않는 type 값")
    fun checkDuplicate_fail_invalidType() {
        mvc.perform(get("/api/v1/auth/check-duplicate")
                .param("type", "PHONE")
                .param("value", "010-1111-2222")
            )
            .andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("로그인 성공")
    fun login_success() {
        val registerRequest = UserFixture.createAuthRegisterRequest()

        register(registerRequest)
            .andExpect(status().isOk)

        val loginRequest = LoginRequest(
            email = registerRequest.email,
            password = registerRequest.password
        )

        mvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
            )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").value(loginRequest.email))
            .andExpect(cookie().exists("accessToken"))
            .andExpect(cookie().exists("refreshToken"))
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 오류")
    fun login_fail_wrong_password() {
        val registerRequest = UserFixture.createAuthRegisterRequest()

        register(registerRequest)
            .andExpect(status().isOk)

        val loginRequest = LoginRequest(
            email = registerRequest.email,
            password = "wrong-password"
        )

        mvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
            )
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(cookie().doesNotExist("accessToken"))
            .andExpect(cookie().doesNotExist("refreshToken"))
    }

    @Test
    @DisplayName("로그아웃 성공")
    fun logout_success() {
        val registerRequest = UserFixture.createAuthRegisterRequest()

        register(registerRequest)
            .andExpect(status().isOk)

        val loginRequest = LoginRequest(
            email = registerRequest.email,
            password = registerRequest.password
        )

        val loginResponse = mvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        ).andReturn().response

        val refreshCookie = checkNotNull(
            loginResponse.getCookie("refreshToken")
        )

        mvc.perform(post("/api/v1/auth/logout")
                .cookie(refreshCookie)
            )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(cookie().maxAge("accessToken", 0))
            .andExpect(cookie().maxAge("refreshToken", 0))
    }

    private fun register(request: AuthRegisterRequest) =
        mvc.perform(post("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
}
