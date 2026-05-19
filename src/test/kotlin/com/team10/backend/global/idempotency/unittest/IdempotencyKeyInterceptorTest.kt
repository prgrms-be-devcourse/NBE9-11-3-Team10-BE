package com.team10.backend.global.idempotency.unittest

import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.idempotency.exception.IdempotencyException
import com.team10.backend.global.idempotency.interceptor.IdempotencyKeyInterceptor
import com.team10.backend.global.idempotency.validator.IdempotencyKeyValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

@DisplayName("IdempotencyKeyInterceptor 단위 테스트")
class IdempotencyKeyInterceptorTest {

    private lateinit var interceptor: IdempotencyKeyInterceptor
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        interceptor = IdempotencyKeyInterceptor()
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
    }

    @Test
    fun `유효한 키가 헤더에 있으면 속성에 설정하고 통과시킨다`() {
        // Given
        val validKey = "order-2026-05-19-abc123"
        request.addHeader(IdempotencyKeyInterceptor.HEADER_NAME, validKey)

        // When
        val result = interceptor.preHandle(request, response, Any())

        // Then
        assertTrue(result)  // 요청 계속 진행
        assertEquals(validKey, request.getAttribute(IdempotencyKeyInterceptor.REQUEST_ATTRIBUTE_KEY))
    }

    @Test
    fun `키가 없으면 속성 설정 없이 통과시킨다`() {
        // When
        val result = interceptor.preHandle(request, response, Any())

        // Then: 키 없음은 Aspect 에서 최종 처리하므로 인터셉터는 통과
        assertTrue(result)
        assertNull(request.getAttribute(IdempotencyKeyInterceptor.REQUEST_ATTRIBUTE_KEY))
    }

    @Test
    fun `유효하지 않은 키는 예외를 던진다`() {
        // Given: 형식 위반 키
        val invalidKey = "invalid@key!"
        request.addHeader(IdempotencyKeyInterceptor.HEADER_NAME, invalidKey)

        // When & Then
        val exception = assertThrows<IdempotencyException> {
            interceptor.preHandle(request, response, Any())
        }
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_INVALID, exception.errorCode)
        assertTrue(exception.message?.contains("Idempotency-Key") == true)
    }

    @Test
    fun `이미 속성에 설정된 키는 헤더보다 우선한다`() {
        // Given: 속성과 헤더에 다른 키가 모두 있음
        val attrKey = "attr-key-001"
        val headerKey = "header-key-002"

        request.setAttribute(IdempotencyKeyInterceptor.REQUEST_ATTRIBUTE_KEY, attrKey)
        request.addHeader(IdempotencyKeyInterceptor.HEADER_NAME, headerKey)

        // When
        interceptor.preHandle(request, response, Any())

        // Then: 속성 키가 우선 사용됨
        assertEquals(attrKey, request.getAttribute(IdempotencyKeyInterceptor.REQUEST_ATTRIBUTE_KEY))
    }

    @Test
    fun `Validator 와 Interceptor 연동 최종 검증`() {
        // 1. Validator 직접 검증
        assertThrows<IdempotencyException> {
            IdempotencyKeyValidator.validate("invalid@key!")
        }

        // 2. Interceptor 에 주입 시에도 동일하게 예외 전파 확인
        request.addHeader(IdempotencyKeyInterceptor.HEADER_NAME, "invalid@key!")

        assertThrows<IdempotencyException> {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
    }
}