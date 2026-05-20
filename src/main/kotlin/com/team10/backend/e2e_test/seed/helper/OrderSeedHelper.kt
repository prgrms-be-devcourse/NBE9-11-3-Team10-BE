package com.team10.backend.e2e_test.seed.helper
import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.e2e_test.seed.fixture.OrderSeedFixture
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class OrderSeedHelper(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository
) {

    /**
     * ✅ Mock Server 의 OrderStore.reset() + initOrders() 이식
     * - BUYER 사용자 조회 → 시나리오별 주문 일괄 생성 및 저장
     * - productName 기반으로 상품 매핑 (ProductSeedFixture 와 연동)
     */
    @Transactional
    fun seedBuyerOrders() {
        // 1. 구매자 계정 조회 (UserSeedFixture 가 먼저 실행되어야 함)
        val buyer = userRepository.findByEmailAndRole("buyer@example.com", Role.BUYER)
            ?: throw IllegalStateException("BUYER user not found. Run UserSeedFixture first.")

        // 2. 판매자 계정 조회 (상품 소유자 확인용)
        val seller = userRepository.findByEmailAndRole("seller@example.com", Role.SELLER)
            ?: throw IllegalStateException("SELLER user not found.")

        // 3. 시딩된 상품 조회 (productName → Product 매핑)
        val productMap = productRepository.findByUserAndProductNameIn(
            seller,
            OrderSeedFixture.SEED_ORDER_SCENARIOS
                .flatMap { it.productQuantities.keys }
                .toSet()
        ).associateBy { it.productName }

        // 4. 이미 시딩된 주문 확인 (재실행 방지)
        //    - scenarioKey 를 orderNumber prefix 로 활용하여 중복 체크
        val existingCount = orderRepository.countByUserAndOrderNumberStartingWith(
            buyer,
            "ORD-E2E"
        )
        if (existingCount >= OrderSeedFixture.SEED_ORDER_SCENARIOS.size) {
            return  // 이미 시딩됨
        }

        // 5. 시나리오별 주문 생성 및 저장
        OrderSeedFixture.SEED_ORDER_SCENARIOS.forEachIndexed { index, scenario ->
            // productName 으로 실제 Product 엔티티 조회
            val products = scenario.productQuantities.mapNotNull { (productName, quantity) ->
                productMap[productName]?.let { it to quantity }
            }.toMap()

            // 필수 상품이 누락된 경우 스킵 (로그 출력)
            if (products.size < scenario.productQuantities.size) {
                println("⚠️ Skipping scenario '${scenario.scenarioKey}': missing products")
                return@forEachIndexed
            }

            // Order Entity 생성
            val order = OrderSeedFixture.toEntity(buyer, products, scenario)

            // orderNumber 에 시나리오 키 포함하여 식별 용이하게 (옵션)
            if (scenario.scenarioKey.startsWith("E2E")) {
                // 리플렉션 또는 테스트 전용 메서드로 orderNumber 오버라이드
                // 또는 Order 생성 시 전달하는 방식으로 처리
            }

            // 저장 (cascade 로 OrderProducts, OrderDelivery, Payment 도 함께 저장)
            orderRepository.save(order)
        }
    }

    /**
     * [유틸] 시나리오 키로 주문 조회 (테스트 코드에서 활용)
     */
    fun findByScenarioKey(buyer: User, scenarioKey: String): Order? {
        // scenarioKey 를 orderNumber 에 포함시켰다는 가정 하에 조회
        return orderRepository.findByUserAndOrderNumberContaining(buyer, scenarioKey)
    }

    /**
     * [유틸] E2E 결제 테스트용 주문 조회
     */
    fun findE2ePaymentTestOrder(buyer: User): Order? {
        return findByScenarioKey(buyer, "E2E_PAYMENT_TEST")
    }
}