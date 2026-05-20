package com.team10.backend.global.idempotency.validator

import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.idempotency.exception.IdempotencyException

/**
 * 멱등성 키의 형식을 검증하는 순수 함수형 컴포넌트
 */
object IdempotencyKeyValidator {

    // 검증 규칙 상수 (설정 파일로 이동 가능)
    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 128
    private val VALID_PATTERN = Regex("^[a-zA-Z0-9\\-_.]+$")  // 알파벳, 숫자, -, _, . 만 허용

    /**
     * 키 유효성 검증
     * @throws IdempotencyException 검증 실패 시
     */
    fun validate(key: String) {
        when {
            key.isBlank() -> {
                throw IdempotencyException(
                    ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key 는 빈 값일 수 없습니다."
                )
            }
            key.length < MIN_LENGTH -> {
                throw IdempotencyException(
                    ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key 는 최소 $MIN_LENGTH 자 이상이어야 합니다. (현재: ${key.length})"
                )
            }
            key.length > MAX_LENGTH -> {
                throw IdempotencyException(
                    ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key 는 최대 $MAX_LENGTH 자를 초과할 수 없습니다. (현재: ${key.length})"
                )
            }
            !VALID_PATTERN.matches(key) -> {
                throw IdempotencyException(
                    ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key 는 영문, 숫자, '-', '_', '.' 만 포함할 수 있습니다."
                )
            }
        }
    }

    /**
     * 키가 유효한지 간단 확인 (예외 던지지 않음)
     */
    fun isValid(key: String?): Boolean {
        return key != null &&
                key.length in MIN_LENGTH..MAX_LENGTH &&
                VALID_PATTERN.matches(key)
    }
}