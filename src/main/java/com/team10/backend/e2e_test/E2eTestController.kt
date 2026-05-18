package com.team10.backend.e2e_test

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")  // ✅ 이 플래그가 있을 때만 로드
class E2eTestController(
    private val entityManager: EntityManager
) {

    /**
     * ✅ E2E 테스트 시작 전 호출: 모든 데이터 초기화 + 시드 삽입
     */
    @PostMapping("/reset")
    @Transactional
    fun resetDatabase(): ResponseEntity<Map<String, String>> {
        // 외래키 제약조건 임시 해제 (H2/MySQL 호환)
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate()

        // 모든 테이블 트런케이트 (데이터만 삭제)
        entityManager.createNativeQuery(
            """
            SELECT CONCAT('TRUNCATE TABLE `', table_name, '`;')
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
            """.trimIndent()
        ).resultList.forEach { sql ->
            entityManager.createNativeQuery(sql as String).executeUpdate()
        }

        // 외래키 제약조건 복구
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate()

        // ✅ 기본 시드 데이터 삽입 (필요시)
//        seedMinimumData()

        return ResponseEntity.ok(mapOf("status" to "reset-complete"))
    }

    /**
     * ✅ 테스트용 더미 사용자 생성 (선택사항)

    @PostMapping("/seed/user")
    @Transactional
    fun seedTestUser(@RequestBody request: SeedUserRequest): ResponseEntity<Map<String, Any>> {
        // DataFaker 등으로 테스트 사용자 생성 로직
        // ...
        return ResponseEntity.ok(mapOf("userId" to 99999L, "email" to request.email))
    }

    private fun seedMinimumData() {
        // 테스트에 필수적인 마스터 데이터만 삽입
        // 예: 카테고리, 설정값, 기본 권한 등
    }
     */
}