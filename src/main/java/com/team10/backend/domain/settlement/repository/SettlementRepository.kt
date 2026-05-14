package com.team10.backend.domain.settlement.repository

import com.team10.backend.domain.settlement.entity.Settlement
import com.team10.backend.domain.settlement.enums.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SettlementRepository : JpaRepository<Settlement, Long> {

    // 동일 판매자 + 기간 중복 정산 방지용 조회
    fun existsBySellerIdAndPeriodStartAndPeriodEnd(
        sellerId: Long,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): Boolean

    fun findBySellerIdAndPeriodStartAndPeriodEnd(
        sellerId: Long,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): Settlement?

    // 정산 상태별 조회 (관리자용)
    fun findByStatusAndPeriodStartLessThanEqual(
        status: SettlementStatus,
        cutoffDate: LocalDate,
        pageable: Pageable
    ): Page<Settlement>

    /*
    // 미정산 결제 건 탐색용 (배치용)
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = 'PAID'
        AND p.order.user.id = :sellerId
        AND p.createdAt BETWEEN :startDate AND :endDate
        AND NOT EXISTS (
            SELECT 1 FROM SettlementDetail sd 
            WHERE sd.payment.id = p.id
        )
    """)
    fun findUnsettledPaymentsBySellerAndPeriod(
        @Param("sellerId") sellerId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<com.team10.backend.domain.order.entity.Payment>
    */
}