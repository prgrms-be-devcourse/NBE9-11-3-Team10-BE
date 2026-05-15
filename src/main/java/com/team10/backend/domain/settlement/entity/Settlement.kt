package com.team10.backend.domain.settlement.entity

import com.team10.backend.domain.settlement.enums.SettlementStatus
import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_settlement_period_seller",
            columnNames = ["seller_id", "period_start", "period_end"]
        )
    ]
)
@SQLDelete(sql = "UPDATE settlements SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
class Settlement (
    @Column(name = "settlement_no", nullable = false, unique = true)
    val settlementNo: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    val seller: User,

    @Column(name = "period_start", nullable = false) val periodStart: LocalDate,
    @Column(name = "period_end", nullable = false) val periodEnd: LocalDate,

    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) var status: SettlementStatus = SettlementStatus.PENDING,
    @Column(name = "total_gross_amount", nullable = false) var totalGrossAmount: Long = 0L,
    @Column(name = "total_fee_amount", nullable = false) var totalFeeAmount: Long = 0L,
    @Column(name = "total_refund_deducted", nullable = false) var totalRefundDeducted: Long = 0L,
    @Column(name = "net_settlement_amount", nullable = false) var netSettlementAmount: Long = 0L,
    @Column(name = "settled_at") var settledAt: LocalDateTime? = null,
    @Column(name = "is_deleted") var isDeleted: Boolean = false,

    @OneToMany(mappedBy = "settlement", cascade = [CascadeType.ALL], orphanRemoval = true)
    val details: MutableList<SettlementDetail> = mutableListOf()
) : BaseEntity() {

    // == 연관관계 편의 메서드 ==
    fun addDetail(detail: SettlementDetail) {
        details.add(detail)
        detail.settlement = this
        // 집계 필드 자동 업데이트
        this.totalGrossAmount += detail.grossAmount
        this.totalFeeAmount += detail.feeAmount
        this.totalRefundDeducted += detail.refundDeducted
        this.netSettlementAmount += detail.netAmount
    }

    fun removeDetail(detail: SettlementDetail) {
        details.remove(detail)
        detail.settlement = null
        // 집계 필드 재계산 (단순화: 전체 재순회)
        recalculateSummary()
    }

    private fun recalculateSummary() {
        this.totalGrossAmount = details.sumOf { it.grossAmount }
        this.totalFeeAmount = details.sumOf { it.feeAmount }
        this.totalRefundDeducted = details.sumOf { it.refundDeducted }
        this.netSettlementAmount = details.sumOf { it.netAmount }
    }

    // == 상태 전이 메서드 ==
    fun markAsCalculated() {
        require(status == SettlementStatus.PENDING) { "CALCULATED 로 전이할 수 없는 상태입니다: $status" }
        this.status = SettlementStatus.CALCULATED
    }

    fun markAsApproved() {
        require(status == SettlementStatus.CALCULATED) { "APPROVED 로 전이할 수 없는 상태입니다: $status" }
        this.status = SettlementStatus.APPROVED
    }

    fun markAsCompleted() {
        require(status == SettlementStatus.APPROVED || status == SettlementStatus.TRANSFERRING) {
            "COMPLETED 로 전이할 수 없는 상태입니다: $status"
        }
        this.status = SettlementStatus.COMPLETED
        this.settledAt = java.time.LocalDateTime.now()
    }

    fun markAsAdjusted() {
        require(status == SettlementStatus.COMPLETED) { "ADJUSTED 로 전이할 수 없는 상태입니다: $status" }
        this.status = SettlementStatus.ADJUSTED
    }

    fun markAsTransferring() {
        require(status == SettlementStatus.APPROVED) { "TRANSFERRING 로 전이할 수 없는 상태입니다: $status" }
        this.status = SettlementStatus.TRANSFERRING
    }

    fun markAsFailed() {
        this.status = SettlementStatus.FAILED
    }

    companion object {
        fun createSettlement(
            settlementNo: String,
            seller: User,
            periodStart: LocalDate,
            periodEnd: LocalDate
        ): Settlement {
            return Settlement(
                settlementNo = settlementNo,
                seller = seller,
                periodStart = periodStart,
                periodEnd = periodEnd,
                status = SettlementStatus.PENDING
            )
        }
    }
}