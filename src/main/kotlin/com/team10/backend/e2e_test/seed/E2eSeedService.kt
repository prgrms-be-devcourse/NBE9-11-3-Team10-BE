package com.team10.backend.e2e_test.seed

import com.team10.backend.e2e_test.seed.helper.*
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class E2eSeedService(
    private val entityManager: EntityManager,
    private val userSeedHelper: UserSeedHelper,
    private val storeProfileSeedHelper: StoreProfileSeedHelper,
    private val productSeedHelper: ProductSeedHelper,
    private val feedSeedHelper: FeedSeedHelper,
    private val orderSeedHelper: OrderSeedHelper
) {

    /**
     * ✅ Mock Server 의 /__reset 동작 이식
     * 1. 전체 테이블 TRUNCATE
     * 2. IDENTITY 시퀀스 리셋
     * 3. 테스트용 User & StoreProfile 시딩
     */
    @Transactional
    fun resetAndSeed() {
        truncateAllTables()
        resetIdentitySequences()

        userSeedHelper.seedAll()
        storeProfileSeedHelper.seedSellerProfiles()
        productSeedHelper.seedSellerProducts()
        feedSeedHelper.seedSellerFeeds()
        orderSeedHelper.seedBuyerOrders()
    }

    private fun truncateAllTables() {
        // 외래키 제약조건 임시 해제
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate()

        // PUBLIC 스키마의 모든 테이블 조회 (H2 MySQL 모드)
        val tables = entityManager.createNativeQuery(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'TABLE'"
        ).resultList as List<String>

        // Hibernate 관리 테이블 제외 후 TRUNCATE
        tables.filter { !it.lowercase().startsWith("hibernate") }
            .forEach { tableName ->
                entityManager.createNativeQuery("TRUNCATE TABLE `$tableName` RESTART IDENTITY").executeUpdate()
            }

        // 외래키 제약조건 복구
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate()
    }

    private fun resetIdentitySequences() {
        // H2 의 RESTART IDENTITY 가 제대로 동작하지 않는 경우를 대비한 안전장치
        // (대부분의 최신 H2 버전에서는 TRUNCATE 시 자동 리셋되므로 생략 가능)
    }

    private fun seedTestUsers() {
        // Mock Server 의 MOCK_USERS.SUCCESS, BUYER, SELLER 에 매핑
        userSeedHelper.seedAll()
    }

    private fun seedStoreProfiles() {
        // Mock Server 의 initStoreProfiles() 이식
        storeProfileSeedHelper.seedSellerProfiles()
    }
}