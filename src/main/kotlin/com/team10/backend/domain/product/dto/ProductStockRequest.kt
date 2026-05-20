package com.team10.backend.domain.product.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Min

data class ProductStockRequest(
        @param:JsonProperty("stock")
        @field:Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        val stock: Int
)