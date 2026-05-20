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

    fun countBySellerIdAndPeriodStartAndPeriodEnd(
        sellerId: Long,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): Long

    // 정산 상태별 조회 (관리자용)
    fun findByStatusAndPeriodStartLessThanEqual(
        status: SettlementStatus,
        cutoffDate: LocalDate,
        pageable: Pageable
    ): Page<Settlement>
}