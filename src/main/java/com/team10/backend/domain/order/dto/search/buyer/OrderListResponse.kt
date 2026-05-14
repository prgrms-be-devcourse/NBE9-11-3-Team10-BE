package com.team10.backend.domain.order.dto.search.buyer

import com.team10.backend.domain.user.entity.User

/**/
data class OrderListResponse(
    @JvmField val userId: Long,
    @JvmField val userName: String,
    @JvmField val orders: List<OrderSummaryResponse> // MutableList와 ? 제거
) {
    companion object {
        @JvmStatic
        fun of(user: User, orders: List<OrderSummaryResponse>): OrderListResponse {
            return OrderListResponse(
                userId = user.id,   // get 메서드 대신 프로퍼티 접근
                userName = user.name,
                orders = orders
            )
        }
    }
}