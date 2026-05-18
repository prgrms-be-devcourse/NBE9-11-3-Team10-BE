package com.team10.backend.e2e_test.seed.fixture

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.enums.RequestType
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.user.entity.User
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * ✅ Mock Server 의 mock-order-data.ts 에 정의된 주문 데이터 매핑
 * - E2E 테스트용 고정 시나리오 (재현성 보장)
 * - orderNumber 는 UUID 기반 유니크 값으로 자동 생성
 */
object OrderSeedFixture {

    data class SeedOrderScenario(
        val scenarioKey: String,           // 테스트 코드에서 식별용 키
        val orderStatus: OrderStatus,      // 주문 상태 (PENDING/PAID/SHIPPED/COMPLETED)
        val paymentStatus: PaymentStatus?, // 결제 상태 (옵션)
        val productQuantities: Map<String, Int>, // productName → quantity 매핑
        val deliveryAddress: String,
        val orderNumber: String? = null,   // null 시 자동 생성
        val createdAt: LocalDateTime = LocalDateTime.now(),
        val description: String = ""
    )

    enum class OrderStatus {
        PENDING,    // 주문 생성 직후, 결제 전
        PAID,       // 결제 완료, 배송 준비 전
        SHIPPED,    // 배송 중
        COMPLETED,  // 배송 완료
        CANCELED    // 주문 취소
    }

    // Mock Server 의 initOrders() 와 매핑되는 시나리오 리스트
    // ⚠️ productName 은 실제 ProductSeedFixture.SEED_PRODUCTS 의 productName 과 일치해야 함
    val SEED_ORDER_SCENARIOS: List<SeedOrderScenario> = listOf(
        // ✅ [E2E 전용] 결제 테스트용 상품 단일 주문 (가장 기본 시나리오)
        SeedOrderScenario(
            scenarioKey = "E2E_PAYMENT_TEST",
            orderStatus = OrderStatus.PAID,
            paymentStatus = PaymentStatus.PAID,
            productQuantities = mapOf("[E2E-TEST] 결제 테스트 상품" to 1),
            deliveryAddress = "서울시 강남구 테헤란로 123 202호",
            description = "자동화된 결제 테스트를 위한 주문"
        ),
        // ✅ 일반 상품 다중 주문 (장바구니 시나리오)
        SeedOrderScenario(
            scenarioKey = "MULTI_PRODUCT_ORDER",
            orderStatus = OrderStatus.SHIPPED,
            paymentStatus = PaymentStatus.PAID,
            productQuantities = mapOf(
                "스프링 입문" to 2,
                "ABC" to 1
            ),
            deliveryAddress = "경기 성남시 분당구 판교역로 166 1102동 304호",
            description = "다중 상품 주문 테스트"
        ),
        // ✅ 품절 상품 포함 주문 (에지 케이스)
        SeedOrderScenario(
            scenarioKey = "SOLD_OUT_PRODUCT_ORDER",
            orderStatus = OrderStatus.PENDING,
            paymentStatus = PaymentStatus.READY,
            productQuantities = mapOf("품절된 상품" to 1),
            deliveryAddress = "서울시 강남구 테헤란로 123 202호",
            description = "품절 상품 주문 시나리오"
        ),
        // ✅ 취소된 주문 (CANCELED 시나리오)
        SeedOrderScenario(
            scenarioKey = "CANCELED_ORDER",
            orderStatus = OrderStatus.CANCELED,
            paymentStatus = PaymentStatus.CANCELED,
            productQuantities = mapOf("ABC" to 1),
            deliveryAddress = "서울시 강남구 테헤란로 123 202호",
            description = "주문 취소 테스트 시나리오"
        )
    )

    /**
     * SeedOrderScenario → 실제 Order Entity 트리로 변환
     * - Order.createOrder() 팩토리 메서드 사용 (도메인 로직 존중)
     * - 양방향 관계, Cascade 설정 자동 적용
     */
    fun toEntity(
        buyer: User,
        products: Map<Product, Int>,  // Product → quantity 매핑
        scenario: SeedOrderScenario
    ): Order {
        // 1. OrderProducts 생성 (주문 시점 가격 스냅샷)
        val orderProducts = products.map { (product, quantity) ->
            OrderProducts.builder()
                .product(product)
                .quantity(quantity)
                .orderPrice(product.price) // 주문 시 가격 고정
                .build()
        }

        // 2. OrderDelivery 생성
        val delivery = OrderDeliveryFixture.createReady(scenario.deliveryAddress)

        // 3. Order 생성 (도메인 팩토리 메서드 사용)
        val orderNumber = scenario.orderNumber ?: generateUniqueOrderNumber()
        val order = Order.createOrder(
            user = buyer,
            orderNumber = orderNumber,
            delivery = delivery,
            items = orderProducts
        )

        // 4. createdAt 오버라이드 (테스트 재현성 위해)
        //    (BaseEntity 의 @CreatedDate 는 보통 자동 설정되므로, 테스트용이라면 별도 처리 필요)
        //    필요시 리플렉션 또는 테스트 전용 setter 활용

        // 5. 주문 상태 전이 (시나리오 기반)
        applyOrderStatus(order, scenario.orderStatus)

        // 6. Payment 생성 (결제 상태가 있는 경우)
        scenario.paymentStatus?.let { paymentStatus ->
            val totalAmount = orderProducts.sumOf { it.orderPrice * it.quantity }
            val payment = Payment.builder()
                .order(order)
                .orderNumber(orderNumber)
                .totalAmount(totalAmount)
                .status(paymentStatus)
                .type(RequestType.PAYMENT)
                .build()
            order.addPayment(payment) // 양방향 관계 설정
        }

        return order
    }

    /**
     * 주문 상태별 도메인 메서드 호출
     */
    private fun applyOrderStatus(order: Order, status: OrderStatus) {
        when (status) {
            OrderStatus.PAID -> {
                // 결제 완료 → 배송 준비 상태로 전이
                order.delivery?.startReady()
            }
            OrderStatus.SHIPPED -> {
                order.delivery?.startReady()
                order.delivery?.updateTracking(OrderDeliveryFixture.generateTrackingNumber())
            }
            OrderStatus.COMPLETED -> {
                order.delivery?.startReady()
                order.delivery?.updateTracking(OrderDeliveryFixture.generateTrackingNumber())
                // 엔티티에 completed() 메서드가 있다면 호출
            }
            OrderStatus.CANCELED -> {
                order.cancelStatusOrder() // 도메인 메서드 호출
            }
            OrderStatus.PENDING -> {
                // 기본 상태: 추가 액션 불필요
            }
        }
    }

    /**
     * 유니크한 orderNumber 생성 (UUID + 타임스탬프 기반)
     */
    fun generateUniqueOrderNumber(): String {
        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        )
        val uuid = UUID.randomUUID().toString().take(8)
        return "ORD-$timestamp-$uuid"
    }
}