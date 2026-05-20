package com.team10.backend.fixture.helper

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderDelivery
import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.enums.RequestType
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.OrderDeliveryFixture
import com.team10.backend.fixture.OrderFixture
import com.team10.backend.fixture.ProductFixture
import com.team10.backend.fixture.UserFixture
import net.datafaker.Faker

/**
 * ⚠️ TEST ONLY - DO NOT USE IN PRODUCTION CODE
 *
 * 이 클래스는 테스트 환경에서만 사용됩니다.
 * 운영 코드에서 직접 호출하면 안 됩니다.
 */

/**
 * 통합 테스트용 Order 생성 헬퍼
 * - 모든 의존 엔티티를 생성 + 저장 + 관계 설정까지 자동 처리
 * - 반환된 [OrderContext] 를 통해 테스트에서 필요한 엔티티 접근 가능
 */
class OrderTestHelper(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val faker: Faker = Faker() // 주입받아도 좋고, 내부 생성해도 좋음
) {

    /**
     * 테스트 결과 컨텍스트: 저장된 엔티티들을 묶어서 반환
     */
    data class OrderContext(
        val seller: User,
        val buyer: User,
        val products: List<Product>,
        val order: Order,
        val orderProducts: List<OrderProducts>,
        val delivery: OrderDelivery,
        val payment: Payment? = null
    )

    /**
     * [기본] 완전한 주문 시나리오 생성 (Seller + Product + Order + Payment)
     */
    fun createCompleteOrder(
        seller: User? = null,
        buyer: User? = null,
        productCount: Int = 1,
        productType: ProductType = ProductType.BOOK,
        orderNumber: String = OrderFixture.generateUniqueOrderNumber(),
        deliveryAddress: String = OrderDeliveryFixture.generateRealisticKoreanAddress(),
        paymentAmount: Int? = null, // null 시 자동 계산
        withPayment: Boolean = true
    ): OrderContext {
        // 1. Seller 생성 (없으면)
        val savedSeller = seller ?: userRepository.save(
            UserFixture.createWithSellerInfo(role = Role.SELLER)
        )

        // 2. Buyer 생성 (없으면)
        val savedBuyer = buyer ?: userRepository.save(
            UserFixture.create(role = Role.BUYER)
        )

        // 3. 상품들 생성 (없으면)
        val products = (1..productCount).map {
            productRepository.save(
                ProductFixture.createSelling(
                    user = savedSeller,
                    type = productType,
                    price = faker.number().numberBetween(5_000, 50_000),
                    stock = faker.number().numberBetween(10, 100)
                )
            )
        }

        // 4. OrderProducts 생성
        val orderProducts = products.map { product ->
            val quantity = faker.number().numberBetween(1, 3)
            OrderProducts.builder()
                .product(product)
                .quantity(quantity)
                .orderPrice(product.price) // 주문 시점 가격 고정
                .build()
        }

        // 5. OrderDelivery 생성
        val delivery = OrderDeliveryFixture.createReady(deliveryAddress)

        // 6. Order 생성 (메모리 상)
        val order = Order.createOrder(
            user = savedBuyer,  // 주문자는 buyer
            orderNumber = orderNumber,
            delivery = delivery,
            items = orderProducts
        )

        // 7. Order 저장 → cascade 로 OrderProducts, OrderDelivery 도 함께 저장
        val savedOrder = orderRepository.save(order)

        // 8. Payment 생성 (옵션)
        val payment = if (withPayment) {
            val amount = paymentAmount ?: orderProducts.sumOf { it.orderPrice * it.quantity }
            val payment = Payment.builder()
                .order(savedOrder)
                .orderNumber(orderNumber)
                .totalAmount(amount)
                .status(PaymentStatus.PAID)
                .type(RequestType.PAYMENT)
                .build()
            // Payment 저장 (Order 는 이미 영속 상태)
            savedOrder.addPayment(payment)
            orderRepository.save(savedOrder) // Payment 도 함께 저장 (cascade)
            payment
        } else null

        return OrderContext(
            seller = savedSeller,
            buyer = savedBuyer,
            products = products,
            order = savedOrder,
            orderProducts = orderProducts,
            delivery = delivery,
            payment = payment
        )
    }

    /**
     * [간소화] 최소 정보만으로 주문 생성 (기본값 최대 활용)
     */
    fun createMinimalOrder(seller: User? = null, buyer: User? = null): OrderContext {
        return createCompleteOrder(
            seller = seller,
            buyer = buyer,
            productCount = 1,
            withPayment = true
        )
    }

    /**
     * [유연성] 커스텀 빌더 스타일 생성
     */
    fun orderBuilder() = OrderBuilderHelper(this)
}

/**
 * 플루언트 빌더 스타일 헬퍼 (선택사항)
 */
class OrderBuilderHelper(
    private val helper: OrderTestHelper
) {
    private var seller: User? = null
    private var buyer: User? = null
    private var productCount = 1
    private var withPayment = true
    private var orderNumber: String? = null

    fun seller(seller: User) = apply { this.seller = seller }
    fun buyer(buyer: User) = apply { this.buyer = buyer }
    fun products(count: Int) = apply { this.productCount = count }
    fun withPayment(withPayment: Boolean) = apply { this.withPayment = withPayment }
    fun orderNumber(number: String) = apply { this.orderNumber = number }

    fun build(): OrderTestHelper.OrderContext {
        return helper.createCompleteOrder(
            seller = seller,
            buyer = buyer,
            productCount = productCount,
            orderNumber = orderNumber ?: OrderFixture.generateUniqueOrderNumber(),
            withPayment = withPayment
        )
    }
}