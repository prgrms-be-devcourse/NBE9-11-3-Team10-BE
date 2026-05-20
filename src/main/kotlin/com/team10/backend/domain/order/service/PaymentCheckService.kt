package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.ConfirmRequest
import com.team10.backend.domain.order.repository.OrderDeliveryRepository
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentCheckService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val orderDeliveryRepository: OrderDeliveryRepository
) {

    @Transactional(readOnly = true)
    fun validatePaymentRequest(request: ConfirmRequest) {
        // 1. 결제 정보 존재 여부 확인
        val payment = paymentRepository.findByOrderNumber(request.orderId)
            ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

        // 2. 주문 정보 존재 여부 확인
        val order = orderRepository.findByOrderNumber(request.orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        // 3. 배송 정보 확인 (Order 엔티티의 상속받은 id 프로퍼티 사용)
        val hasDelivery = orderDeliveryRepository.existsById(order.id)
        if (!hasDelivery) {
            throw BusinessException(ErrorCode.DELIVERY_NOT_FOUND)
        }

        // 4. 금액 위조 검증 (Int 타입에 맞춰 비교)
        if (payment.totalAmount.toLong() != request.amount) {
            throw BusinessException(ErrorCode.AMOUNT_MISMATCH)
        }
    }
}