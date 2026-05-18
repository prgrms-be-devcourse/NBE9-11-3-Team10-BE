package com.team10.backend.domain.order.controller

import com.team10.backend.domain.user.enums.Role
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.security.CustomUserPrincipal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class OrderDetailControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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

    @BeforeEach
    fun cleanUp() {
        // 테이블 간의 외래키 제약 조건을 고려하여 자식 테이블부터 순서대로 삭제
        jdbcTemplate.update("DELETE FROM payments")
        jdbcTemplate.update("DELETE FROM order_products")
        jdbcTemplate.update("DELETE FROM order_delivery")
        jdbcTemplate.update("DELETE FROM orders")
        jdbcTemplate.update("DELETE FROM products")
        jdbcTemplate.update("DELETE FROM users")
    }

    @Test
    @DisplayName("구매자 주문 상세 조회 성공 - 구매자 본인의 주문 조회")
    fun getOrderDetail_Buyer_Success() {
        // 1. 유저 및 상품 세팅
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) " +
                    "VALUES (1, 'buyer@test.com', '1234', '홍길동', '길동이', '010', '서울', 'ACTIVE', 'BUYER')"
        )
        jdbcTemplate.update(
            "INSERT INTO products (id, user_id, product_name, price, stock, type, status, created_at, updated_at) " +
                    "VALUES (101, 1, '테스트 상품', 10000, 100, 'BOOK', 'SELLING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
        )

        // 2. 주문 세팅 (구매자 1, 상품 101, 주문 501)
        val orderNum = "ORD-DETAIL-001"
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted, created_at, updated_at) " +
                    "VALUES (501, 1, ?, 10000, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            orderNum,
            "PENDING",
            false
        )
        jdbcTemplate.update("INSERT INTO order_products (id, order_id, product_id, quantity, order_price) VALUES (701, 501, 101, 1, 10000)")
        jdbcTemplate.update("INSERT INTO order_delivery (id, order_id, delivery_address, tracking_number) VALUES (801, 501, '서울시 강남구', 'TRK-123')")
        jdbcTemplate.update(
            "INSERT INTO payments (id, order_id, order_number, total_amount, status, idempotency_key, type, created_at, updated_at) " +
                    "VALUES (901, 501, ?, 10000, 'PAID', ?, 'PAYMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            orderNum,
            "unique-toss-key-902"
        )

        // When & Then
        mvc.perform(
            get("/api/v1/orders/{orderNumber}", orderNum)
                .with(authentication(getAuthentication(1L, Role.BUYER)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderNumber").value(orderNum))
            .andExpect(jsonPath("$.data.delivery.deliveryAddress").value("서울시 강남구"))
            .andExpect(jsonPath("$.data.orderItems[0].productId").value(101))
            .andDo(print())
    }

    @Test
    @DisplayName("판매자 주문 상세 조회 성공 - 판매자가 본인 상품이 포함된 주문 조회")
    fun getOrderDetail_Seller_Success() {
        // 1. 데이터 준비: 판매자(10), 구매자(20), 주문(502)
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (10, 'seller@test.com', '1234', '이판매', '판매왕', '010', '서울', 'ACTIVE', 'SELLER')")
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (20, 'buyer@test.com', '1234', '김구매', '구매왕', '010', '부산', 'ACTIVE', 'BUYER')")
        jdbcTemplate.update("INSERT INTO products (id, user_id, product_name, price, stock, type, status) VALUES (201, 10, '맥북', 10000, 10, 'BOOK', 'SELLING')")

        val orderNum = "ORD-SELLER-001"
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted, created_at, updated_at) " +
                    "VALUES (502, 20, ?, 10000, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            orderNum,
            "PENDING",
            false
        )
        jdbcTemplate.update("INSERT INTO order_products (id, order_id, product_id, quantity, order_price) VALUES (702, 502, 201, 1, 10000)")
        jdbcTemplate.update("INSERT INTO order_delivery (id, order_id, delivery_address, tracking_number) VALUES (802, 502, '부산시 수영구', 'TRK-999')")
        jdbcTemplate.update(
            "INSERT INTO payments (id, order_id, order_number, total_amount, status, idempotency_key, type, created_at, updated_at) " +
                    "VALUES (902, 502, ?, 10000, 'PAID', ?, 'PAYMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            orderNum,
            "unique-toss-key-902"
        )

        // When & Then: 판매자 ID(10)로 조회 시 성공해야 함
        mvc.perform(
            get("/api/v1/orders/{orderNumber}", orderNum)
                .with(authentication(getAuthentication(10L, Role.SELLER)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderNumber").value(orderNum))
            .andExpect(jsonPath("$.data.orderItems[0].productName").value("맥북"))
            .andDo(print())
    }

    @Test
    @DisplayName("주문 상세 조회 실패 - 구매자가 본인이 아니라 타인의 주문 조회 시 ACCESS_DENIED")
    fun getOrderDetail_Buyer_AccessDenied() {
        // 1. 진짜 주인(유저 1)과 그의 주문을 먼저 생성
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (1, 'owner@test.com', '1', '원래주인', '주인', '010', '서울', 'ACTIVE', 'BUYER')")
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted, created_at, updated_at) " +
                    "VALUES (501, 1, ?, 10000, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "ORD-DETAIL-001",
            "PENDING",
            false
        )
        jdbcTemplate.update("INSERT INTO products (id, user_id, product_name, price, stock, type, status) VALUES (101, 1, '테스트상품', 10000, 10, 'BOOK', 'SELLING')")
        jdbcTemplate.update("INSERT INTO order_products (id, order_id, product_id, quantity, order_price) VALUES (701, 501, 101, 1, 10000)")
        jdbcTemplate.update("INSERT INTO order_delivery (id, order_id, delivery_address, tracking_number) VALUES (801, 501, '서울시...', 'TRK-123')")

        // 2. 외부 악성 유저(유저 2) 생성
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (2, 'thief@test.com', '1', '박구매', '박박', '010', '인천', 'ACTIVE', 'BUYER')")

        // When & Then: 유저 2(2L)가 유저 1의 주문번호로 조회 시도 시 403 차단 확인
        mvc.perform(
            get("/api/v1/orders/{orderNumber}", "ORD-DETAIL-001")
                .with(authentication(getAuthentication(2L, Role.BUYER)))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.code))
            .andDo(print())
    }

    @Test
    @DisplayName("주문 상세 조회 실패 - B 판매자가 A판매자의 판매내역에 접근하려고 할 경우")
    fun getOrderDetail_Seller_AccessDenied() {
        // 1. 판매자A(유저 10)와 그 판매자의 상품이 담긴 주문 생성
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (10, 'sellerA@test.com', '1', '판매자A', 'A', '010', '서울', 'ACTIVE', 'SELLER')")
        jdbcTemplate.update("INSERT INTO products (id, user_id, product_name, price, stock, type, status) VALUES (201, 10, 'A상품', 10000, 10, 'BOOK', 'SELLING')")

        // 구매자(20)가 판매자A의 상품을 주문함
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (20, 'buyer@test.com', '1', '구매자', 'B', '010', '부산', 'ACTIVE', 'BUYER')")
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted, created_at, updated_at) " +
                    "VALUES (601, 20, ?, 10000, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "ORD-SELLER-001",
            "PENDING",
            false
        )
        jdbcTemplate.update("INSERT INTO order_products (id, order_id, product_id, quantity, order_price) VALUES (701, 601, 201, 1, 10000)")
        jdbcTemplate.update("INSERT INTO order_delivery (id, order_id, delivery_address, tracking_number) VALUES (901, 601, '서울시...', 'TRK-123')")

        // 2. 이 주문과 전혀 상관없는 판매자 B(유저 11) 생성
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (11, 'sellerB@test.com', '1', '김판매', 'B판매', '010', '대구', 'ACTIVE', 'SELLER')")

        // When & Then: 판매자 B(11L)가 판매자 A의 주문 조회 시도 시 403 차단 확인
        mvc.perform(
            get("/api/v1/orders/{orderNumber}", "ORD-SELLER-001")
                .with(authentication(getAuthentication(11L, Role.SELLER)))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.code))
            .andDo(print())
    }

    @Test
    @DisplayName("주문 상세 조회 실패 - 존재하지 않는 주문 번호")
    fun getOrderDetail_OrderNotFound() {
        // When & Then: 실재하지 않는 주문 번호 조회 시 404 확인
        mvc.perform(
            get("/api/v1/orders/{orderNumber}", "NON-EXIST-ORD")
                .with(authentication(getAuthentication(1L, Role.BUYER)))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.ORDER_NOT_FOUND.code))
            .andExpect(jsonPath("$.detail").value("주문 내역을 찾을 수 없습니다."))
            .andDo(print())
    }
}