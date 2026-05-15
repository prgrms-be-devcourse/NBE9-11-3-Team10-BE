package com.team10.backend.domain.order.dto.search.seller

import com.team10.backend.domain.user.entity.User

data class SellerOrderListResponse(
    @JvmField val sellerId: Long,
    @JvmField val sales: List<SellerOrderSummaryResponse>
) {
    companion object {
        @JvmStatic
        fun of(seller: User, sales: List<SellerOrderSummaryResponse>): SellerOrderListResponse {
            return SellerOrderListResponse(
                sellerId = seller.id, // 프로퍼티 접근
                sales = sales
            )
        }
    }
}