package com.team10.backend.domain.settlement.service

import com.team10.backend.domain.order.entity.Payment
import org.springframework.stereotype.Component

/**
 * 정산 수수료 계산 전략 인터페이스
 * - 판매자 등급, 카테고리, 프로모션 등에 따라 다른 정책 적용 가능
 */
interface FeePolicy {
    fun calculate(grossAmount: Long): Long
    fun description(): String
}

@Component
class DefaultFeePolicy : FeePolicy {
    // 예: 정률 3% + 정액 100 원
    private val rate = 0.03
    private val fixedFee = 100L

    override fun calculate(grossAmount: Long): Long {
        return (grossAmount * rate).toLong() + fixedFee
    }

    override fun description(): String = "정률 3% + 정액 100 원"
}

@Component
class FeeCalculator(
    private val defaultPolicy: DefaultFeePolicy
    // 추후: private val policyRepository: FeePolicyRepository
) {
    fun getPolicy(payment: Payment): FeePolicy {
        // TODO: 판매자 등급/카테고리 기반 동적 정책 선택 로직
        // 예: if (payment.order.user.isPremium) return PremiumFeePolicy()
        return defaultPolicy
    }

    fun calculateNetAmount(payment: Payment, policy: FeePolicy = getPolicy(payment)): Long {
        val gross = payment.totalAmount.toLong()
        val fee = policy.calculate(gross)
        val refundDeducted = 0L // 부분 취소 로직은 별도 확장
        return gross - fee - refundDeducted
    }
}