package com.team10.backend.domain.settlement.service

import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.domain.settlement.dto.SettlementBatchResult
import com.team10.backend.domain.settlement.entity.Settlement
import com.team10.backend.domain.settlement.entity.SettlementDetail
import com.team10.backend.domain.settlement.repository.SettlementRepository
import com.team10.backend.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class SettlementBatchService(
    private val paymentRepository: PaymentRepository,
    private val settlementRepository: SettlementRepository,
    private val userRepository: UserRepository,
    private val feeCalculator: FeeCalculator,
    private val reconciliationService: SettlementReconciliationService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val CHUNK_SIZE = 100 // 대량 처리 시 메모리 관리용 청크 크기

    /**
     * [배치 진입점] 특정 날짜의 미정산 결제 건을 대상으로 정산 실행
     * - 멱등성 보장: 동일 날짜+판매자 정산 이력 존재 시 스킵
     * - 청크 기반 처리: 대량 데이터 메모리 오버플로우 방지
     * - 예외 격리: 개별 결제 건 실패 시 전체 롤백하지 않고 로그 기록 후 계속 진행
     */
    @Transactional
    fun executeDailySettlement(sellerId: Long, targetDate: LocalDate, force: Boolean = false): SettlementBatchResult {
        log.info("Starting settlement batch: sellerId=$sellerId, targetDate=$targetDate, force=$force")

        // 1. 멱등성 체크: 동일 기간 정산 이력 존재 시 스킵
        if (!force && settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                sellerId, targetDate, targetDate
            )
        ) {
            log.warn("Settlement already exists for seller=$sellerId, date=$targetDate. Use force=true to re-run.")
            return SettlementBatchResult.skipped(targetDate)
        }

        // 2. 판매자 존재 여부 검증
        val seller = userRepository.findById(sellerId)
            .orElseThrow { IllegalArgumentException("Seller not found: $sellerId") }

        // 3. 미정산 결제 건 조회 (청크 단위로 처리하기 위해 ID 기준 페이징 준비)
        val unsettledPayments = paymentRepository
            .findUnsettledPaymentsBySellerAndPeriod(sellerId, targetDate, targetDate.plusDays(1))

        if (unsettledPayments.isEmpty()) {
            log.info("No unsettled payments found for seller=$sellerId, date=$targetDate")
            return SettlementBatchResult(
                targetDate = targetDate,
                status = SettlementBatchResult.Status.SUCCESS,
                summary = SettlementBatchResult.Summary(0, 0, 0, 0, 0, 0, 0, 0)
            )
        }


        // 4. PG 대조(Reconciliation) 수행 (현재는 체크를 별도로 하지 않고 모두 정상 처리됨, 추후 추가 예정)
        val reconciliation = reconciliationService.reconcileInternal(unsettledPayments, sellerId, targetDate)

        // 5. 불일치 건은 `UNCERTAIN` 상태로 마킹 후 제외
        reconciliation.unmatched.forEach { detail ->
            detail.payment.markAsUncertain()
            // paymentRepository.save(detail.payment) // JPA 영속성 컨텍스트 자동 감지
            log.warn("Payment marked UNCERTAIN: orderNumber=${detail.payment.orderNumber}, reason=${detail.reason}")
        }


        // 6. 정산 마스터 생성
        val settlementNo = generateSettlementNo(sellerId, targetDate)
        val settlement = Settlement.createSettlement(
            settlementNo = settlementNo,
            seller = seller,
            periodStart = targetDate,
            periodEnd = targetDate
        )

        // 7. 청크 단위로 정산 명세 생성 및 집계
        val errors = mutableListOf<SettlementBatchResult.ErrorDetail>()
        var settledCount = 0
        var totalGross = 0L
        var totalFee = 0L
        var totalRefund = 0L

        reconciliation.matched.chunked(CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
            log.debug("Processing chunk ${chunkIndex + 1}/${(reconciliation.matched.size + CHUNK_SIZE - 1) / CHUNK_SIZE}")

            chunk.forEach { payment ->
                try {
                    val policy = feeCalculator.getPolicy(payment)
                    val feeAmount = policy.calculate(payment.totalAmount.toLong())
                    val refundDeducted = 0L // 부분 취소 확장 시 여기에 로직 추가

                    val detail = SettlementDetail.create(
                        payment = payment,
                        sellerId = sellerId,
                        grossAmount = payment.totalAmount.toLong(),
                        feeAmount = feeAmount,
                        refundDeducted = refundDeducted
                    )
                    settlement.addDetail(detail)
                    settledCount++
                    totalGross += payment.totalAmount.toLong()
                    totalFee += feeAmount
                    totalRefund += refundDeducted

                } catch (e: Exception) {
                    log.error("Failed to create settlement detail for payment ${payment.orderNumber}: ${e.message}", e)
                    errors.add(SettlementBatchResult.ErrorDetail(
                        paymentId = payment.id ?: 0L,
                        orderNumber = payment.orderNumber,
                        reason = e.message ?: "Unknown error",
                        errorCode = "CALCULATION_ERROR"
                    ))
                }
            }
            // 청크 단위 플러시 (선택사항: 대량 데이터 시 메모리 최적화)
            // entityManager.flush(); entityManager.clear();
        }

        // 8. 정산 상태 전이 및 저장
        settlement.markAsCalculated()
        val savedSettlement = settlementRepository.save(settlement)

        // 9. 결과 집계
        val netAmount = totalGross - totalFee - totalRefund
        val summary = SettlementBatchResult.Summary(
            totalPaymentsProcessed = reconciliation.matched.size + reconciliation.unmatched.size,
            totalGrossAmount = totalGross,
            totalFeeAmount = totalFee,
            totalRefundDeducted = totalRefund,
            netSettlementAmount = netAmount,
            settledCount = settledCount,
            skippedCount = reconciliation.unmatched.size,
            uncertainCount = reconciliation.unmatched.size
        )

        val status = when {
            errors.isEmpty() && reconciliation.unmatched.isEmpty() -> SettlementBatchResult.Status.SUCCESS
            errors.isEmpty() -> SettlementBatchResult.Status.PARTIAL_SUCCESS
            else -> SettlementBatchResult.Status.FAILED
        }

        log.info("Settlement batch completed: settlementNo=$settlementNo, status=$status, settled=$settledCount, errors=${errors.size}")

        return SettlementBatchResult(
            settlementId = savedSettlement.id,
            settlementNo = savedSettlement.settlementNo,
            targetDate = targetDate,
            status = status,
            summary = summary,
            errors = errors
        )
    }

    /**
     * [스케줄러 연동용] 매일 자정 이후 T+1 정산 자동 실행
     * - 모든 활성 판매자에 대해 병렬 또는 순차 실행 가능
     */
    @Scheduled(cron = "0 30 0 * * *") // 매일 00:30 실행
    @Transactional
    fun executeAllSellersDailySettlement() {
        val sellerIds = userRepository.findAllSellerIds()

        sellerIds.forEach { sellerId ->
            try {
                val result = executeDailySettlement(sellerId, LocalDate.now())
                if (result.status != SettlementBatchResult.Status.SUCCESS) {
                    log.warn("Partial/failed settlement for seller=$sellerId: ${result.status}, errors=${result.errors.size}")
                    // TODO: 관리자 알림 또는 재시도 큐 전송
                }
            } catch (e: Exception) {
                log.error("Critical error in settlement batch for seller=$sellerId: ${e.message}", e)
                // TODO: 장애 알림 (Slack, PagerDuty 등)
            }
        }
    }

    // === 유틸리티 ===
    private fun generateSettlementNo(sellerId: Long, date: LocalDate): String {
        // 예: STL-20260514-0001 (날짜 + 시퀀스)
        // 실제 운영 시: Redis INCR 또는 DB 시퀀스 테이블 활용 권장
        val datePart = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "STL-$datePart-${sellerId.toString().padEnd(4, '0')}"
    }
}