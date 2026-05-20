package global.idempotency.impl

import com.team10.backend.global.idempotency.IdempotencyStatus
import com.team10.backend.global.idempotency.IdempotencyStore
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryIdempotencyStore : IdempotencyStore {
    private data class Entry(
        val status: IdempotencyStatus,
        val response: String? = null,
        val expiresAt: Instant
    )

    private val store = ConcurrentHashMap<String, Entry>()
    private val cleaner = Executors.newSingleThreadScheduledExecutor()

    companion object {
        private val log = LoggerFactory.getLogger(InMemoryIdempotencyStore::class.java)
    }

    init {
        // 5분 주기로 만료 키 정리
        cleaner.scheduleAtFixedRate(::cleanExpired, 5, 5, TimeUnit.MINUTES)
    }

    override fun checkAndLock(key: String, lockTtlSec: Int): IdempotencyStatus {
        val now = Instant.now()

        var acquiredLock = false

        val entry = store.compute(key) { _, existing ->
            when {
                // 만료된 엔트리는 무시
                existing == null || existing.expiresAt.isBefore(now) -> {
                    acquiredLock = true  // ✅ 락 획득 플래그 설정
                    Entry(
                        status = IdempotencyStatus.LOCKED,  // 내부용: 동시성 차단
                        expiresAt = now.plusSeconds(lockTtlSec.toLong())
                    )
                }
                else -> existing  // 기존 상태 유지
            }
        }

        // ✅ 락을 새로 획득했으면 진행 허용 (NONE), 아니면 기존 상태 반환
        return if (acquiredLock) {
            IdempotencyStatus.NONE
        } else {
            checkNotNull(entry).status
        }
    }

    override fun complete(key: String, response: String, cacheTtlSec: Int): Boolean {
        val now = Instant.now()
        val updated = store.computeIfPresent(key) { _, existing ->
            if (existing.status == IdempotencyStatus.LOCKED) {
                Entry(IdempotencyStatus.COMPLETED, response, now.plusSeconds(cacheTtlSec.toLong()))
            } else existing
        }
        return updated?.status == IdempotencyStatus.COMPLETED
    }

    override fun getResponse(key: String): String? {
        val now = Instant.now()
        val entry = store[key] ?: return null

        // 만료되지 않았고 COMPLETED 상태일 때만 응답 반환
        return if (entry.expiresAt.isAfter(now) && entry.status == IdempotencyStatus.COMPLETED) {
            entry.response
        } else null
    }

    override fun release(key: String) {
        store.remove(key)
    }

    private fun cleanExpired() {
        val now = Instant.now()
        store.entries.removeIf { (_, entry) -> entry.expiresAt.isBefore(now) }
    }

    internal fun callCleaner() {
        // TEST 전용 메소드, 운영 환경 사용 금지
        this.cleanExpired()
    }

    @PreDestroy
    fun shutdown() {
        cleaner.shutdownNow()
    }
}