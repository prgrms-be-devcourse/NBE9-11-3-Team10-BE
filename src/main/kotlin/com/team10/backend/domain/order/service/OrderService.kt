package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.OrderCreateRequest
import com.team10.backend.domain.order.dto.OrderResponse
import com.team10.backend.domain.order.dto.cancel.CancelRequest
import com.team10.backend.domain.order.dto.search.OrderDetailResponse
import com.team10.backend.domain.order.dto.search.buyer.OrderListResponse
import com.team10.backend.domain.order.dto.search.buyer.OrderSummaryResponse
import com.team10.backend.domain.order.dto.search.seller.SellerOrderListResponse
import com.team10.backend.domain.order.dto.search.seller.SellerOrderSummaryResponse
import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderDelivery
import com.team10.backend.domain.order.entity.OrderProducts
import com.team10.backend.domain.order.enums.DeliveryStatus
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.repository.OrderProductRepository
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderProductRepository: OrderProductRepository
//    private val RefundService : RefundService
) {
    // 사전 재고 검증용 메서드
    // 주문 생성에서는 동시성 보장을 위해 원자적 UPDATE 결과로 재고 부족 여부를 판단하여 현재 사용하지 않는다.
    fun validateStockAvailability(request: OrderCreateRequest) {
        for (productReq in request.orderProducts) {
            // 1. findByIdOrNull과 엘비스 연산자를 사용해 Null 안전성 확보 및 !! 제거
            val product = productRepository.findByIdOrNull(productReq.productId)
                ?: throw BusinessException(ErrorCode.PRODUCT_NOT_FOUND)

            // 2. 재고 검증
            if (product.stock < productReq.quantity) {
                throw BusinessException(ErrorCode.INSUFFICIENT_STOCK)
            }
        }
    }

    @Transactional
    fun createOrder(userId: Long, req: OrderCreateRequest): OrderResponse {
        // 1. 주문자 조회 (findUser에서 유효한 User 객체 반환)
        val user = findUser(userId)

        // 2. 배송 정보 엔티티 생성
        val delivery = deliveryInfo(req)

        // 3. 주문 상품(OrderProducts) 리스트 생성
        val orderProductsList = getOrderProductList(req)

        // 4. 토스페이먼츠 호환 주문 번호 생성 (문자열 템플릿 사용)
        val todayDate = LocalDate.now().toString().replace("-", "")
        val uuidSnippet = UUID.randomUUID().toString().substring(0, 8)
        val orderNumber = "ORD-$todayDate-$uuidSnippet"

        // 5. 최종적으로 주문 생성
        val order = Order.createOrder(user, orderNumber, delivery, orderProductsList)

        // 6. 영속화 (CascadeType.ALL로 연관 엔티티 함께 저장)
        orderRepository.save(order)

        // 7. 응답 DTO 변환 및 반환
        return OrderResponse.from(order)
    }


    fun findUser(userId: Long): User {
        return userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND, "해당 유저 정보를 찾을 수 없습니다.")
    }


    //order-delivery 테이블에 배송지, 운송장 번호 생성
    fun deliveryInfo(request: OrderCreateRequest): OrderDelivery {
        return OrderDelivery(
            deliveryAddress = request.deliveryAddress,
            trackingNumber = null // 초기 생성 시 송장 번호는 null
        )
    }


    // 주문 상품 생성에 필요한 상품 정보를 조회하고, 재고 차감은 원자적 UPDATE로 처리
    fun getOrderProductList(request: OrderCreateRequest): List<OrderProducts> {
        return request.orderProducts.map { productReq ->
            // 1. 주문 상품 생성에 필요한 상품 가격 정보를 조회
            val product = productRepository.findById(productReq.productId).orElseThrow {
                BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다. ID: ${productReq.productId}")
            }
            // 2. 재고 부족 검증과 차감을 하나의 UPDATE 문으로 원자적으로 처리
            val updatedCount = productRepository.decreaseStockAtomically(
                productReq.productId,
                productReq.quantity
            )

            if (updatedCount == 0) {
                throw BusinessException(ErrorCode.INSUFFICIENT_STOCK)
            }

            // 3. 빌더 대신 주 생성자로 안전하게 객체 생성
            OrderProducts(
                product = product,
                quantity = productReq.quantity,
                orderPrice = product.price
            )
        }
    }


    // [Buyer] 주문한 내역 전체 조회
    @Transactional(readOnly = true)
    fun getBuyerOrderList(userId: Long): OrderListResponse {
        // 1. 유저 존재 여부 및 권한 검증
        val user = findUser(userId)
        if (user.role != Role.BUYER) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        // 2. 해당 유저의 모든 주문 내역 조회
        val userOrderList: List<Order> = orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId)

        // 3. 코틀린 map 함수를 사용해 DTO 리스트로 변환
        val summaryResponses = userOrderList.map { order ->
            OrderSummaryResponse.from(order)
        }

        // 4. 회원 정보와 주문 목록을 결합하여 반환
        return OrderListResponse.of(user, summaryResponses)
    }


    // [Seller] 나에게 들어온 판매 내역 전체 조회
    @Transactional(readOnly = true)
    fun getSellerOrderList(sellerId: Long): SellerOrderListResponse {
        // 1. 판매자 존재 확인 및 권한 검증
        val seller = findUser(sellerId)
        if (seller.role != Role.SELLER) { // 프로퍼티 접근법 사용
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        // 2. 이 판매자가 등록한 상품들이 포함된 '주문 상품(OrderProducts)' 조회
        val sellerSales: List<OrderProducts> = orderProductRepository.findAllBySellerId(sellerId)

        // 3. 코틀린 map 함수를 사용해 DTO 리스트로 변환
        val summaryResponses = sellerSales.map { orderProduct ->
            SellerOrderSummaryResponse.from(orderProduct)
        }

        // 4. 판매자 정보와 판매 요약 목록을 결합하여 반환
        return SellerOrderListResponse.of(seller, summaryResponses)
    }


    @Transactional(readOnly = true)
    fun getOrderDetail(curUserId: Long, orderNumber: String): OrderDetailResponse {
        // 1. 주문 상세 조회 및 Null 검증
        val order = orderRepository.findByOrderNumberWithDetails(orderNumber)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        // 2. 현재 요청 유저 조회
        val user = findUser(curUserId)

        // 3. Enum 모든 분기 처리
        when (user.role) {
            Role.BUYER -> {
                // 구매자라면: 주문서의 주인인지 확인
                if (order.user.id != curUserId) {
                    throw BusinessException(ErrorCode.ACCESS_DENIED)
                }
            }

            Role.SELLER -> {
                // 판매자라면: orderProducts 중 '내 상품'이 하나라도 포함되어 있는지 확인
                val isSellerOfThisOrder = order.orderProducts.any { op ->
                    op.product.user.id == curUserId
                }
                if (!isSellerOfThisOrder) {
                    throw BusinessException(ErrorCode.ACCESS_DENIED)
                }
            }
            // 만야 다른 Role(예: ADMIN 등)이 추가되더라도 컴파일러가 잡아주거나 else로 방어 조치 가능
            else -> throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        // 4. DTO 변환 및 반환
        return OrderDetailResponse.from(order)
    }


    @Transactional
    fun deleteOrderSoft(userId: Long, orderNumber: String) {
        // 1. 주문 내역 조회 및 Null 검증
        val order = orderRepository.findByOrderNumber(orderNumber)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        // 2. 권한 및 배송 상태 검증
        validateOrderDelete(userId, order)

        // 3. 결제 상태와 재고 로직에 대한 검증 및 처리
        handlePaymentAndStock(order)

        // 4. 주문 상태를 CANCELED로 변경
        order.cancelStatusOrder()

        // 5. 소프트 딜리트 수행 (@SQLDelete 작동) 및 즉시 반영
        orderRepository.delete(order)
        orderRepository.flush()
    }


    private fun validateOrderDelete(userId: Long, order: Order) {
        // 1. 주문자 권한 검증
        if (order.user.id != userId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        // 2. 배송 정보가 없을 때의 예외 처리
        val delivery = order.delivery
            ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        when (delivery.status) {
            DeliveryStatus.SHIPPING,
            DeliveryStatus.COMPLETED -> {
                throw BusinessException(ErrorCode.CANNOT_CANCEL_SHIPPING_ORDER)
            }

            else -> {
                // READY 등 취소가 가능한 상태일 때는 아무것도 하지 않고 통과
            }
        }
    }


    private fun handlePaymentAndStock(order: Order) {
        // 1. 결제 리스트 검증
        if (order.payments.isEmpty()) {
            throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)
        }

        // 2. 컬렉션 내장 함수를 사용하여 가장 마지막 결제 정보 추출
        val latestPayment = order.payments.last()
        val paymentStatus = latestPayment.status

        // 3. 팀 컨벤션에 맞춰 결제 상태(paymentStatus)에 따른 대응을 when 식으로 철저하게 분기 처리
        when (paymentStatus) {
            PaymentStatus.PAID -> {
                // [이미 결제된 경우]: 환불 로직 실행
                val cancelRequest = CancelRequest("고객 요청에 의한 주문 취소")
                // refundService.sendCancelRequest(order.orderNumber, latestPayment.paymentKey, cancelRequest)

                // 결제 완료(PAID) 상태도 재고 복구가 필요하므로 아래 복구 로직 함수 호출
                restoreStock(order)
            }

            PaymentStatus.READY -> {
                // [결제 대기인 경우]: 결제 전이므로 환불 없이 재고만 복구
                restoreStock(order)
            }

            else -> {
                // FAILED, PENDING 등 재고 복구나 환불이 필요 없는 상태일 때는 아무것도 하지 않음
            }
        }
    }

    // 재고 복구 공통 로직
    private fun restoreStock(order: Order) {
        for (orderProduct in order.orderProducts) {
            val updatedCount = productRepository.increaseStockAtomically(
                orderProduct.product.id,
                orderProduct.quantity
            )

            if (updatedCount == 0) {
                throw BusinessException(
                    ErrorCode.PRODUCT_NOT_FOUND,
                    "상품을 찾을 수 없습니다. ID: ${orderProduct.product.id}"
                )
            }
        }
    }
}
