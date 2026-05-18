//package com.team10.backend.domain.order.controller
//
//import com.team10.backend.domain.order.dto.webhook.WebhookPayload
//import com.team10.backend.domain.order.service.PaymentWebhookService2
//import com.team10.backend.global.dto.ApiResponse
//import com.team10.backend.global.dto.ApiResponse.Companion.ok
//import lombok.RequiredArgsConstructor
//import lombok.extern.slf4j.Slf4j
//import org.slf4j.LoggerFactory
//import org.springframework.web.bind.annotation.PostMapping
//import org.springframework.web.bind.annotation.RequestBody
//import org.springframework.web.bind.annotation.RequestMapping
//import org.springframework.web.bind.annotation.RestController
//
//@RestController
//@RequestMapping("/api/v1/payments/webhook")
//class PaymentWebhookController(
//    private val webhookService: PaymentWebhookService2
//) {
//    private val log = LoggerFactory.getLogger(javaClass)
//
//    @PostMapping
//    fun handleWebhook(@RequestBody payload: WebhookPayload): ApiResponse<Void> {
//        log.info("웹훅 수신: eventType={}, orderId={}", payload.eventType, payload.data.orderId)
//
//        webhookService.processWebhook(payload)
//
//        return ApiResponse.ok() // 200 OK를 응답해야 토스가 재전송을 안 함
//    }
//}
