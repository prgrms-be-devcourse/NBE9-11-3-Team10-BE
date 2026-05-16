package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.ConfirmRequest
import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import lombok.RequiredArgsConstructor
import org.springframework.stereotype.Service

//import lombok.extern.slf4j.Slf4j;
@Service
class PaymentService(
    private val paymentCheckService: PaymentCheckService,
    private val paymentUpdateService: PaymentUpdateService,
    private val orderConfirmService: OrderConfirmService
) {

    fun confirmPayment(request: ConfirmRequest): TossConfirmResponse {
        // 1. 사전 검증 (TX 1 시작 및 종료)
        paymentCheckService.validatePaymentRequest(request)

        // 2. 결제 승인 요청 (내부에서 REQUIRES_NEW로 시도 기록 저장 후 API 호출)
        // 이 구간에서는 메인 트랜잭션이 없으므로 DB 커넥션을 점유하지 않습니다.
        val response = orderConfirmService.sendConfirmRequest(request, null)

        // 3. 후처리 (TX 2 시작 및 종료)
        // API 승인이 성공했으므로 우리 DB 상태를 최종 업데이트합니다.
        paymentUpdateService.completeOrderAndFinalizeRecord(
            orderId = request.orderId,
            paymentKey = request.paymentKey,
            response = response
        )

        return response
    }
}