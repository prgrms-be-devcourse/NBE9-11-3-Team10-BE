package com.team10.backend.domain.product.controller

import com.team10.backend.domain.product.dto.*
import com.team10.backend.domain.product.service.ProductService
import com.team10.backend.global.dto.ApiResponse
import com.team10.backend.global.security.CustomUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/stores/me/products")
@Tag(name = "상품 관리", description = "상품 등록/수정/삭제 API")
class ProductCommandController(
    private val productService: ProductService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "상품 등록", description = "판매자가 신규 상품을 등록합니다.")
    fun create(
        @AuthenticationPrincipal principal: CustomUserPrincipal,
        @Valid @RequestBody request: ProductCreateRequest
    ): ApiResponse<ProductDetailResponse> {
        return ApiResponse.ok(productService.create(principal.userId, request))
    }

    @PutMapping("/{productId}")
    @Operation(summary = "상품 수정", description = "판매자가 등록한 상품 정보를 수정합니다.")
    fun update(
        @AuthenticationPrincipal principal: CustomUserPrincipal,
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductUpdateRequest
    ): ApiResponse<ProductDetailResponse> {
        return ApiResponse.ok(productService.update(principal.userId, productId, request))
    }

    @PatchMapping("/{productId}/inactive")
    @Operation(summary = "상품 삭제(비활성화)", description = "판매자가 등록한 상품을 삭제합니다.")
    fun inactive(
        @AuthenticationPrincipal principal: CustomUserPrincipal,
        @PathVariable productId: Long
    ): ApiResponse<ProductInactiveResponse> {
        return ApiResponse.ok(productService.inactive(principal.userId, productId))
    }

    @PatchMapping("/{productId}/stock")
    @Operation(summary = "상품 재고 수정", description = "판매자가 등록한 상품의 재고를 수정합니다.")
    fun updateStock(
        @AuthenticationPrincipal principal: CustomUserPrincipal,
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductStockRequest
    ): ApiResponse<ProductStockResponse> {
        return ApiResponse.ok(productService.updateStock(principal.userId, productId, request))
    }
}