package com.team10.backend.e2e_test.seed.helper

import com.team10.backend.domain.user.entity.SellerInfo
import com.team10.backend.domain.user.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class StoreProfileSeedHelper(
    private val userRepository: UserRepository
) {

    /**
     * ✅ Mock Server 의 initStoreProfiles() 이식
     * - SELLER 사용자에 SellerInfo(프로필) 연결
     * - 통계치(stats)는 추후 도메인 로직 또는 집계 리로 관리 권장
     */
    @Transactional
    fun seedSellerProfiles() {
        val seller = userRepository.findByEmail("seller@example.com") ?: return

        // 이미 프로필이 연결되어 있으면 스
        if (seller.sellerInfo != null) return

        val sellerInfo = SellerInfo().apply {
            updateSellerInfo(
                bio = "좋은 책을 소개하는 스토어입니다.",
                businessNumber = "123-45-67890"
            )
        }

        // User 엔티티의 양방향 관계 설정 메서드 사용
        seller.attachSellerInfo(sellerInfo)
        userRepository.save(seller) // cascade 또는 명시적 저장
    }
}