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
// 정산 명세는 감사/회계 목적의 불변 데이터이므로 소프트 삭제(@SQLDelete) 는 제거하는 것을 권장합니다.
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

    /**
     * JPA 프록시 생성 및 리플렉션 초기화를 위한 무인자 생성자
     * ⚠️ 비즈니스 로직에서 절대 호출되지 않으며, JPA 가 엔티티를 조회/생성할 때만 사용됩니다.
     * Kotlin 은 모든 프로퍼티의 초기화를 강제하므로 null!! 로 임시 할당하지만,
     * 런타임 시 Hibernate 가 실제 DB 데이터로 필드를 덮어쓰므로 안전합니다.
     */
    constructor() : this(
        payment = null!!,
        sellerId = 0L,
        orderNumber = "",
        grossAmount = 0L,
        feeAmount = 0L,
        refundDeducted = 0L,
        netAmount = 0L
    )

    // == 연관관계 편의 메서드 ==
    fun setSettlement(settlement: Settlement?) {
        this.settlement = settlement
    }

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

    // == 조회용 getter (Kotlin property 접근 호환 및 방어적 코딩) ==
    fun getPayment(): Payment = payment
    fun getSellerId(): Long = sellerId
    fun getOrderNumber(): String = orderNumber
    fun getGrossAmount(): Long = grossAmount
    fun getFeeAmount(): Long = feeAmount
    fun getRefundDeducted(): Long = refundDeducted
    fun getNetAmount(): Long = netAmount
    fun getStatus(): String = status
}