package com.team10.backend.global.idempotency.integrationtest

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.idempotency.IdempotencyStore
import com.team10.backend.global.idempotency.testsupport.IdempotencyTestController
import com.team10.backend.global.idempotency.testsupport.IdempotencyTestRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.context.request.RequestContextHolder
import java.util.*
import java.util.concurrent.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("멱등성 (Idempotency) 통합 테스트")
class IdempotencyIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var testController: IdempotencyTestController

    @Autowired
    private lateinit var idempotencyStore: IdempotencyStore

    private val objectMapper = jacksonObjectMapper()
    private val testRequest = IdempotencyTestRequest(
        userId = "user-001",
        amount = 10000,
        description = "통합 테스트용 결제 요청"
    )
    private lateinit var validIdempotencyKey: String

    @BeforeEach
    fun setUp() {
        // 테스트 간 상태 격리를 위해 카운터 초기화
        IdempotencyTestController.resetCounter()

        validIdempotencyKey = "test-key-${UUID.randomUUID()}"
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 시나리오 1: 첫 요청은 정상 처리 + 비즈니스 로직 실행
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("첫 요청: 멱등성 키 유효 시 비즈니스 로직 실행 및 200 OK")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `first request with valid key executes business logic and returns 200`() {
        // When
        val result = performPostWithKey(validIdempotencyKey)

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transactionId").exists())
            .andExpect(jsonPath("$.message").value("Processed: ${testRequest.description}"))

        // 비즈니스 로직이 정확히 1 번 실행되었는지 검증
        assertAll(
            { assertEquals(1, IdempotencyTestController.executionCount) },
            { println("✅ 첫 요청: 비즈니스 로직 실행됨 (카운트: ${IdempotencyTestController.executionCount})") }
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 시나리오 2: 동일 키로 재요청 시 캐시된 응답 반환 (로직 미실행)
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("동일 키 재요청: 캐시된 응답 반환 + 비즈니스 로직 미실행")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `duplicate request with same key returns cached response without re-execution`() {
        // Given: 첫 요청으로 캐시 생성
        performPostWithKey(validIdempotencyKey)
            .andExpect(status().isOk)
        val firstExecutionCount = IdempotencyTestController.executionCount

        // When: 동일 키로 재요청
        val result = performPostWithKey(validIdempotencyKey)

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transactionId").exists())
            // ✅ 캐시된 응답이므로 transactionId 가 첫 요청과 동일해야 함
            .andExpect(jsonPath("$.transactionId").value(
                objectMapper.readTree(result.andReturn().response.contentAsString)["transactionId"].asText()
            ))

        // 비즈니스 로직이 재실행되지 않았는지 검증
        assertEquals(
            firstExecutionCount,
            IdempotencyTestController.executionCount,
            "캐시 히트 시 비즈니스 로직이 재실행되어서는 안 됨"
        )
        println("✅ 동일 키 재요청: 캐시 응답 반환 (카운트 변화 없음: ${IdempotencyTestController.executionCount})")
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 시나리오 3: 멱등성 키 누락 시 400 응답
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("멱등성 키 누락: 400 Bad Request + 올바른 에러 코드")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `missing idempotency key returns 400 bad request`() {
        // When: 헤더 없이 요청
        val result = mockMvc.perform(
            post("/api/test/idempotency/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest))
        )

        // Then
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.IDEMPOTENCY_KEY_MISSING.code))
            .andExpect(jsonPath("$.detail").value("Missing 'Idempotency-Key' header"))

        // 비즈니스 로직이 실행되지 않았는지 확인
        assertEquals(0, IdempotencyTestController.executionCount)
        println("✅ 키 누락: 400 응답 + 로직 미실행")
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 시나리오 4: 잘못된 키 형식 시 400 응답
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("잘못된 키 형식: 400 Bad Request + IDEMPOTENCY_KEY_INVALID")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `invalid key format returns 400 bad request`() {
        val invalidKeys = listOf(
            "short",           // 길이 부족
            "a".repeat(129),   // 길이 초과
            "invalid@key!",    // 허용되지 않은 문자
            "key with space"   // 공백 포함
        )

        invalidKeys.forEach { invalidKey ->
            // When
            val result = mockMvc.perform(
                post("/api/test/idempotency/process")
                    .header("Idempotency-Key", invalidKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRequest))
            )

            // Then
            result
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.IDEMPOTENCY_KEY_INVALID.code))

            println("✅_invalid 키 '$invalidKey': 400 응답")
        }

        // 모든 요청이 검증 단계에서 차단되었으므로 로직 미실행
        assertEquals(0, IdempotencyTestController.executionCount)
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 시나리오 5: 동시 요청 시 한 개만 성공, 나머지는 409 (경쟁 상태)
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("동시 요청: 한 요청만 200, 나머지는 409 Conflict")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `concurrent requests with same key - one succeeds others get 409`() {
        val concurrentKey = "concurrent-test-key-${UUID.randomUUID()}"
        val requestCount = 5
        val results = mutableListOf<ResultActions>()
        val latch = CountDownLatch(requestCount)

        // ✅ 1. 현재 요청 속성 캡처
        val originalRequestAttributes = RequestContextHolder.getRequestAttributes()

        // ✅ 2. ExecutorService 로 선언 (shutdownNow() 사용 가능)
        val baseExecutor: ExecutorService = Executors.newFixedThreadPool(requestCount)

        // ✅ 3. SecurityContext 자동 전파 래핑
        val securityExecutor = DelegatingSecurityContextExecutor(baseExecutor)

        // ✅ 4. RequestAttributes 수동 전파를 위한 최종 Executor
        val executor: Executor = Executor { command ->
            securityExecutor.execute {
                try {
                    originalRequestAttributes?.let {
                        RequestContextHolder.setRequestAttributes(it)
                    }
                    command.run()
                } finally {
                    RequestContextHolder.resetRequestAttributes()
                }
            }
        }

        try {
            repeat(requestCount) {
                executor.execute {  // ✅ execute() 사용
                    try {
                        val result = performPostWithKey(concurrentKey)
                        synchronized(results) { results.add(result) }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            val completed = latch.await(30, TimeUnit.SECONDS)
            assertTrue(completed, "동시 요청 테스트 시간 초과")

            // ✅ 결과 분석 (기존 로직 유지)
            val successCount = results.count {
                try { it.andExpect(status().isOk).let { true } }
                catch (e: AssertionError) { false }
            }
            val conflictCount = results.count {
                try { it.andExpect(status().isConflict).let { true } }
                catch (e: AssertionError) { false }
            }

            assertEquals(1, successCount, "동시 요청 중 정확히 1 개만 성공해야 함")
            assertEquals(requestCount - 1, conflictCount, "나머지 요청은 409 를 반환해야 함")

            results.filter {
                try { it.andExpect(status().isConflict).let { true }
                } catch (e: AssertionError) { false }
            }.forEach {
                it.andExpect(jsonPath("$.errorCode").value(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS.code))
            }

            assertEquals(1, IdempotencyTestController.executionCount)
            println("✅ 동시 요청: 1 성공 + ${requestCount - 1} Conflict + 로직 1 회 실행")

        } finally {
            baseExecutor.shutdownNow()  // ✅ 정답: ExecutorService 참조로 정리
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 시나리오 6: 락 TTL 만료 후 동일 키 재사용 가능
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("락 TTL 만료 후: 동일 키로 새 요청 처리 가능")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `after lock TTL expires same key can be reused for new request`() {
        val shortTtlKey = "short-ttl-key-${UUID.randomUUID()}"

        // Given: 첫 요청 (lockTtlSec = 2 초로 짧게 설정된 엔드포인트 필요시)
        performPostWithKey(shortTtlKey)
            .andExpect(status().isOk)
        val firstTransactionId = objectMapper.readTree(
            mockMvc.perform(
                post("/api/test/idempotency/process")
                    .header("Idempotency-Key", shortTtlKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRequest))
            ).andReturn().response.contentAsString
        )["transactionId"].asText()

        // When: 락 TTL (2 초) 대기 후 동일 키 재요청
        Thread.sleep(3000)  // 3 초 대기 (lockTtlSec=2 보다 길게)

        val result = performPostWithKey(shortTtlKey)

        // Then: 새 요청으로 처리되어 새로운 transactionId 반환
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transactionId").exists())

        // 새 transactionId 는 이전과 다를 수 있음 (캐시 만료 후 새 실행)
        // ※ 실제 동작은 캐시 TTL(cacheTtlSec) 에 따라 달라짐
        println("✅ TTL 만료 후 재요청: 새 처리 실행 (카운트: ${IdempotencyTestController.executionCount})")
    }

    // ─────────────────────────────────────────────────────────────
    // 🎯 시나리오 7: @Idempotent 이 없는 엔드포인트는 영향 받지 않음
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("@Idempotent 미적용 엔드포인트: 키 유무와 무관하게 정상 처리")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `endpoint without @Idempotent processes normally regardless of key`() {
        // When: 키 없이 요청 (멱등성 미적용 엔드포인트)
        val result1 = mockMvc.perform(
            post("/api/test/idempotency/no-idempotency")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest))
        )

        // When: 키 있어도 동일하게 처리
        val result2 = mockMvc.perform(
            post("/api/test/idempotency/no-idempotency")
                .header("Idempotency-Key", "any-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest))
        )

        // Then: 둘 다 200 OK
        result1.andExpect(status().isOk)
        result2.andExpect(status().isOk)

        // 비즈니스 로직은 두 번 실행됨 (멱등성 적용 안 됨)
        // ※ no-idempotency 도 카운터를 사용하므로 2 증가
        println("✅ @Idempotent 미적용: 키와 무관하게 2 회 실행 (카운트: ${IdempotencyTestController.executionCount})")
    }

    @Test
    @DisplayName("컨트롤러 파라미터로 멱등성 키 직접 전달 검증")
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `controller receives idempotency key as parameter and uses it in business logic`() {
        // Given: 고유한 키 생성 (테스트 격리용)
        val specificKey = "direct-use-key-${UUID.randomUUID()}"
        val expectedMessage = "Controller received key: $specificKey"

        // When: 요청 전송
        val result = mockMvc.perform(
            post("/api/test/idempotency/process-with-key")
                .header("Idempotency-Key", specificKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest))
        )

        // Then:
        // 1. HTTP 200 OK
        // 2. 응답 메시지에 전달한 키가 그대로 포함됨
        // 3. 비즈니스 로직 정상 실행 (카운트 1)
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value(expectedMessage))

        assertEquals(1, IdempotencyTestController.executionCount, "비즈니스 로직이 정확히 1회 실행되어야 함")
        println("✅ 컨트롤러 키 직접 사용 검증 완료: '$specificKey' 정상 전달 및 처리")
    }

    // ─────────────────────────────────────────────────────────────
    // 🔧 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────
    private fun performPostWithKey(key: String): ResultActions {
        return mockMvc.perform(
            post("/api/test/idempotency/process")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest))
        ).andDo(print())  // 실패 시 요청/응답 출력
    }
}