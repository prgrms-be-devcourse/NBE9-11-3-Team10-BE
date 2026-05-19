package com.team10.backend.global.idempotency.testsupport

import com.team10.backend.global.idempotency.Idempotent
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@RestController
@RequestMapping("/api/test/idempotency")
class IdempotencyTestController {

    // 실행 횟수를 추적하기 위한 카운터 (테스트용)
    companion object {
        private val _executionCount = AtomicInteger(0)

        val executionCount: Int
            get() = _executionCount.get()

        fun resetCounter() {
            _executionCount.set(0)
        }

        fun incrementCount() {
            _executionCount.incrementAndGet()  // ✅ 원자적 증가
        }
    }

    @PostMapping("/process")
    @Idempotent(lockTtlSec = 2, cacheTtlSec = 60)  // ← 멱등성 적용
    fun processWithIdempotency(
        @RequestBody request: IdempotencyTestRequest
    ): ResponseEntity<IdempotencyTestResponse> {

        // ✅ 비즈니스 로직이 실제로 실행되었는지 추적하기 위해 카운트 증가
        incrementCount()

        // 가짜 비즈니스 로직 (약간의 지연 시뮬레이션)
        Thread.sleep(50)

        val response = IdempotencyTestResponse(
            transactionId = "TXN-${UUID.randomUUID()}",
            processedAt = LocalDateTime.now(),
            message = "Processed: ${request.description}"
        )

        return ResponseEntity.ok(response)
    }

    @PostMapping("/no-idempotency")
    fun processWithoutIdempotency(
        @RequestBody request: IdempotencyTestRequest
    ): ResponseEntity<IdempotencyTestResponse> {
        // 멱등성 미적용 엔드포인트 (비교용)
        incrementCount()

        val response = IdempotencyTestResponse(
            transactionId = "TXN-${UUID.randomUUID()}",
            processedAt = LocalDateTime.now(),
            message = "No idempotency: ${request.description}"
        )

        return ResponseEntity.ok(response)
    }

    // ✅ 신규: 컨트롤러에서 멱등성 키를 직접 파라미터로 받는 엔드포인트
    @PostMapping("/process-with-key")
    @Idempotent(lockTtlSec = 2, cacheTtlSec = 60)
    fun processWithKeyInController(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: IdempotencyTestRequest
    ): ResponseEntity<IdempotencyTestResponse> {

        incrementCount()

        // 비즈니스 로직에서 키를 직접 활용하는 시뮬레이션
        val response = IdempotencyTestResponse(
            transactionId = "TXN-${UUID.randomUUID()}",
            processedAt = LocalDateTime.now(),
            message = "Controller received key: $idempotencyKey"
        )

        return ResponseEntity.ok(response)
    }
}