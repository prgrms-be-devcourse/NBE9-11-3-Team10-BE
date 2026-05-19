package com.team10.backend.global.idempotency.unittest

import com.team10.backend.global.idempotency.IdempotencyStatus
import global.idempotency.impl.InMemoryIdempotencyStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InMemoryIdempotencyStoreTest {

    private lateinit var store: InMemoryIdempotencyStore

    @BeforeEach
    fun setUp() {
        store = InMemoryIdempotencyStore()
    }

    @Test
    fun `첫 요청은 NONE 을 반환하여 실행을 허용해야 한다`() {
        val status = store.checkAndLock("test-key", lockTtlSec = 10)

        Assertions.assertThat(status).isEqualTo(IdempotencyStatus.NONE)
    }

    @Test
    fun `처리 중 동일 키 요청은 LOCKED 를 반환해야 한다`() {
        // 첫 요청: 락 획득
        store.checkAndLock("key", lockTtlSec = 10)

        // 동시 요청: 경쟁 상태
        val status = store.checkAndLock("key", lockTtlSec = 10)

        Assertions.assertThat(status).isEqualTo(IdempotencyStatus.LOCKED)
    }

    @Test
    fun `완료된 키는 캐시 응답을 위해 COMPLETED 를 반환해야 한다`() {
        store.checkAndLock("key", lockTtlSec = 10)  // 락 획득
        store.complete("key", """{"result":"ok"}""", cacheTtlSec = 60)  // 완료 처리

        // 캐시 조회용 호출 (실제 구현에서는 별도 메서드 사용)
        val response = store.getResponse("key")

        Assertions.assertThat(response).isEqualTo("""{"result":"ok"}""")
    }

    @Test
    fun `동시 요청 시 한 요청만 NONE 을 반환하고 나머지는 LOCKED 를 반환한다`() {
        // Given
        val requestCount = 3
        val latch = CountDownLatch(requestCount)
        val results = mutableListOf<IdempotencyStatus>()
        val lock = Any()  // synchronized 를 위한 락 객체

        // When: 3 개의 요청을 비동기로 동시 실행
        (1..requestCount).forEach {
            CompletableFuture.runAsync {
                val status = store.checkAndLock("key-concurrent", lockTtlSec = 10)

                // 스레드 안전한 결과 수집
                synchronized(lock) {
                    results.add(status)
                }
                latch.countDown()
            }
        }

        // 모든 스레드 완료 대기 (타임아웃 설정으로 데드락 방지)
        val completed = latch.await(5, TimeUnit.SECONDS)
        org.junit.jupiter.api.Assertions.assertTrue(completed, "테스트 시간 초과: 모든 요청이 완료되지 않음")

        // Then: 정확히 1 개는 NONE (락 획득), 나머지는 LOCKED (경쟁 실패)
        val noneCount = results.count { it == IdempotencyStatus.NONE }
        val lockedCount = results.count { it == IdempotencyStatus.LOCKED }

        org.junit.jupiter.api.Assertions.assertEquals(1, noneCount, "락은 정확히 1 개의 요청만 획득해야 합니다")
        org.junit.jupiter.api.Assertions.assertEquals(requestCount - 1, lockedCount, "나머지 요청은 LOCKED 상태를 반환해야 합니다")

        // 추가 검증: COMPLETED 상태는 이 단계에서 나오면 안 됨
        org.junit.jupiter.api.Assertions.assertTrue(
            results.none { it == IdempotencyStatus.COMPLETED },
            "checkAndLock 단계에서는 아직 COMPLETED 상태가 나올 수 없습니다"
        )
    }

    @Test
    fun `순차적 요청은 첫 요청 실행 후 두 번째 요청은 캐시 반환`() {
        // 첫 요청: 락 획득
        val first = store.checkAndLock("seq-key", lockTtlSec = 10)
        org.junit.jupiter.api.Assertions.assertEquals(IdempotencyStatus.NONE, first)

        // 완료 처리 시뮬레이션
        store.complete("seq-key", """{"data":"ok"}""", cacheTtlSec = 60)

        // 두 번째 요청: 이미 완료된 키이므로 캐시 조회 가능
        // (실제 사용에서는 getResponse() 로 응답을 꺼내씀)
        val cached = store.getResponse("seq-key")
        org.junit.jupiter.api.Assertions.assertEquals("""{"data":"ok"}""", cached)
    }

    @Test
    fun `락 TTL 만료 후 동일 키로 재진입 가능`() {
        // 첫 요청: 락 획득 (TTL 1 초)
        val first = store.checkAndLock("ttl-key", lockTtlSec = 1)
        org.junit.jupiter.api.Assertions.assertEquals(IdempotencyStatus.NONE, first)

        // TTL 대기
        Thread.sleep(1100)

        // 만료 후 동일 키 재요청: 새 락 획득 가능
        val second = store.checkAndLock("ttl-key", lockTtlSec = 10)
        org.junit.jupiter.api.Assertions.assertEquals(IdempotencyStatus.NONE, second)
    }

    @Test
    fun `비즈니스 예외 발생 후 release 호출 시 재진입 가능`() {
        // 첫 요청: 락 획득
        store.checkAndLock("error-key", lockTtlSec = 10)

        // 에러 시뮬레이션: 명시적 해제
        store.release("error-key")

        // 재시도: 새 락 획득 가능
        val retry = store.checkAndLock("error-key", lockTtlSec = 10)
        org.junit.jupiter.api.Assertions.assertEquals(IdempotencyStatus.NONE, retry)
    }

    @Test
    fun `COMPLETED 상태일 때 getResponse가 올바른 JSON을 반환한다`() {
        val key = "key-cache"
        store.checkAndLock(key, 10)
        store.complete(key, """{"orderId":"123","status":"PAID"}""", 60)

        val response = store.getResponse(key)
        org.junit.jupiter.api.Assertions.assertNotNull(response)
        org.junit.jupiter.api.Assertions.assertEquals("""{"orderId":"123","status":"PAID"}""", response)
    }

    @Test
    fun `LOCKED 상태일 때 getResponse는 null을 반환한다`() {
        val key = "key-lock"
        store.checkAndLock(key, 10)
        org.junit.jupiter.api.Assertions.assertNull(store.getResponse(key))
    }
}