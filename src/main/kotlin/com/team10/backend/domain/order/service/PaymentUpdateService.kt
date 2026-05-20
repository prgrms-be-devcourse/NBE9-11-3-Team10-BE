package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.enums.OrderStatus
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.repository.OrderDeliveryRepository
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Service
class PaymentUpdateService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val orderDeliveryRepository: OrderDeliveryRepository,
    private val productRepository: ProductRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional // 성공 시 모든 것이 함께 커밋
    fun completeOrderAndFinalizeRecord(orderId: String, paymentKey: String, response: TossConfirmResponse) {
        // 1. 주문에 대해 비관적 락(FOR UPDATE) 획득
        val order = orderRepository.findByOrderNumberWithPessimisticLock(orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        // 2. 레이스 컨디션 방어
        // 먼저 온 놈이 상태를 SUCCESS로 바꾸고 트랜잭션을 끝내면, (웹훅이 먼저 올수도 있고, 결제 승인이 먼저 올수도 있다)
        // 뒤늦게 대기 타다 들어온 놈은 이 IF문에 걸려서 아무 짓도 안 함
        if (order.status == OrderStatus.SUCCESS) {
            return
        }

        // 3. 연관 엔티티들 조회
        val payment = paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)
            ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        val delivery = orderDeliveryRepository.findById(order.id)
            .orElseThrow { BusinessException(ErrorCode.DELIVERY_NOT_FOUND) }

        // 4. 만약 이 안에서 JSON 직렬화 에러가 나거나 DB 커넥션이 끊기면,
        // 아래의 order.successStatusOrder()까지 전부 다 함께 롤백 (Partial Commit 방지)
        val jsonResponse = serializeResponse(response)
        payment.completePayment(paymentKey)
        payment.complete(jsonResponse) // 영수증 JSON 세팅

        // 5. 주문 및 배송 상태 변경
        order.successStatusOrder()
        delivery.startReady()

        // 변경 감지로 일괄 커밋/명시적 저장
        paymentRepository.save(payment)
        orderRepository.save(order)
        orderDeliveryRepository.save(delivery)
    }

    private fun serializeResponse(response: TossConfirmResponse): String {
        return objectMapper.writeValueAsString(response)
    }

    @Transactional
    fun rollbackStockAndCancelOrder(
        orderId: String,
        expired: String,
        nextStatus: PaymentStatus?,
        paymentKey: String?
    ) {
        // [1] 주문(Order)에 대해 비관적 락(FOR UPDATE)을 획득하여 동시성 레이스 컨디션 차단
        val order = orderRepository.findByOrderNumberWithPessimisticLock(orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        val payment = paymentRepository.findFirstByOrderOrderByCreatedAtDesc(order)
            ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        // [레이스 컨디션 2차 방어] 혹시나 가드를 뚫고 들어왔을 때를 대비
        if (order.status == OrderStatus.EXPIRED) {
            return
        }

        if (expired == "EXPIRED") {
            // 여러 주문 상품의 재고 복구 순서를 일관되게 유지하기 위해 상품 ID 기준으로 정렬
            val sortedOrderProducts = order.orderProducts.sortedBy { it.product.id }

            // 1. 주문 상품들의 재고를 원자적 UPDATE로 복구
            for (orderProduct in sortedOrderProducts) {
                val updatedCount = productRepository.increaseStockAtomically(
                    orderProduct.product.id,
                    orderProduct.quantity
                )

                if (updatedCount == 0) {
                    throw BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
                }
            }

            // 2. 주문 및 결제 상태 만료처리
            //엔티티 내부에서 만료(EXPIRED) 처리를 수행
            payment.expirePayment()
            order.expireStatusOrder()
        }
    }
}