package com.team10.backend.global.idempotency

interface IdempotencyStore {
    /**
     * 키 검사 및 LOCK 설정
     * @return 현재 상태
     */
    fun checkAndLock(key: String, lockTtlSec: Int): IdempotencyStatus

    /**
     * 처리 완료 후 응답 저장 및 TTL 연장
     * @return 상태 변경 성공 여부 (경쟁 시 false 반환)
     */
    fun complete(key: String, response: String, cacheTtlSec: Int): Boolean

    /**
     * 수동 해제 (타임아웃/취소/에러 시)
     */
    fun release(key: String)
}