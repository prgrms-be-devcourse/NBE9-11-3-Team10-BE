package com.team10.backend.domain.order.dto.cancel


data class CancelRequest(
    val cancelReason: String // 취소 사유 (필수)
)
