package com.team10.backend.domain.order.service

import com.team10.backend.domain.order.dto.confirm.ConfirmRequest
import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.enums.RequestType
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import tools.jackson.databind.ObjectMapper
import java.util.*

//import lombok.extern.slf4j.Slf4j;
@Service //@Slf4j
class OrderConfirmService(
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate,
    private val paymentStatusService: PaymentStatusService,
    private val orderRepository: OrderRepository,
    @Value("\${custom.toss.payment.secret-key}") private val secretKey: String
) {

    private val tossUrl = "https://api.tosspayments.com/v1/payments"

    // 1. 재시도 로직
    @Retryable(
        value = [ResourceAccessException::class],
        exclude = [BusinessException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0) // 코틀린에서는 double 표기 명시
    )
    fun sendConfirmRequest(request: ConfirmRequest,idempotencyKey: String, testCode: String?): TossConfirmResponse {
        val headers = HttpHeaders()

        if (testCode != null) {
            headers.add("TossPayments-Test-Code", testCode)
        }

        // 시크릿 키 문자열 템플릿 처리 후 인코딩
        val encodedKey = Base64.getEncoder().encodeToString("$secretKey:".toByteArray())

        val order = orderRepository.findByOrderNumber(request.orderId)
            ?: throw BusinessException(ErrorCode.ORDER_NOT_FOUND)

        val currentPayment = paymentStatusService.getOrCreatePaymentAttempt(order, RequestType.PAYMENT,idempotencyKey)

        // AOP 캐시가 만료된 이후 재진입 시 방어용
        if (currentPayment.status == PaymentStatus.PAID) {
            val responseBody = currentPayment.responseBody
                ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND) // 혹은 데이터 정합성 에러
            return paymentStatusService.parseResponse(responseBody)
        }

        val suffix = if (testCode != null) UUID.randomUUID().toString() else ""

        headers.set("Idempotency-Key", "$idempotencyKey$suffix")
        headers.set("Authorization", "Basic $encodedKey")
        headers.contentType = MediaType.APPLICATION_JSON

        val entity = HttpEntity(request, headers)

        try {
            // 자바 클래스 메타데이터 표현식 최적화 및 널 안정성 확보
            val response = restTemplate.postForEntity(
                "$tossUrl/confirm",
                entity,
                TossConfirmResponse::class.java
            )

            return response.body ?: throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)
        } catch (e: HttpClientErrorException) {
            val errorBody = e.getResponseBodyAsString()
            paymentStatusService.finalizeRecord(currentPayment, PaymentStatus.FAILED, null)
            handleBusinessError(e.statusCode, errorBody)
            throw e
        } catch (e: HttpServerErrorException) {
            val errorBody = e.getResponseBodyAsString()
            paymentStatusService.finalizeRecord(currentPayment, PaymentStatus.FAILED, null)
            handleSystemError(e.statusCode, errorBody)
            throw e
        } catch (e: ResourceAccessException) {
            paymentStatusService.markRecordAsUncertain(currentPayment)
            throw e
        }
    }


    // 최종적으로 사용자에게 실패 응답을 던지거나, 관리자 알림을 보냄
    @Recover
    fun recover(e: ResourceAccessException, request: ConfirmRequest, idempotencyKey: String, testCode: String?): TossConfirmResponse {
        System.err.println("[ERROR] 결제 승인 최종 실패 - 주문번호: ${request.orderId}, 에러: ${e.message}")

        // TODO: 관리자에게 알람 로직 구현
        // 네트워크 장애 시: "결제 확인 중" 상태로 변경하거나 관리자 알림

        throw BusinessException(ErrorCode.NETWORK_ERROR_FINAL_FAILED)
    }

    private fun handleBusinessError(status: HttpStatusCode, errorBody: String?) {
        val errorCode = parseErrorCode(errorBody)

        // 중첩 when 구조를 사용해 status와 errorCode를 철저하고 가독성 높게 매핑
        when (status) {
            HttpStatus.NOT_FOUND -> when (errorCode) {
                "NOT_FOUND_PAYMENT" -> throw BusinessException(ErrorCode.NOT_FOUND_PAYMENT)
                "NOT_FOUND_PAYMENT_SESSION" -> throw BusinessException(ErrorCode.NOT_FOUND_PAYMENT_SESSION)
//                else -> throw BusinessException(HttpStatus.NOT_FOUND) // 정의되지 않은 404 기본 에러 처리
            }

            HttpStatus.FORBIDDEN -> when (errorCode) {
                "REJECT_ACCOUNT_PAYMENT" -> throw BusinessException(ErrorCode.REJECT_ACCOUNT_PAYMENT)
                "REJECT_CARD_PAYMENT" -> throw BusinessException(ErrorCode.REJECT_CARD_PAYMENT)
                "REJECT_CARD_COMPANY" -> throw BusinessException(ErrorCode.REJECT_CARD_COMPANY)
                "FORBIDDEN_REQUEST" -> throw BusinessException(ErrorCode.FORBIDDEN_REQUEST)
                "INVALID_PASSWORD" -> throw BusinessException(ErrorCode.INVALID_PASSWORD)
//                else -> throw BusinessException(ErrorCode.FORBIDDEN_REQUEST) // 정의되지 않은 403 기본 에러 처리
            }

            HttpStatus.BAD_REQUEST -> when (errorCode) {
                "ALREADY_PROCESSED_PAYMENT" -> throw BusinessException(ErrorCode.ALREADY_PROCESSED_PAYMENT)
                "INVALID_REQUEST" -> throw BusinessException(ErrorCode.INVALID_REQUEST)
                "INVALID_API_KEY" -> throw BusinessException(ErrorCode.INVALID_API_KEY)
                "INVALID_REJECT_CARD" -> throw BusinessException(ErrorCode.INVALID_REJECT_CARD)
                "INVALID_CARD_EXPIRATION" -> throw BusinessException(ErrorCode.INVALID_CARD_EXPIRATION)
                "INVALID_STOPPED_CARD" -> throw BusinessException(ErrorCode.INVALID_STOPPED_CARD)
                "INVALID_CARD_LOST_OR_STOLEN" -> throw BusinessException(ErrorCode.INVALID_CARD_LOST_OR_STOLEN)
                "INVALID_CARD_NUMBER" -> throw BusinessException(ErrorCode.INVALID_CARD_NUMBER)
                "INVALID_ACCOUNT_INFO_RE_REGISTER" -> throw BusinessException(ErrorCode.INVALID_ACCOUNT_INFO_RE_REGISTER)
                "UNAPPROVED_ORDER_ID" -> throw BusinessException(ErrorCode.UNAPPROVED_ORDER_ID)
//                else -> throw BusinessException(ErrorCode.BAD_REQUEST) // 정의되지 않은 400 기본 에러 처리
            }

            else -> throw BusinessException(ErrorCode.INVALID_REQUEST) // 400, 403, 404 이외의 예외 처리 가드
        }
    }

    private fun handleSystemError(status: HttpStatusCode, errorBody: String?) {
        val errorCode = parseErrorCode(errorBody)

        // log.error("토스페이먼츠 5xx 에러 발생 - Status: $status, Code: $errorCode")
        // TODO: 관리자나 개발자에게 알람이 가는 로직

        // 3. 토스 서버 및 은행 점검 문제 (500 계열) 처리 철저화
        if (!status.is5xxServerError) {
            throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR) // 5xx가 아닌 경우 가드 분기
        }

        when (errorCode) {
            "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING" -> throw BusinessException(ErrorCode.FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING)
            "UNKNOWN_PAYMENT_ERROR" -> throw BusinessException(ErrorCode.UNKNOWN_PAYMENT_ERROR)
            "FAILED_INTERNAL_SYSTEM_PROCESSING" -> throw BusinessException(ErrorCode.FAILED_INTERNAL_SYSTEM_PROCESSING)
            else -> throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR) // 정의되지 않은 5xx 기본 에러 처리
        }
    }

    private fun parseErrorCode(errorBody: String?): String {
        // errorBody가 널이거나 비어있으면 즉시 기본 코드 반환 (가드 절)
        if (errorBody.isNullOrBlank()) {
            return "UNKNOWN_ERROR"
        }

        // 코틀린에서 try-catch는 '식(Expression)'이므로 반환값으로 바로 사용 가능
        return try {
            val root = objectMapper.readTree(errorBody)
            root.path("code").asText("UNKNOWN_ERROR") // "code" 필드가 없을 때의 기본값 지정
        } catch (e: Exception) {
            "UNKNOWN_ERROR"
        }
    }
}
