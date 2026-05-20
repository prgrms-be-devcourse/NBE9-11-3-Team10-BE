package com.team10.backend.domain.settlement.dto

data class SettlementBatchResult(
    val settlementId: Long? = null,
    val settlementNo: String? = null,
    val targetDate: java.time.LocalDate,
    val status: Status,
    val summary: Summary,
    val errors: List<ErrorDetail> = emptyList(),
    val processedAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
) {
    enum class Status { SUCCESS, PARTIAL_SUCCESS, FAILED, SKIPPED }

    data class Summary(
        val totalPaymentsProcessed: Int,
        val totalGrossAmount: Long,
        val totalFeeAmount: Long,
        val totalRefundDeducted: Long,
        val netSettlementAmount: Long,
        val settledCount: Int,
        val skippedCount: Int,
        val uncertainCount: Int
    )

    data class ErrorDetail(
        val paymentId: Long,
        val orderNumber: String,
        val reason: String,
        val errorCode: String
    )

    companion object {
        fun skipped(targetDate: java.time.LocalDate): SettlementBatchResult {
            return SettlementBatchResult(
                targetDate = targetDate,
                status = Status.SKIPPED,
                summary = Summary(0, 0, 0, 0, 0, 0, 0, 0)
            )
        }

        fun failed(targetDate: java.time.LocalDate, errors: List<ErrorDetail>): SettlementBatchResult {
            return SettlementBatchResult(
                targetDate = targetDate,
                status = Status.FAILED,
                summary = Summary(0, 0, 0, 0, 0, 0, 0, 0),
                errors = errors
            )
        }
    }
}