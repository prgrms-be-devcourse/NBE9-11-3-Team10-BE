package com.team10.backend.domain.order.repository

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.RequestType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByOrderNumber(orderNumber: String): Payment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderNumber = :orderNumber")
    fun findByOrderNumberForUpdate(@Param("orderNumber") orderNumber: String): Payment?

    // 1. 해당 주문의 가장 최신 시도 기록 조회
    fun findFirstByOrderOrderByCreatedAtDesc(order: Order): Payment?

    // 2. 비관적 락을 위한 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Payment?

    fun findAllByOrderOrderByCreatedAtAsc(order: Order): List<Payment>

    fun findByOrderNumberAndType(orderNumber: String, type: RequestType): Payment?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Payment p 
        SET p.status = com.team10.backend.domain.order.enums.PaymentStatus.PENDING 
        WHERE p.id = :id 
          AND p.status = com.team10.backend.domain.order.enums.PaymentStatus.UNCERTAIN
    """)
    fun updateStatusFromUncertainToPending(@Param("id") id: Long): Int

    // 미정산 결제 건 탐색용 (배치용)
    @Query("""
    SELECT DISTINCT p FROM Payment p
    JOIN p.order o
    JOIN OrderProducts op ON op.order = o
    JOIN Product prod ON prod = op.product
    JOIN User u On u = prod.user
    WHERE u.id = :sellerId
      AND p.status = 'PAID'
      AND p.createdAt BETWEEN :startDate AND :endDate
      AND NOT EXISTS (
        SELECT 1 FROM SettlementDetail sd WHERE sd.payment = p
      )
""")
    fun findUnsettledPaymentsBySellerAndPeriod(
        @Param("sellerId") sellerId: Long,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<Payment>
}
