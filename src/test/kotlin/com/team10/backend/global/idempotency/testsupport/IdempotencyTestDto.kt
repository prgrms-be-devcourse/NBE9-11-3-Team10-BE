package com.team10.backend.global.idempotency.testsupport

import java.time.LocalDateTime

data class IdempotencyTestRequest(
    val userId: String,
    val amount: Long,
    val description: String? = null
)

data class IdempotencyTestResponse(
    val transactionId: String,
    val processedAt: LocalDateTime,
    val message: String
)