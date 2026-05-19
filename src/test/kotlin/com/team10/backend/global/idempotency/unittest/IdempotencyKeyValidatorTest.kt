package com.team10.backend.global.idempotency.unittest

import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.idempotency.exception.IdempotencyException
import com.team10.backend.global.idempotency.validator.IdempotencyKeyValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("IdempotencyKeyValidator 단위 테스트")
class IdempotencyKeyValidatorTest {

    // ─────────────────────────────────────────
    // 테스트 데이터 제공 메서드 (반드시 static + Stream<Arguments>)
    // ─────────────────────────────────────────
    companion object {
        @JvmStatic  // ← Java 에서 호출 가능하도록 (JUnit 5 필수)
        fun invalidKeyCases(): Stream<Arguments> = Stream.of(
            Arguments.of("", "빈 문자열"),
            Arguments.of("   ", "공백만 포함"),
            Arguments.of("ab", "최소 길이 미만 (2자)"),
            Arguments.of("a".repeat(129), "최대 길이 초과 (129자)")  // ✅ 런타임 표현식 사용 가능
        )

        @JvmStatic
        fun invalidPatternCases(): Stream<Arguments> = Stream.of(
            Arguments.of("key@invalid", "@ 문자"),
            Arguments.of("key with space", "공백 포함"),
            Arguments.of("key/invalid", "/ 문자"),
            Arguments.of("key#invalid", "# 문자"),
            Arguments.of("key\$invalid", "$ 문자")
        )
    }

    @ParameterizedTest
    @MethodSource("invalidKeyCases")  // ← 메서드 이름과 일치해야 함
    @DisplayName("길이 위반 키는 예외를 던진다")
    fun `길이 위반 키는 예외를 던진다`(key: String, description: String) {
        val exception = assertThrows<IdempotencyException> {
            IdempotencyKeyValidator.validate(key)
        }
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_INVALID, exception.errorCode)
        assertFalse(IdempotencyKeyValidator.isValid(key))
    }

    @ParameterizedTest
    @MethodSource("invalidPatternCases")
    @DisplayName("허용되지 않은 문자가 포함된 키는 예외를 던진다")
    fun `허용되지 않은 문자가 포함된 키는 예외를 던진다`(key: String, description: String) {
        val exception = assertThrows<IdempotencyException> {
            IdempotencyKeyValidator.validate(key)
        }
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_INVALID, exception.errorCode)
    }
}