package com.team10.backend.global.idempotency

import global.idempotency.impl.InMemoryIdempotencyStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class InMemoryIdempotencyStoreTest {

    private lateinit var store: InMemoryIdempotencyStore

    @BeforeEach
    fun setUp() {
        store = InMemoryIdempotencyStore()
    }

    @Test
    fun `첫 요청은 LOCKED 상태를 반환한다`() {
        val status = store.checkAndLock("key-1", lockTtlSec = 10)
        assertEquals(IdempotencyStatus.LOCKED, status)
    }

    @Test
    fun `동시 요청 시 후발 요청도 기존 상태를 유지한다`() {
        val latch = CountDownLatch(3)
        val results = mutableListOf<IdempotencyStatus>()

        (1..3).forEach {
            CompletableFuture.runAsync {
                val s = store.checkAndLock("key-concurrent", lockTtlSec = 10)
                synchronized(results) { results.add(s) }
                latch.countDown()
            }
        }
        latch.await()

        // 모두 LOCKED여야 함 (선점된 키는 상태를 그대로 반환)
        assertTrue(results.all { it == IdempotencyStatus.LOCKED })
    }

    @Test
    fun `complete 호출 시 상태가 COMPLETED로 변경되고 응답이 저장된다`() {
        store.checkAndLock("key-complete", 10)
        val success = store.complete("key-complete", """{"id":1}""", 60)

        assertTrue(success)
        val status = store.checkAndLock("key-complete", 10) // 재조회
        assertEquals(IdempotencyStatus.COMPLETED, status)
    }

    @Test
    fun `TTL 경과 후 정리되면 새 요청으로 간주된다`() {
        val key = "key-ttl"
        store.checkAndLock(key, lockTtlSec = 1) // 1초 TTL

        // TTL보다 약간 더 대기
        Thread.sleep(1100)
        store.callCleaner() // 수동 호출 또는 내부 스케줄러 대기

        val status = store.checkAndLock(key, lockTtlSec = 10)
        assertEquals(IdempotencyStatus.LOCKED, status) // 만료 후 재락
    }

    @Test
    fun `release 호출 시 키가 즉시 삭제된다`() {
        val key = "key-release"
        store.checkAndLock(key, 10)
        store.release(key)

        val status = store.checkAndLock(key, 10)
        assertEquals(IdempotencyStatus.LOCKED, status) // 새 락 생성됨
    }
}