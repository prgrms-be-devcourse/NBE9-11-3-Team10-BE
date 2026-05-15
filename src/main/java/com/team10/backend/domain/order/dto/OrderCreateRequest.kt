package com.team10.backend.domain.order.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

/*
* {
  "userId": 1,
  "deliveryAddress": "서울특별시 .....",
  "items": [
    { "productId": 101, "quantity": 2 },
    { "productId": 105, "quantity": 1 }
  ]
}*/
data class OrderCreateRequest(
    @JvmField
    @NotBlank(message = "배송 주소는 필수입니다")
    val deliveryAddress: String,

    @JvmField
    @NotEmpty(message = "상품을 최소 1개 이상 선택해야 합니다")
    @field:Valid
    val orderProducts: List<OrderProductReq>
) {
    data class OrderProductReq(
        @JvmField
        val productId: Long,

        @JvmField
        @Min(1)
        val quantity: Int
    )
}