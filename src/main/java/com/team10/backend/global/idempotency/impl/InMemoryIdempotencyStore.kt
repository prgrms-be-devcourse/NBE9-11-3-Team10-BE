package global.idempotency.impl

import com.team10.backend.global.idempotency.IdempotencyStatus
import com.team10.backend.global.idempotency.IdempotencyStore
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["idempotency.store"], havingValue = "memory", matchIfMissing = true)
class InMemoryIdempotencyStore : IdempotencyStore {
    private data class Entry(
        val status: IdempotencyStatus,
        val response: String? = null,
        val expiresAt: Instant
    )

    private val MAX_KEYS = 100_000
    private val log = LoggerFactory.getLogger(javaClass)

    private val store = ConcurrentHashMap<String, Entry>()
    private val cleaner = Executors.newSingleThreadScheduledExecutor()

    init {
        // 1분 주기로 만료 키 정리
        cleaner.scheduleAtFixedRate(::cleanExpired, 1, 5, TimeUnit.MINUTES)
    }

    override fun checkAndLock(key: String, lockTtlSec: Int): IdempotencyStatus {
        if (store.size >= MAX_KEYS) {
            // 긴급 정리 또는 폴백 로직 (예: 로그 기록 후 LOCKED 반환)
            log.warn("Idempotency store reached max size: $MAX_KEYS. Cleaning expired keys.")
            cleanExpired()
        }

        val now = Instant.now()

        val entry = store.compute(key) { _, existing ->
            if (existing != null && existing.expiresAt.isAfter(now)) {
                existing
            } else {
                Entry(IdempotencyStatus.LOCKED, null, now.plusSeconds(lockTtlSec.toLong()))
            }
        }

        return checkNotNull(entry).status
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