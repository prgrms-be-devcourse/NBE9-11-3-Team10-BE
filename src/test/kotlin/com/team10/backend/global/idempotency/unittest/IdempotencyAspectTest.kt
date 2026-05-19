package com.team10.backend.global.idempotency.unittest

import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.idempotency.IdempotencyStatus
import com.team10.backend.global.idempotency.IdempotencyStore
import com.team10.backend.global.idempotency.Idempotent
import com.team10.backend.global.idempotency.aspect.IdempotencyAspect
import com.team10.backend.global.idempotency.exception.IdempotencyException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.*
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Method
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("IdempotencyAspect 단위 테스트")
class IdempotencyAspectTest {

    @Mock
    private lateinit var store: IdempotencyStore

    @Mock
    private lateinit var joinPoint: ProceedingJoinPoint

    @Mock
    private lateinit var methodSignature: MethodSignature

    @Mock
    private lateinit var method: Method

    @InjectMocks
    private lateinit var aspect: IdempotencyAspect

    private lateinit var mockRequest: MockHttpServletRequest
    private val testKey = "test-idempotency-key"
    private val defaultLockTtl = 15
    private val defaultCacheTtl = 86400

    // 테스트용 더미 어노테이션 (런타임 리플렉션용)
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    @Idempotent(lockTtlSec = 15, cacheTtlSec = 86400)
    annotation class DummyIdempotent

    companion object {
        private val DEFAULT_RETURN_TYPE = Map::class.java
    }

    @BeforeEach
    fun setUp() {
        mockRequest = MockHttpServletRequest()
        // RequestContextHolder 에 목 요청 설정 (Aspect 가 헤더/속성 읽을 수 있도록)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(mockRequest))
    }

