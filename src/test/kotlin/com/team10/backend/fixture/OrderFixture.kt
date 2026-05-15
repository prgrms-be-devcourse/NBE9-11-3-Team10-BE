package com.team10.backend.fixture

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderDelivery
import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import net.datafaker.Faker
import java.time.LocalDateTime
import java.util.*

object OrderFixture {
    private val faker = Faker()
    private const val ORDER_NUMBER_PREFIX = "ORD"

    // ─────────────────────────────────────────────
    // ✅ Order 생성 헬퍼 (핵심: createOrder 정적 메서드 활용)
    // ─────────────────────────────────────────────

    /**
     * [기본] 주문 생성 팩토리
     * - 총액 자동 계산, 양방향 관계 설정, 초기 Payment(READY) 자동 포함
     * - orderNumber 는 UUID 기반 유니크 값 생성
     */
    fun create(
        user: User = UserFixture.create(role = Role.BUYER),
        orderNumber: String = generateUniqueOrderNumber(),
        delivery: OrderDelivery = OrderDeliveryFixture.create(),
        products: List<Pair<Product, Int>> = listOf(ProductFixture.createSelling() to 1) // (Product, quantity)
    ): Order {
        val orderProducts = products.map { (product, quantity) ->
            OrderProducts.builder()
                .product(product)
                .quantity(quantity)
                .orderPrice(product.price) // 주문 시점 가격 스냅샷
                .build()
        }
        return Order.createOrder(user, orderNumber, delivery, orderProducts)
    }

    /**
     * [시나리오] 주문 성공 상태 (SUCCESS) 로 변경된 주문
     */
    fun createSuccess(
        user: User = UserFixture.create(role = Role.BUYER),
        orderNumber: String = generateUniqueOrderNumber(),
        delivery: OrderDelivery = OrderDeliveryFixture.create(),
        products: List<Pair<Product, Int>> = listOf(ProductFixture.createSelling() to 1)
    ): Order {
        val order = create(user, orderNumber, delivery, products)
        order.successStatusOrder()
        return order
    }

    /**
     * [시나리오] 주문 취소 상태 (CANCELED) 로 변경된 주문
     * - 결제 미완료 상태에서 취소 시나리오
     */
    fun createCanceled(
        user: User = UserFixture.create(role = Role.BUYER),
        orderNumber: String = generateUniqueOrderNumber(),
        delivery: OrderDelivery = OrderDeliveryFixture.create(),
        products: List<Pair<Product, Int>> = listOf(ProductFixture.createSelling() to 1)
    ): Order {
        val order = create(user, orderNumber, delivery, products)
        order.cancelStatusOrder()
        return order
    }

    /**
     * [시나리오] 소프트 삭제된 주문 (@SQLDelete/@SQLRestriction 테스트용)

    fun createDeleted(
        user: User = UserFixture.create(role = Role.BUYER),
        orderNumber: String = generateUniqueOrderNumber()
    ): Order {
        val order = create(user, orderNumber)
        // 엔티티에 isDeleted setter 가 없다면 리플렉션 또는 테스트 전용 메서드 필요
        // 현재 구조라면 @MockBean 으로 Repository 레벨에서 테스트하거나,
        // 실제 DB 에서 @SQLDelete 가 동작하는지 통합 테스트로 검증 권장
        return order
    }
     */

    // ─────────────────────────────────────────────
    // 🔁 유틸리티 메서드
    // ─────────────────────────────────────────────

    /**
     * 유니크한 orderNumber 생성 (UUID + 타임스탬프 기반)
     * - DB unique 제약조건 충돌 방지
     */
    fun generateUniqueOrderNumber(): String {
        val timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val uuid = UUID.randomUUID().toString().take(8)
        return "$ORDER_NUMBER_PREFIX-$timestamp-$uuid"
    }

    /**
     * 주문 상품 항목 생성 헬퍼
     * - orderPrice 를 주문 시점 가격으로 고정 (상품 가격 변동 시 테스트 격리)
     */
    fun createOrderProducts(
        product: Product = ProductFixture.createSelling(),
        quantity: Int = faker.number().numberBetween(1, 5),
        orderPrice: Int = product.price // 기본값은 상품 현재 가격
    ): OrderProducts {
        return OrderProducts.builder()
            .product(product)
            .quantity(quantity)
            .orderPrice(orderPrice)
            .build()
    }

    /**
     * 다중 상품을 포함한 주문 생성 (장바구니 시나리오)
     */
    fun createWithMultipleProducts(
        user: User = UserFixture.create(role = Role.BUYER),
        productQuantities: Map<Product, Int> = mapOf(
            ProductFixture.createSelling() to 2,
            ProductFixture.createSelling() to 1
        )
    ): Order {
        val products = productQuantities.toList()
        return create(user = user, products = products)
    }
}