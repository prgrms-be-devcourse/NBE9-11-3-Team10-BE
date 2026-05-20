package com.team10.backend.domain.order.controller

import com.team10.backend.domain.order.dto.OrderCreateRequest
import com.team10.backend.domain.order.dto.OrderDeleteResponse
import com.team10.backend.domain.order.dto.OrderResponse
import com.team10.backend.domain.order.dto.confirm.ConfirmRequest
import com.team10.backend.domain.order.dto.confirm.TossConfirmResponse
import com.team10.backend.domain.order.dto.search.OrderDetailResponse
import com.team10.backend.domain.order.dto.search.buyer.OrderListResponse
import com.team10.backend.domain.order.dto.search.seller.SellerOrderListResponse
import com.team10.backend.domain.order.service.OrderService
import com.team10.backend.domain.order.service.PaymentService
import com.team10.backend.global.dto.ApiResponse
import com.team10.backend.global.idempotency.Idempotent
import com.team10.backend.global.security.CustomUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "주문", description = "주문 관련 API")
class OrderController(
    private val orderService: OrderService,
    private val paymentService: PaymentService
) {

    @Idempotent(lockTtlSec = 10, cacheTtlSec = 180)
    @PostMapping("/confirm")
    @Operation(summary = "주문 검증", description = "상품을 구매할때 토스 페이먼트 api를 호출하여 검증합니다.")
    fun confirmPayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: ConfirmRequest): ApiResponse<TossConfirmResponse> {
        val response = paymentService.confirmPayment(request,idempotencyKey)
        return ApiResponse.ok(response)
    }

    @PostMapping
    @Operation(summary = "주문 등록", description = "구매자가 상품을 구매할때 주문을 생성합니다.")
    fun createOrder(
        @AuthenticationPrincipal currentUser: CustomUserPrincipal,
        @RequestBody @Valid req: OrderCreateRequest
    ): ApiResponse<OrderResponse> {
        val response = orderService.createOrder(currentUser.userId, req)
        return ApiResponse.ok(response)
    }

    @GetMapping("/buyer")
    @Operation(summary = "구매자 주문 전체 조회", description = "유저가 본인 주문내역 전체를 확인할 수 있습니다.")
    fun getBuyerOrderList(@AuthenticationPrincipal currentUser: CustomUserPrincipal): ApiResponse<OrderListResponse> {
        val orderListResponse = orderService.getBuyerOrderList(currentUser.userId)
        return ApiResponse.ok(orderListResponse)
    }

    @GetMapping("/seller")
    @Operation(summary = "판매자 판매 내역 전체 조회", description = "판매자가 본인 판매내역 전체를 확인할 수 있습니다.")
    fun getSellerOrderList(@AuthenticationPrincipal currentUser: CustomUserPrincipal): ApiResponse<SellerOrderListResponse> {
        val sellerOrderListResponse = orderService.getSellerOrderList(currentUser.userId)
        return ApiResponse.ok(sellerOrderListResponse)
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "유저 주문 상세 내역 조회(판매자, 구매자)", description = "유저(판매자,구매자)가 주문 내역을 상세하게 확인할 수 있습니다.")
    fun getBuyerOrderDetail(
        @AuthenticationPrincipal currentUser: CustomUserPrincipal,
        @PathVariable("orderNumber") orderNumber: String
    ): ApiResponse<OrderDetailResponse> {
        val orderDetailResponse = orderService.getOrderDetail(currentUser.userId, orderNumber)
        return ApiResponse.ok(orderDetailResponse)
    }

    @DeleteMapping("/{orderNumber}")
    @Operation(summary = "주문 삭제(취소,환불)", description = "주문 번호를 통해 주문을 삭제(소프트 딜리트)합니다.")
    fun deleteOrder(
        @AuthenticationPrincipal currentUser: CustomUserPrincipal,
        @PathVariable("orderNumber") orderNumber: String
    ): ApiResponse<OrderDeleteResponse> {
        orderService.deleteOrderSoft(currentUser.userId, orderNumber)
        val response = OrderDeleteResponse(orderNumber, "성공적으로 삭제되었습니다.")
        return ApiResponse.ok(response)
    }
}
