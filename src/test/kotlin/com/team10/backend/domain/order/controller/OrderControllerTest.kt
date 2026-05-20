package com.team10.backend.domain.order.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.team10.backend.domain.order.dto.OrderCreateRequest
import com.team10.backend.domain.order.dto.OrderCreateRequest.OrderProductReq
import com.team10.backend.domain.order.service.OrderService
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.security.CustomUserPrincipal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())

    @BeforeEach
    fun setUp() {
        cleanupDatabase()

        // 1. 기본 유저 세팅
        insertUser(1L, "buyer@test.com", "홍길동", "nickname1", "BUYER")   // 구매자
        insertUser(3L, "buyer3@test.com", "홍길동3", "nickname3", "BUYER") // 주문 없는 구매자

        insertUser(2L, "seller@test.com", "홍길동2", "nickname2", "SELLER") // 판매자
        insertUser(4L, "seller4@test.com", "홍길동4", "nickname4", "SELLER") // 판매자, 판매 상품 없음

        // 2. 기본 상품 세팅 (판매자 2L의 상품들)
        insertProduct(101L, 2L, "상품A", 10000)
        insertProduct(102L, 2L, "상품B", 20000)
        insertProduct(103L, 2L, "상품C", 30000)

        // 3. 주문 및 복합 상황 데이터 세팅
        setupDefaultOrders()
    }

    // ================= SQL 집중 관리 영역 =================
    private fun cleanupDatabase() {
        jdbcTemplate.update("DELETE FROM payments")
        jdbcTemplate.update("DELETE FROM order_products")
        jdbcTemplate.update("DELETE FROM order_delivery")
        jdbcTemplate.update("DELETE FROM orders")
        jdbcTemplate.update("DELETE FROM products")
        jdbcTemplate.update("DELETE FROM users")
    }

    private fun insertUser(id: Long, email: String, name: String, nickname: String, role: String) {
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role, created_at, updated_at) " +
                    "VALUES (?, ?, '1234', ?, ?, '010-0000-0000', '주소', 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            id, email, name, nickname, role
        )
    }

    private fun insertProduct(id: Long, userId: Long, name: String, price: Int) {
        jdbcTemplate.update(
            "INSERT INTO products (id, user_id, product_name, price, stock, type, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, 10, 'BOOK', 'SELLING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            id, userId, name, price
        )
    }

    private fun insertOrder(id: Long, userId: Long, orderNum: String, amount: Int, date: String) {
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            id, userId, orderNum, amount, "PENDING", 0, date, date
        )
    }

    private fun insertOrderProduct(id: Long, orderId: Long, productId: Long, qty: Int, price: Int) {
        jdbcTemplate.update(
            "INSERT INTO order_products (id, order_id, product_id, quantity, order_price) VALUES (?, ?, ?, ?, ?)",
            id, orderId, productId, qty, price
        )
    }

    private fun insertPayment(id: Long, orderId: Long, orderNum: String, amount: Int, status: String) {
        jdbcTemplate.update(
            "INSERT INTO payments (id, order_id, order_number, total_amount, status, idempotency_key, type, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            id, orderId, orderNum, amount, status, UUID.randomUUID().toString(), "PAYMENT"
        )
    }

    private fun setupDefaultOrders() {
        // [기존 데이터] 과거 주문 (Buyer 1L이 상품A, B 구매)
        insertOrder(501L, 1L, "ORD-OLD-001", 30000, "2024-01-01 10:00:00")
        insertOrderProduct(1001L, 501L, 101L, 1, 10000)
        insertOrderProduct(1002L, 501L, 102L, 1, 20000)
        insertPayment(901L, 501L, "ORD-OLD-001", 30000, "PAID")

        // [기존 데이터] 최신 주문 (Buyer 1L이 상품C 구매)
        insertOrder(502L, 1L, "ORD-NEW-002", 30000, "2024-04-16 10:00:00")
        insertOrderProduct(1003L, 502L, 103L, 1, 30000)
        insertPayment(902L, 502L, "ORD-NEW-002", 30000, "PAID")

        // 판매자 판매 내역 테스트용 데이터
        insertUser(99L, "other@test.com", "남판매", "남다", "SELLER")
        insertProduct(202L, 99L, "남 상품", 50000)

        val mixOrderNum = "ORD-MIX-001"
        insertOrder(601L, 1L, mixOrderNum, 60000, "2024-04-16 11:00:00")
        insertOrderProduct(701L, 601L, 101L, 1, 10000)
        insertOrderProduct(702L, 601L, 202L, 1, 50000)
        insertPayment(801L, 601L, mixOrderNum, 60000, "PAID")
    }

    private fun getMockUser(id: Long, role: Role): CustomUserPrincipal {
        return CustomUserPrincipal(id, role)
    }

    private fun getAuthentication(id: Long, role: Role): UsernamePasswordAuthenticationToken {
        val principal = getMockUser(id, role)
        return UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        )
    }

    @Test
    @DisplayName("주문 생성 성공 - 로직을 통해 합산 금액 검증")
    fun t1_2() {
        // Given
        val item1 = OrderProductReq(101L, 2)
        val item2 = OrderProductReq(102L, 2)
        val item3 = OrderProductReq(103L, 2)
        val expectedTotalAmount = 120000

        val req = OrderCreateRequest("서울특별시 강남구 테헤란로", listOf(item1, item2, item3))
        val requestJson = objectMapper.writeValueAsString(req)
        println("========================================")
        println("전송되는 JSON: $requestJson")
        println("========================================")

        // When
        val resultActions = mvc.perform(
            post("/api/v1/orders")
                .with(authentication(getAuthentication(1L, Role.BUYER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        ).andDo(print())

        // Then
        resultActions
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value(1L))
            .andExpect(jsonPath("$.data.totalAmount").value(expectedTotalAmount))
            .andExpect(jsonPath("$.data.orderNumber").exists())
    }

    @Test
    @DisplayName("단일 상품 주문 생성 성공")
    fun t1_3() {
        // Given
        val item1 = OrderProductReq(101L, 2)
        val req = OrderCreateRequest("서울특별시 강남구 테헤란로", listOf(item1))
        val requestJson = objectMapper.writeValueAsString(req)
        println("========================================")
        println("전송되는 JSON: $requestJson")
        println("========================================")

        // When & Then
        mvc.perform(
            post("/api/v1/orders")
                .with(authentication(getAuthentication(1L, Role.BUYER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalAmount").value(20000))
            .andExpect(jsonPath("$.data.orderNumber").exists())
    }

    @Test
    @DisplayName("주문 생성 실패 - 필수 입력값 누락 (Validation)")
    fun t2() {
        // Given
        val req = OrderCreateRequest("", emptyList())

        // When & Then
        mvc.perform(
            post("/api/v1/orders")
                .with(authentication(getAuthentication(1L, Role.BUYER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("주문 생성 실패 - 존재하지 않는 상품 (BusinessException)")
    fun t3() {
        // Given
        val item = OrderProductReq(999L, 1)
        val req = OrderCreateRequest("주소", listOf(item))

        // When
        val resultActions = mvc.perform(
            post("/api/v1/orders")
                .with(authentication(getAuthentication(1L, Role.BUYER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andDo(print())

        // Then
        resultActions
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.PRODUCT_NOT_FOUND.code))
            .andExpect(jsonPath("$.detail").value("상품을 찾을 수 없습니다. ID: 999"))
    }

    @Test
    @DisplayName("주문 생성 실패 - 존재하지 않는 사용자 (BusinessException)")
    fun t4() {
        // Given
        val invalidUserId = 999L
        val item = OrderProductReq(101L, 1)
        val req = OrderCreateRequest("서울특별시 강남구", listOf(item))

        // When
        val resultActions = mvc.perform(
            post("/api/v1/orders")
                .with(authentication(getAuthentication(invalidUserId, Role.BUYER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andDo(print())

        // Then
        resultActions
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.USER_NOT_FOUND.code))
            .andExpect(jsonPath("$.detail").value("해당 유저 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("구매자 주문 목록 조회 성공 - 최신순 정렬 및 대표명 검증")
    fun t5() {
        // When & Then
        mvc.perform(
            get("/api/v1/orders/buyer")
                .with(authentication(getAuthentication(1L, Role.BUYER)))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(1L))
            .andExpect(jsonPath("$.data.userName").value("홍길동"))
            .andExpect(jsonPath("$.data.orders[0].orderNumber").value("ORD-MIX-001"))
            .andExpect(jsonPath("$.data.orders[0].representativeProductName").value("상품A 외 1건"))
            .andExpect(jsonPath("$.data.orders[0].totalQuantity").value(2))
            .andExpect(jsonPath("$.data.orders[1].orderNumber").value("ORD-NEW-002"))
            .andExpect(jsonPath("$.data.orders[1].representativeProductName").value("상품C"))
            .andExpect(jsonPath("$.data.orders[1].totalQuantity").value(1))
            .andExpect(jsonPath("$.data.orders[2].orderNumber").value("ORD-OLD-001"))
            .andExpect(jsonPath("$.data.orders[2].representativeProductName").value("상품A 외 1건"))
            .andExpect(jsonPath("$.data.orders[2].totalQuantity").value(2))
            .andDo(print())
    }

    @Test
    @DisplayName("구매자 주문 목록 조회 성공 - 주문 내역이 없는 경우")
    fun t6() {
        // When & Then
        mvc.perform(
            get("/api/v1/orders/buyer")
                .with(authentication(getAuthentication(3L, Role.BUYER)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orders").isEmpty)
            .andExpect(jsonPath("$.data.userName").value("홍길동3"))
    }

    @Test
    @DisplayName("구매자 주문 목록 조회 실패 - 존재하지 않는 유저")
    fun getBuyerOrderList_UserNotFound() {
        // When & Then
        mvc.perform(
            get("/api/v1/orders/buyer")
                .with(authentication(getAuthentication(999L, Role.BUYER)))
        )
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.USER_NOT_FOUND.code))
            .andExpect(jsonPath("$.detail").value("해당 유저 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("구매자 주문 내역 조회 실패 - 구매자가 아닌 Seller ID로 조회 할 경우")
    fun getOrderList_InvalidRole() {
        // When & Then
        mvc.perform(
            get("/api/v1/orders/buyer")
                .with(authentication(getAuthentication(2L, Role.SELLER)))
        )
            .andDo(print())
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.code))
            .andExpect(jsonPath("$.detail").value("해당 리소스에 대한 접근 권한이 없습니다."))
    }

    @Test
    @DisplayName("판매자 판매 내역 조회 성공 - 데이터 정합성 및 payment 상태 검증")
    fun getSellerOrderList_Success() {
        // Given
        val auth = getAuthentication(2L, Role.SELLER)

        // When & Then
        mvc.perform(
            get("/api/v1/orders/seller")
                .with(authentication(auth))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sellerId").value(2L))
            .andExpect(jsonPath("$.data.sales.length()").value(4))
            .andExpect(jsonPath("$.data.sales[?(@.productName == '상품A' && @.totalAmount == 10000)]").exists())
            .andExpect(jsonPath("$.data.sales[?(@.productName == '남 상품')]").doesNotExist())
            .andDo(print())
    }

    @Test
    @DisplayName("판매자 판매 내역 조회 실패 - 판매자가 아닌 BUYER ID로 조회 할 경우")
    fun getSellerOrderList_InvalidRole() {
        // Given
        val auth = getAuthentication(1L, Role.BUYER)

        // When & Then
        mvc.perform(
            get("/api/v1/orders/seller")
                .with(authentication(auth))
        )
            .andDo(print())
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.code))
            .andExpect(jsonPath("$.detail").value("해당 리소스에 대한 접근 권한이 없습니다."))
    }

    @Test
    @DisplayName("판매자 판매 내역 조회 성공 - 판매 내역이 전혀 없는 경우")
    fun getSellerOrderList_Empty_Success() {
        // Given
        val auth = getAuthentication(4L, Role.SELLER)

        // When & Then
        mvc.perform(
            get("/api/v1/orders/seller")
                .with(authentication(auth))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sales").isArray)
            .andExpect(jsonPath("$.data.sales.length()").value(0))
            .andDo(print())
    }

    @Test
    @DisplayName("판매자 판매 내역 조회 실패 - 존재하지 않는 유저 ID")
    fun getSellerOrderList_UserNotFound() {
        // Given
        val auth = getAuthentication(9999L, Role.SELLER)

        // When & Then
        mvc.perform(
            get("/api/v1/orders/seller")
                .with(authentication(auth))
        )
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.USER_NOT_FOUND.code))
            .andExpect(jsonPath("$.detail").value("해당 유저 정보를 찾을 수 없습니다."))
    }

    @Test
    @DisplayName("주문 생성 실패 - 재고 수량 부족 (BusinessException)")
    fun t1_4_fail_insufficient_stock() {
        // Given
        val item = OrderProductReq(101L, 11)
        val req = OrderCreateRequest("서울특별시 강남구", listOf(item))
        val auth = getAuthentication(1L, Role.BUYER)

        // When
        val resultActions = mvc.perform(
            post("/api/v1/orders")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        ).andDo(print())

        // Then
        resultActions
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.INSUFFICIENT_STOCK.code))
            .andExpect(jsonPath("$.detail").value("재고가 부족합니다."))
    }
}