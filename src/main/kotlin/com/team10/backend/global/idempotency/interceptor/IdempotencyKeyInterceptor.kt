package com.team10.backend.global.idempotency.interceptor

import com.team10.backend.global.idempotency.validator.IdempotencyKeyValidator
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 멱등성 키 추출 및 검증을 담당하는 인터셉터
 *
 * 실행 순서:
 * 1. 클라이언트 요청 수신
 * 2. 이 인터셉터 실행 → 키 추출/검증 → request attribute 에 설정
 * 3. 컨트롤러/Aspect 실행 (검증된 키만 사용)
 */
@Component
class IdempotencyKeyInterceptor : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // Aspect 가 읽을 request attribute 키 (일관된 상수 사용)
        const val REQUEST_ATTRIBUTE_KEY = "IDEMPOTENCY_KEY"
        const val HEADER_NAME = "Idempotency-Key"
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val rawKey = extractRawKey(request)

        // 키가 없으면 단순 통과 (Aspect 에서 최종 검증)
        if (rawKey == null) {
            log.debug("Idempotency-Key header not found in request: {}", request.requestURI)
            return true
        }

        IdempotencyKeyValidator.validate(rawKey)

        // ✅ 검증된 키를 request attribute 에 설정 (Aspect 가 읽을 수 있도록)
        request.setAttribute(REQUEST_ATTRIBUTE_KEY, rawKey)
        log.debug("Idempotency-Key validated and set: key={}", maskKey(rawKey))

        return true
    }

    /**
     * 요청에서 원본 키 추출 (헤더 우선, 속성 폴백)
     */
    private fun extractRawKey(request: HttpServletRequest): String? {
        // 1순위: 이미 다른 필터/인터셉터에서 설정한 속성
        return request.getAttribute(REQUEST_ATTRIBUTE_KEY) as? String
        // 2순위: HTTP 헤더에서 직접 읽기
            ?: request.getHeader(HEADER_NAME)?.takeIf { it.isNotBlank() }
    }

    /**
     * 로그에 키를 마스킹하여 출력 (보안 고려)
     */
    private fun maskKey(key: String): String {
        return if (key.length <= 8) {
            "***"
        } else {
            "${key.take(4)}***${key.takeLast(4)}"
        }
    }
}