    @AfterEach
    fun tearDown() {
        // 정적 상태 초기화로 테스트 간 간섭 방지
        RequestContextHolder.resetRequestAttributes()
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 테스트 그룹 1: Idempotency-Key 추출 실패
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("extractIdempotencyKey 실패 시")
    inner class KeyMissingTests {

        @Test
        fun `헤더와 속성 모두 없을 때 예외를 던진다`() {
            // Given: 키가 전혀 설정되지 않음
            // When & Then
            val exception = assertThrows<IdempotencyException> {
                aspect.handleIdempotency(joinPoint)
            }
            assertEquals(ErrorCode.IDEMPOTENCY_KEY_MISSING, exception.errorCode)
            assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 테스트 그룹 2: Idempotency-Key 추출 성공 후 상태 분기
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Idempotency-Key 존재 시 상태별 동작")
    inner class StatusBranchTests {

        @BeforeEach
        fun setupKeyAndAnnotation() {
            // 키 설정 (헤더 방식)
            mockRequest.addHeader("Idempotency-Key", testKey)

            // @Idempotent 어노테이션 모킹
            lenient().`when`(joinPoint.signature).thenReturn(methodSignature)
            lenient().`when`(methodSignature.method).thenReturn(method)
            lenient().`when`(method.getAnnotation(Idempotent::class.java)).thenReturn(
                DummyIdempotent::class.java.getAnnotation(Idempotent::class.java)
            )
            lenient().`when`(methodSignature.returnType).thenReturn(DEFAULT_RETURN_TYPE)
        }

        @Test
        fun `COMPLETED 상태이고 캐시 존재 시 캐시된 값을 반환한다`() {
            // Given
            val cachedResponse = """{"result":"cached","timestamp":"2026-05-19T10:30:00"}"""
            given(store.checkAndLock(eq(testKey), eq(defaultLockTtl)))
                .willReturn(IdempotencyStatus.COMPLETED)
            given(store.getResponse(testKey)).willReturn(cachedResponse)

            // When
            val result = aspect.handleIdempotency(joinPoint)

            // Then
            assertTrue(result is Map<*, *>) // JSON 이 Map 으로 역직렬화됨
            assertEquals("cached", (result as Map<*, *>)["result"])
            verify(store, never()).complete(any(), any(), any()) // 캐시 히트 시 저장 안 함
        }

        @Test
        fun `COMPLETED 상태이지만 캐시가 없으면 예외를 던진다`() {
            // Given
            given(store.checkAndLock(eq(testKey), eq(defaultLockTtl)))
                .willReturn(IdempotencyStatus.COMPLETED)
            given(store.getResponse(testKey)).willReturn(null)

            // When & Then
            val exception = assertThrows<IdempotencyException> {
                aspect.handleIdempotency(joinPoint)
            }
            assertEquals(ErrorCode.IDEMPOTENCY_CACHE_MISS, exception.errorCode)
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.status)
        }

        @Test
        fun `LOCKED 상태이면 진행 중 예외를 던진다`() {
            // Given
            given(store.checkAndLock(eq(testKey), eq(defaultLockTtl)))
                .willReturn(IdempotencyStatus.LOCKED)

            // When & Then
            val exception = assertThrows<IdempotencyException> {
                aspect.handleIdempotency(joinPoint)
            }
            assertEquals(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, exception.errorCode)
            assertEquals(HttpStatus.CONFLICT, exception.status)
        }

        @Test
        fun `NONE 상태이면 실제 메서드를 실행하고 결과를 캐시한다`() {
            // Given
            val businessResult = mapOf("orderId" to "ORD-123", "amount" to 10000)
            given(store.checkAndLock(eq(testKey), eq(defaultLockTtl)))
                .willReturn(IdempotencyStatus.NONE)
            given(joinPoint.proceed()).willReturn(businessResult)
            given(store.complete(eq(testKey), anyString(), eq(defaultCacheTtl)))
                .willReturn(true)

            // When
            val result = aspect.handleIdempotency(joinPoint)

            // Then
            assertEquals(businessResult, result)
            verify(joinPoint).proceed()  // 실제 로직 실행 확인

            val jsonCaptor = argumentCaptor<String>()
            verify(store).complete(
                eq(testKey),
                jsonCaptor.capture(),
                eq(defaultCacheTtl)
            )

            // 캡처된 값으로 상세 검증
            val capturedJson = jsonCaptor.firstValue
            assert(capturedJson.contains("\"orderId\":\"ORD-123\""))  // JSON 형식 확인
            assert(capturedJson.contains("\"amount\":10000"))
        }

        @Test
        fun `NONE 상태에서 비즈니스 로직 실패 시 락을 해제하고 예외를 전파한다`() {
            // Given
            val businessException = RuntimeException("결제 실패")
            given(store.checkAndLock(eq(testKey), eq(defaultLockTtl)))
                .willReturn(IdempotencyStatus.NONE)
            given(joinPoint.proceed()).willThrow(businessException)

            // When & Then
            val exception = assertThrows<RuntimeException> {
                aspect.handleIdempotency(joinPoint)
            }
            assertEquals("결제 실패", exception.message)
            verify(store).release(testKey)  // 락 해제 확인
            verify(store, never()).complete(any(), any(), any())  // 캐싱 안 함
        }

        @Test
        fun `캐시 저장 실패 시 경고 로그를 남기고 결과를 반환한다`() {
            // Given: store.complete() 가 경쟁 상태로 실패하는 시나리오
            val businessResult = "success"
            given(store.checkAndLock(eq(testKey), eq(defaultLockTtl)))
                .willReturn(IdempotencyStatus.NONE)
            given(joinPoint.proceed()).willReturn(businessResult)
            given(store.complete(eq(testKey), any<String>(), eq(defaultCacheTtl)))
                .willReturn(false)  // ❌ 캐시 저장 실패

            // When
            val result = aspect.handleIdempotency(joinPoint)

            // Then: 비즈니스 결과는 정상 반환, 캐싱만 실패
            assertEquals(businessResult, result)
            verify(store).complete(any(), any(), any())  // 시도는 함
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 테스트 그룹 3: Request Attribute 우선순위 검증
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Idempotency-Key 추출 우선순위")
    inner class KeyExtractionPriorityTests {

        @BeforeEach
        fun setupAnnotation() {
            whenever(joinPoint.signature).thenReturn(methodSignature)
            whenever(methodSignature.method).thenReturn(method)
            whenever(method.getAnnotation(Idempotent::class.java)).thenReturn(
                DummyIdempotent::class.java.getAnnotation(Idempotent::class.java)
            )
        }

        @Test
        fun `Request Attribute 가 헤더보다 우선한다`() {
            // Given: 둘 다 설정되었을 때 Attribute 가 우선
            mockRequest.setAttribute("IDEMPOTENCY_KEY", "attr-key")
            mockRequest.addHeader("Idempotency-Key", "header-key")

            given(store.checkAndLock(eq("attr-key"), any())).willReturn(IdempotencyStatus.NONE)
            given(joinPoint.proceed()).willReturn("ok")
            given(store.complete(any(), any(), any())).willReturn(true)

            // When
            aspect.handleIdempotency(joinPoint)

            // Then
            verify(store).checkAndLock(eq("attr-key"), any())  // ✅ Attribute 키 사용
            verify(store, never()).checkAndLock(eq("header-key"), any())
        }

        @Test
        fun `Attribute 가 없을 때 헤더를 폴백으로 사용한다`() {
            // Given: Attribute 는 없고 헤더만 있음
            mockRequest.addHeader("Idempotency-Key", "header-key")

            given(store.checkAndLock(eq("header-key"), any())).willReturn(IdempotencyStatus.NONE)
            given(joinPoint.proceed()).willReturn("ok")
            given(store.complete(any(), any(), any())).willReturn(true)

            // When
            aspect.handleIdempotency(joinPoint)

            // Then
            verify(store).checkAndLock(eq("header-key"), any())  // ✅ 헤더 키 사용
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 테스트 그룹 4: ObjectMapper 설정 검증
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ObjectMapper 직렬화 설정")
    inner class ObjectMapperTests {

        @BeforeEach
        fun setupKeyAndAnnotation() {
            mockRequest.addHeader("Idempotency-Key", testKey)
            whenever(joinPoint.signature).thenReturn(methodSignature)
            whenever(methodSignature.method).thenReturn(method)
            whenever(method.getAnnotation(Idempotent::class.java)).thenReturn(
                DummyIdempotent::class.java.getAnnotation(Idempotent::class.java)
            )
        }

        @Test
        fun `LocalDateTime 필드가 포함된 객체를 ISO 문자열로 직렬화한다`() {
            // Given: java.time 타입이 포함된 결과 객체
            data class ResponseDto(val id: String, val createdAt: LocalDateTime)
            val result = ResponseDto("TEST-001", LocalDateTime.of(2026, 5, 19, 10, 30))

            given(store.checkAndLock(eq(testKey), any())).willReturn(IdempotencyStatus.NONE)
            given(joinPoint.proceed()).willReturn(result)
            given(store.complete(any(), any(), any())).willReturn(true)

            // When
            aspect.handleIdempotency(joinPoint)

            // Then: ArgumentCaptor 로 캡처 후 검증
            val jsonCaptor = argumentCaptor<String>()
            verify(store).complete(eq(testKey), jsonCaptor.capture(), anyInt())

            val capturedJson = jsonCaptor.firstValue
            assertTrue(capturedJson.contains("2026-05-19T10:30:00"))  // ISO-8601 형식
            assertFalse(capturedJson.contains("epoch-second"))         // 타임스탬프 아님
        }


    }
}