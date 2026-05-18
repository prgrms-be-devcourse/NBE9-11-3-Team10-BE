package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.OrderCreateRequest
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.fixture.OrderFixture
import com.team10.backend.fixture.ProductFixture
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("OrderService  테스트")
class OrderServiceKTest {

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var entityManager: EntityManager // Fixture 객체 영속화를 위해 주입

    @Nested
    @DisplayName("주문 생성 (createOrder)")
    inner class CreateOrder {

        @Test
        @DisplayName("성공: 유효한 요청 데이터와 재고가 존재할 때 주문 생성 및 DTO 반환에 성공한다.")
        fun success() {
            // given
            val buyer = UserFixture.create()
            val seller = UserFixture.create(role = com.team10.backend.domain.user.enums.Role.SELLER)
            val product = ProductFixture.createSelling(user = seller, stock = 10)

            entityManager.persist(buyer)
            entityManager.persist(seller)
            entityManager.persist(product)

            val productReq = OrderCreateRequest.OrderProductReq(productId = product.id, quantity = 2)
            val request = OrderCreateRequest(
                deliveryAddress = "서울시 강남구",
                orderProducts = listOf(productReq)
            )

            // when
            val response = orderService.createOrder(buyer.id, request)

            // then
            assertNotNull(response)
            assertNotNull(response.userId)
        }

        @Test
        @DisplayName("예외: 존재하지 않는 유저 ID로 주문을 요청하면 USER_NOT_FOUND 예외가 발생한다.")
        fun throwExceptionWhenUserNotFound() {
            // given
            val seller = UserFixture.create(role = com.team10.backend.domain.user.enums.Role.SELLER)
            val product = ProductFixture.createSelling(user = seller, stock = 10)

            // 상품은 정상적으로 존재하게 만듬
            entityManager.persist(seller)
            entityManager.persist(product)

            // 존재하지 않는 임의의 유저 ID 세팅
            val invalidUserId = 999_999L

            val productReq = OrderCreateRequest.OrderProductReq(productId = product.id, quantity = 2)
            val request = OrderCreateRequest(
                deliveryAddress = "서울시 강남구",
                orderProducts = listOf(productReq)
            )

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.createOrder(invalidUserId, request)
            }
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.errorCode)
        }

        @Test
        @DisplayName("예외: 요청된 상품 중 존재하지 않는 상품 ID가 있으면 PRODUCT_NOT_FOUND 예외가 발생한다.")
        fun throwExceptionWhenProductNotFound() {
            // given
            val buyer = UserFixture.create()
            entityManager.persist(buyer)

            val invalidProductId = 999_999L // 존재하지 않는 가짜 상품 ID
            val productReq = OrderCreateRequest.OrderProductReq(productId = invalidProductId, quantity = 2)
            val request = OrderCreateRequest(
                deliveryAddress = "서울시 강남구",
                orderProducts = listOf(productReq)
            )

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.createOrder(buyer.id, request)
            }
            assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.errorCode)
        }

        @Test
        @DisplayName("예외: 상품의 현재 재고보다 많은 수량을 주문하면 INSUFFICIENT_STOCK 예외가 발생한다.")
        fun throwExceptionWhenStockIsInsufficient() {
            // given
            val buyer = UserFixture.create()
            val seller = UserFixture.create(role = com.team10.backend.domain.user.enums.Role.SELLER)
            val product = ProductFixture.createSelling(user = seller, stock = 5) // 재고 5개 설정

            entityManager.persist(buyer)
            entityManager.persist(seller)
            entityManager.persist(product)

            val productReq = OrderCreateRequest.OrderProductReq(productId = product.id, quantity = 10) // 10개 주문 시도
            val request = OrderCreateRequest(
                deliveryAddress = "서울시 강남구",
                orderProducts = listOf(productReq)
            )

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.createOrder(buyer.id, request)
            }
            assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.errorCode)
        }
    }

    @Nested
    @DisplayName("바이어 주문 내역 전체 조회 (getBuyerOrderList)")
    inner class GetBuyerOrderList {

        @Test
        @DisplayName("성공: 바이어 권한을 가진 유저가 요청 시 자신의 주문 내역이 최신순으로 정렬되어 조회된다.")
        fun success() {
            // given
            // 1. 바이어 유저 생성 및 영속화
            val buyer = UserFixture.create(role = Role.BUYER)
            entityManager.persist(buyer)

            // 2. 판매 상품들 생성 및 영속화
            val seller = UserFixture.create(role = Role.SELLER)
            val product1 = ProductFixture.createSelling(user = seller)
            val product2 = ProductFixture.createSelling(user = seller)
            entityManager.persist(seller)
            entityManager.persist(product1)
            entityManager.persist(product2)

            // 3. 해당 바이어의 주문 2개 생성 (생성 순서에 따른 최신순 검증을 위함)
            val order1 = OrderFixture.create(user = buyer, products = listOf(product1 to 1))
            val order2 = OrderFixture.create(user = buyer, products = listOf(product2 to 3))

            entityManager.persist(order1)
            entityManager.persist(order2)

            // 영속성 컨텍스트의 캐시를 비워 DB 정렬(id 혹은 createdAt 내림차순)을 정확히 테스트하기 위함
            entityManager.flush()
            entityManager.clear()

            // when
            val response = orderService.getBuyerOrderList(buyer.id)

            // then
            assertNotNull(response)
            assertEquals(buyer.id, response.userId) // 반환된 회원 정보 검증
            assertEquals(2, response.orders.size)          // 주문 개수 검증

            // repository의 OrderByCreatedAtDesc(또는 ID 내림차순) 조건에 따라
            // 나중에 생성된 order2가 무조건 첫 번째(인덱스 0)에 와야 함
            assertEquals(order2.orderNumber, response.orders[0].orderNumber)
            assertEquals(order1.orderNumber, response.orders[1].orderNumber)
        }

        @Test
        @DisplayName("예외: 존재하지 않는 유저 ID로 조회를 요청하면 USER_NOT_FOUND 예외가 발생한다.")
        fun throwExceptionWhenUserNotFound() {
            // given
            val invalidUserId = 999_999L

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.getBuyerOrderList(invalidUserId)
            }
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.errorCode)
        }

        @Test
        @DisplayName("예외: 요청 유저의 권한이 BUYER가 아닌 경우(예: SELLER) ACCESS_DENIED 예외가 발생한다.")
        fun throwExceptionWhenUserIsNotBuyer() {
            // given
            // BUYER가 아닌 SELLER 권한으로 유저를 생성하여 DB에 저장
            val seller = UserFixture.create(role = Role.SELLER)
            entityManager.persist(seller)

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.getBuyerOrderList(seller.id) // 셀러 ID로 바이어 조회 API 호출
            }
            assertEquals(ErrorCode.ACCESS_DENIED, exception.errorCode)
        }
    }

    @Nested
    @DisplayName("셀러 판매 내역 전체 조회 (getSellerOrderList)")
    inner class GetSellerOrderList {

        @Test
        @DisplayName("성공: 셀러 권한을 가진 유저가 요청 시 자신에게 들어온 주문 상품(판매) 내역이 정상 조회된다.")
        fun success() {
            // given
            // 1. 셀러(나)와 바이어(구매자) 생성 및 영속화
            val seller = UserFixture.create(role = Role.SELLER)
            val buyer = UserFixture.create(role = Role.BUYER)
            entityManager.persist(seller)
            entityManager.persist(buyer)

            // 2. 내가 등록한 상품들 생성 및 영속화
            val myProduct1 = ProductFixture.createSelling(user = seller, price = 20000)
            val myProduct2 = ProductFixture.createSelling(user = seller, price = 50000)
            entityManager.persist(myProduct1)
            entityManager.persist(myProduct2)

            // 3. 다른 셀러가 등록한 남의 상품 생성 및 영속화 (조회 대상에서 제외되어야 함)
            val otherSeller = UserFixture.create(role = Role.SELLER)
            val otherProduct = ProductFixture.createSelling(user = otherSeller)
            entityManager.persist(otherSeller)
            entityManager.persist(otherProduct)

            // 4. 바이어가 내 상품 2개와 다른 사람 상품 1개를 각각 주문함
            val order1 = OrderFixture.create(user = buyer, products = listOf(myProduct1 to 2)) // 내 상품 2개 구매
            val order2 = OrderFixture.create(user = buyer, products = listOf(myProduct2 to 1, otherProduct to 1)) // 내 것 1개, 남의 것 1개 구매

            entityManager.persist(order1)
            entityManager.persist(order2)

            // 영속성 컨텍스트의 캐시를 지워 완전한 DB 조회 상태로 테스트 격리
            entityManager.flush()
            entityManager.clear()

            // when
            val response = orderService.getSellerOrderList(seller.id)

            // then
            assertNotNull(response)
            assertEquals(seller.id, response.sellerId) // 반환된 판매자 정보 검증

            // 총 3개의 상품 주문 건 중, '나의 상품'이 포함된 건수는 2개여야 함 (myProduct1, myProduct2)
            assertEquals(2, response.sales.size)

            // 금액이나 수량이 도메인 로직에 맞게 올바르게 매핑되었는지 교차 검증
            // DTO 구조에 맞게 프로퍼티명(예: price, quantity, productName 등)을 맞춰 비교하세요.
            val item1 = response.sales.firstOrNull { it.productName == myProduct1.productName }
            val item2 = response.sales.firstOrNull { it.productName == myProduct2.productName }

            assertNotNull(item1)
            assertNotNull(item2)
            assertEquals(2, item1?.quantity) // myProduct1은 2개 주문함
            assertEquals(1, item2?.quantity) // myProduct2는 1개 주문함
        }

        @Test
        @DisplayName("예외: 존재하지 않는 유저 ID로 조회를 요청하면 USER_NOT_FOUND 예외가 발생한다.")
        fun throwExceptionWhenUserNotFound() {
            // given
            val invalidSellerId = 999_999L

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.getSellerOrderList(invalidSellerId)
            }
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.errorCode)
        }

        @Test
        @DisplayName("예외: 요청 유저의 권한이 SELLER가 아닌 경우(예: BUYER) ACCESS_DENIED 예외가 발생한다.")
        fun throwExceptionWhenUserIsNotSeller() {
            // given
            // SELLER가 아닌 BUYER 권한으로 유저를 생성하여 DB에 저장
            val buyer = UserFixture.create(role = Role.BUYER)
            entityManager.persist(buyer)

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.getSellerOrderList(buyer.id) // 바이어 ID로 셀러 전용 API 호출
            }
            assertEquals(ErrorCode.ACCESS_DENIED, exception.errorCode)
        }
    }

    @Nested
    @DisplayName("주문 상세 조회 (getOrderDetail)")
    inner class GetOrderDetail {

        @Test
        @DisplayName("예외: 존재하지 않는 주문 번호(orderNumber)로 조회를 요청하면 ORDER_NOT_FOUND 예외가 발생한다.")
        fun throwExceptionWhenOrderNotFound() {
            // given
            val user = UserFixture.create(role = Role.BUYER)
            entityManager.persist(user)
            val invalidOrderNumber = "ORD-NOT-FOUND-123"

            // when & then
            val exception = assertThrows<BusinessException> {
                orderService.getOrderDetail(user.id, invalidOrderNumber)
            }
            assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.errorCode)
        }


        //  1. 구매자(BUYER) 권한 테스트 그룹
        @Nested
        @DisplayName("요청 유저가 구매자(BUYER)인 경우")
        inner class BuyerContext {

            @Test
            @DisplayName("성공: 자신이 주문한 주문서 상세 조회를 요청하면 정상적으로 DTO가 반환된다.")
            fun success() {
                // given
                val buyer = UserFixture.create(role = Role.BUYER)
                val seller = UserFixture.create(role = Role.SELLER)
                val product = ProductFixture.createSelling(user = seller)

                entityManager.persist(buyer)
                entityManager.persist(seller)
                entityManager.persist(product)

                val order = OrderFixture.create(user = buyer, products = listOf(product to 1))
                entityManager.persist(order)

                entityManager.flush()
                entityManager.clear()

                // when
                val response = orderService.getOrderDetail(buyer.id, order.orderNumber)
                System.out.println(response)
                // then
                assertNotNull(response)
                assertEquals(order.orderNumber, response.orderNumber)
            }

            @Test
            @DisplayName("예외: 다른 사람이 주문한 주문서 상세 조회를 요청하면 ACCESS_DENIED 예외가 발생한다.")
            fun throwExceptionWhenNotOrderOwner() {
                // given
                val orderOwner = UserFixture.create(role = Role.BUYER)
                val hacker = UserFixture.create(role = Role.BUYER) // 침입하려는 다른 구매자
                val seller = UserFixture.create(role = Role.SELLER)
                val product = ProductFixture.createSelling(user = seller)

                entityManager.persist(orderOwner)
                entityManager.persist(hacker)
                entityManager.persist(seller)
                entityManager.persist(product)

                val order = OrderFixture.create(user = orderOwner, products = listOf(product to 1))
                entityManager.persist(order)

                // when & then
                val exception = assertThrows<BusinessException> {
                    orderService.getOrderDetail(hacker.id, order.orderNumber) // hacker ID 유입
                }
                assertEquals(ErrorCode.ACCESS_DENIED, exception.errorCode)
            }
        }


        // 📦 2. 판매자(SELLER) 권한 테스트 그룹

        @Nested
        @DisplayName("요청 유저가 판매자(SELLER)인 경우")
        inner class SellerContext {

            @Test
            @DisplayName("성공: 해당 주문 내역 중 '자신이 판매하는 상품'이 최소 1개 이상 포함되어 있다면 상세 조회가 허용된다.")
            fun success() {
                // given
                val buyer = UserFixture.create(role = Role.BUYER)
                val seller = UserFixture.create(role = Role.SELLER) // 조회 요청할 셀러(나)
                val otherSeller = UserFixture.create(role = Role.SELLER)

                val myProduct = ProductFixture.createSelling(user = seller)
                val otherProduct = ProductFixture.createSelling(user = otherSeller)

                entityManager.persist(buyer)
                entityManager.persist(seller)
                entityManager.persist(otherSeller)
                entityManager.persist(myProduct)
                entityManager.persist(otherProduct)

                // 내 상품과 타인 상품이 장바구니 형태로 섞여서 주문된 시나리오 생성
                val order = OrderFixture.create(
                    user = buyer,
                    products = listOf(myProduct to 1, otherProduct to 2)
                )
                entityManager.persist(order)

                entityManager.flush()
                entityManager.clear()

                // when
                val response = orderService.getOrderDetail(seller.id, order.orderNumber)

                // then
                assertNotNull(response)
                assertEquals(order.orderNumber, response.orderNumber)
            }

            @Test
            @DisplayName("예외: 해당 주문 내역에 포함된 상품 중 '자신이 판매하는 상품'이 단 하나도 없다면 ACCESS_DENIED 예외가 발생한다.")
            fun throwExceptionWhenNoMyProductInOrder() {
                // given
                val buyer = UserFixture.create(role = Role.BUYER)
                val seller = UserFixture.create(role = Role.SELLER) // 내 상품은 주문에 없음
                val otherSeller = UserFixture.create(role = Role.SELLER) // 이 사람 상품만 주문됨

                val otherProduct = ProductFixture.createSelling(user = otherSeller)

                entityManager.persist(buyer)
                entityManager.persist(seller)
                entityManager.persist(otherSeller)
                entityManager.persist(otherProduct)

                // 남의 상품만 들어있는 주문 생성
                val order = OrderFixture.create(user = buyer, products = listOf(otherProduct to 1))
                entityManager.persist(order)

                // when & then
                val exception = assertThrows<BusinessException> {
                    orderService.getOrderDetail(seller.id, order.orderNumber) // 주문 상품과 무관한 셀러 ID 유입
                }
                assertEquals(ErrorCode.ACCESS_DENIED, exception.errorCode)
            }
        }
    }
}