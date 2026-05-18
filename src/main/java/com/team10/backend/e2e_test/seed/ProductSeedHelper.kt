package com.team10.backend.e2e_test.seed
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class ProductSeedHelper(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository
) {

    /**
     * ✅ Mock Server 의 ProductStore.reset() 이식
     * - SELLER 사용자 조회 → 시드 제품 일괄 저장
     * - 이미 존재하면 중복 저장 방지 (이름 기반 체크)
     */
    @Transactional
    fun seedSellerProducts() {
        // 1. 판매자 계정 조회 (UserSeedFixture 가 먼저 실행되어야 함)
        val seller = userRepository.findByEmailAndRole("seller@example.com", Role.SELLER)
            ?: throw IllegalStateException("SELLER user not found. Run UserSeedFixture first.")

        // 2. 이미 시딩된 제품 확인 (재실행 방지)
        val existingCount = productRepository.countByUserAndProductNameIn(
            seller,
            ProductSeedFixture.SEED_PRODUCTS.map { it.productName }
        )
        if (existingCount >= ProductSeedFixture.SEED_PRODUCTS.size) {
            return  // 이미 시딩됨
        }

        // 3. 제품 일괄 생성 및 저장
        val products = ProductSeedFixture.SEED_PRODUCTS.map { data ->
            ProductSeedFixture.toEntity(seller, data)
        }
        productRepository.saveAll(products)
    }

    /**
     * [유틸] 특정 상품명으로 제품 조회 (테스트 코드에서 활용)
     */
    fun findByProductName(name: String): Product? {
        return productRepository.findByProductName(name)
    }

    /**
     * [유틸] E2E 테스트 전용 상품 조회 (결제 시나리오용)
     */
    fun findE2eTestProduct(): Product? {
        return findByProductName("[E2E-TEST] 결제 테스트 상품")
    }
}