package com.team10.backend.domain.order.dto.search.seller

import com.team10.backend.domain.user.entity.User

data class SellerOrderListResponse(
     val sellerId: Long,
     val sales: List<SellerOrderSummaryResponse>
) {
    companion object {
        fun of(seller: User, sales: List<SellerOrderSummaryResponse>): SellerOrderListResponse {
            return SellerOrderListResponse(
                sellerId = seller.id, // 프로퍼티 접근
                sales = sales
            )
        }
    }
}