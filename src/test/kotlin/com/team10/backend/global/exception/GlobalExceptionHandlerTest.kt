package com.team10.backend.global.exception

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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest // ✅ 전체 컨텍스트 로드 (안정성 최우선)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("필수 필드 5개가 모두 정확히 응답된다")
    fun requiredFields_areReturned() {
        val expectedCode = ErrorCode.USER_NOT_FOUND.code

        mockMvc.perform(MockMvcRequestBuilders.get("/test/business"))
            .andExpect(MockMvcResultMatchers.status().isNotFound())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("https://api.example.com/errors/$expectedCode"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Not Found"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(404))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").value("회원을 찾을 수 없습니다."))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value(expectedCode))
            .andExpect(MockMvcResultMatchers.jsonPath("$.instance").value("/test/business"))
    }

    @Test
    @DisplayName("traceId 가 응답에 포함되면 UUID 포맷을 따른다")
    fun traceId_ifPresent_hasValidFormat() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/test/business")
                .requestAttr("traceId", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        )
            .andExpect(MockMvcResultMatchers.status().isNotFound())
            .andExpect(MockMvcResultMatchers.jsonPath("$.traceId").exists())
            .andExpect(MockMvcResultMatchers.jsonPath("$.traceId").value("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
    }

    @Test
    @DisplayName("검증 실패 시 필수 필드 + validationErrors 확장 필드가 포함된다")
    fun validationFailure_returnsProblemDetailWithErrors() {
        val invalidJson = """{"name":"","email":"not-an-email"}"""

        mockMvc.perform(
            MockMvcRequestBuilders.post("/test/valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.type").value("https://api.example.com/errors/VALIDATION_FAILED")
            )
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Bad Request"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(400))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").exists())
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.instance").value("/test/valid"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.validationErrors").isArray())
            .andExpect(MockMvcResultMatchers.jsonPath("$.validationErrors[?(@.field=='email')]").exists())
    }

    @Test
    @DisplayName("내부 오류 시 민감 정보가 detail 에 노출되지 않는다")
    fun unexpectedException_doesNotExposeSensitiveInfo() {
        mockMvc.perform(MockMvcRequestBuilders.get("/test/unexpected"))
            .andExpect(MockMvcResultMatchers.status().isInternalServerError())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("https://api.example.com/errors/INTERNAL_ERROR"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.title").value("Internal Server Error"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(500))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").value("서버 내부 오류가 발생했습니다. 관리자에게 문의하세요."))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.instance").value("/test/unexpected"))
            .andExpect(
                MockMvcResultMatchers.jsonPath(
                    "$.detail",
                    Matchers.not(Matchers.containsString("테스트용 예외"))
                )
            )
            .andExpect(MockMvcResultMatchers.jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    @DisplayName("타입 변환 실패 (MethodArgumentTypeMismatchException)")
    fun handleTypeMismatch() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/test/param-type")
                .param("id", "not-a-number")
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("TYPE_MISMATCH"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").value(Matchers.containsString("타입 변환 실패")))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").value(Matchers.containsString("id")))
    }

    @Test
    @DisplayName("필수 파라미터 누락 (MissingServletRequestParameterException)")
    fun handleMissingParam() {
        mockMvc.perform(MockMvcRequestBuilders.get("/test/param-missing"))
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("PARAM_MISSING"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").value("필수 파라미터 누락: 'name'"))
    }

    @Test
    @DisplayName("파라미터 @NotBlank 검증 실패")
    fun handleMethodValidationFailure() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/test/validated")
                .param("code", "  ")
        ) // 공백만 전달 → @NotBlank 실패
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.errorCode").value("VALIDATION_FAILED")
            ) // ✅ 수정된 기대값: "code: must not be blank" 포함 확인
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").value(Matchers.containsString("code")))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.detail").value(Matchers.containsString("must not be blank"))
            )
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 (405)")
    fun handleMethodNotAllowed() {
        // POST 엔드포인트를 GET 으로 호출
        mockMvc.perform(MockMvcRequestBuilders.get("/test/method-test"))
            .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.detail")
                    .value(Matchers.containsString("지원하지 않는 HTTP 메서드"))
            )
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type (415)")
    fun handleUnsupportedMediaType() {
        // JSON 이 필요한 엔드포인트에 text/plain 전송
        mockMvc.perform(
            MockMvcRequestBuilders.post("/test/media-type-test")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not-json")
        )
            .andExpect(MockMvcResultMatchers.status().isUnsupportedMediaType())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.detail").value("지원하지 않는 Content-Type 입니다."))
    }

    @Test
    @DisplayName("존재하지 않는 경로 (404 - NoHandlerFoundException)")
    fun handleNotFound() {
        mockMvc.perform(MockMvcRequestBuilders.get("/test/this-path-does-not-exist"))
            .andExpect(MockMvcResultMatchers.status().isNotFound())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("NOT_FOUND"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.detail")
                    .value(Matchers.containsString("찾을 수 없습니다"))
            )
    }

    @Test
    @DisplayName("DB 무결성 위반 (409 - DataIntegrityViolationException)")
    fun handleDataIntegrityViolation() {
        // TestController 에서 직접 예외 던지기로 시뮬레이션
        mockMvc.perform(MockMvcRequestBuilders.get("/test/db-violation"))
            .andExpect(MockMvcResultMatchers.status().isConflict())
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.detail")
                    .value("이미 존재하는 리소스입니다. 또는 데이터 무결성 제약조건에 위반됩니다.")
            )
    }
}