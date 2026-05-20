package com.team10.backend.domain.settlement.repository

import com.team10.backend.domain.settlement.entity.SettlementDetail
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementDetailRepository : JpaRepository<SettlementDetail, Long> {

    // 판매자별 정산 내역 조회 (API 용)
    @Query("""
        SELECT sd FROM SettlementDetail sd
        WHERE sd.sellerId = :sellerId
        AND sd.settlement.status = :status
        ORDER BY sd.createdAt DESC
    """)
    fun findBySellerIdAndSettlementStatus(
        @Param("sellerId") sellerId: Long,
        @Param("status") status: String,
        pageable: Pageable
    ): Page<SettlementDetail>

    // 특정 결제 건이 정산 명세에 포함되었는지 확인
    fun existsByPaymentId(paymentId: Long): Boolean

    // 정산 ID 로 명세 리스트 조회 (상세 조회용)
    fun findBySettlementIdOrderByCreatedAtDesc(settlementId: Long): List<SettlementDetail>
}