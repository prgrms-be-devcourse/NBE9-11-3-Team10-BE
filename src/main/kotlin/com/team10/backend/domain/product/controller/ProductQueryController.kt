package com.team10.backend.domain.product.controller

import com.team10.backend.domain.product.dto.ProductDetailResponse
import com.team10.backend.domain.product.dto.ProductPageResponse
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.product.service.ProductService
import com.team10.backend.global.dto.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Validated
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "상품 조회", description = "상품 조회 API")
class ProductQueryController(
    private val productService: ProductService
) {
    @GetMapping
    @Operation(
        summary = "상품 전체 조회",
        description = "등록된 상품 목록을 최신순으로 조회하며, 페이지 및 필터 조건을 지원합니다."
    )
    fun list(
        @RequestParam(defaultValue = "1")
        @Min(value = 1, message = "page는 1 이상이어야 합니다.")
        page: Int,

        @RequestParam(defaultValue = "10")
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        size: Int,

        @RequestParam(required = false)
        type: ProductType?,

        @RequestParam(required = false)
        status: ProductStatus?,

        @RequestParam(required = false)
        sellerId: Long?
    ): ApiResponse<ProductPageResponse> =
        ApiResponse.ok(productService.list(page - 1, size, type, status, sellerId))

    @GetMapping("/{productId}")
    @Operation(summary = "상품 상세 조회", description = "상품 ID로 특정 상품의 상세 정보를 조회합니다.")
    fun detail(
        @PathVariable productId: Long
    ): ApiResponse<ProductDetailResponse> =
        ApiResponse.ok(productService.detail(productId))
}