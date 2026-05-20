package com.team10.backend.domain.order.enums

import java.util.List

enum class PaymentStatus {
    READY,
    PENDING,
    PAID,
    FAILED,
    CANCELED,
    UNCERTAIN,
    EXPIRED;

    // 현재 상태에서 다음에 올 수 있는 상태들을 정의
    fun canTransitionTo(next: PaymentStatus): Boolean {
        return when (this) {
            READY -> next in listOf(PENDING, PAID, FAILED,EXPIRED) // 토스창 인증 후 바로 DONE이 오는 케이스 대응 (PAID 추가)
            PENDING -> next in listOf(PAID, FAILED, UNCERTAIN,EXPIRED)
            UNCERTAIN -> next in listOf(PAID, FAILED,EXPIRED)
            PAID -> next in listOf(CANCELED) // 결제 완료 후에는 오직 취소 상태로만 전이 가능
            CANCELED, FAILED,EXPIRED -> false // 최종 상태이므로 다음 상태로 전이 불가 (중복 웹훅 자동 차단)
        }
    }
}
