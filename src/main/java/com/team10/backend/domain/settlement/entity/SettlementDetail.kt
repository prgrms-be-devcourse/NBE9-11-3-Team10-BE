package com.team10.backend.domain.settlement.entity

import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "settlement_details",
    indexes = [
        Index(name = "idx_settlement_detail_payment", columnList = "payment_id"),
        Index(name = "idx_settlement_detail_seller_date", columnList = "seller_id, created_at")
    ]
)

class SettlementDetail (
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    var settlement: Settlement? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    val payment: Payment,

    @Column(name = "seller_id", nullable = false, insertable = false, updatable = false)
    val sellerId: Long,

    @Column(name = "order_number", nullable = false)
    val orderNumber: String,

    @Column(name = "gross_amount", nullable = false)
    val grossAmount: Long,

    @Column(name = "fee_amount", nullable = false)
    val feeAmount: Long,

    @Column(name = "refund_deducted", nullable = false)
    val refundDeducted: Long,

    @Column(name = "net_amount", nullable = false)
    val netAmount: Long,

    @Column(name = "status", nullable = false)
    var status: String = "INCLUDED", // INCLUDED, EXCLUDED_REFUND, ADJUSTED_LATER

    @Column(name = "is_deleted")
    var isDeleted: Boolean = false
) : BaseEntity() {
    // == 팩토리 메서드 (비즈니스 로직 전용) ==
    companion object {
        fun create(
            payment: Payment,
            sellerId: Long,
            grossAmount: Long,
            feeAmount: Long,
            refundDeducted: Long
        ): SettlementDetail {
            require(grossAmount >= 0) { "정산 매출액은 0 이상이어야 합니다." }
            require(feeAmount >= 0) { "수수료는 0 이상이어야 합니다." }
            require(refundDeducted >= 0) { "환불 차감액은 0 이상이어야 합니다." }

            val netAmount = grossAmount - feeAmount - refundDeducted

            return SettlementDetail(
                payment = payment,
                sellerId = sellerId,
                orderNumber = payment.orderNumber,
                grossAmount = grossAmount,
                feeAmount = feeAmount,
                refundDeducted = refundDeducted,
                netAmount = netAmount
            )
        }
    }
}