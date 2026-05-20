package com.team10.backend.domain.order.controller

import com.team10.backend.domain.user.enums.Role
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.security.CustomUserPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderDeleteControllerTest {

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
    @DisplayName("주문 삭제 성공 - 결제 대기 상태의 주문을 취소")
    fun deleteOrder_Success() {
        // 1. 유저 및 상품 세팅
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (1, 'buyer@test.com', '1', '구매자', '구매자', '010', '서울', 'ACTIVE', 'BUYER')")
        jdbcTemplate.update("INSERT INTO products (id, user_id, product_name, price, stock, type, status) VALUES (101, 1, '상품', 10000, 9, 'BOOK', 'SELLING')")

        // 2. 주문 세팅 (READY 상태)
        val orderNum = "ORD-DELETE-001"
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted) VALUES (501, 1, ?, 10000, 'PENDING', false)",
            orderNum
        )
        jdbcTemplate.update("INSERT INTO order_products (id, order_id, product_id, quantity, order_price) VALUES (701, 501, 101, 1, 10000)")
        jdbcTemplate.update("INSERT INTO order_delivery (id, order_id, delivery_address, tracking_number, delivery_status) VALUES (801, 501, '주소', 'TRK-1', 'READY')")
        jdbcTemplate.update(
            "INSERT INTO payments (id, order_id, order_number, total_amount, status, idempotency_key, type, created_at, updated_at) " +
                    "VALUES (901, 501, ?, 10000, 'PAID', ?, 'PAYMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            orderNum,
            "unique-toss-key-902"
        )

        // When & Then
        mvc.perform(
            delete("/api/v1/orders/{orderNumber}", orderNum)
                .with(authentication(getAuthentication(1L, Role.BUYER)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderNumber").value(orderNum))
            .andDo(print())

        // [참고] 환불/재고 로직이 추가되면 여기서 jdbcTemplate으로 재고가 늘어났는지 확인하는 로직을 추가할 예정
        val restoredStock = jdbcTemplate.queryForObject(
            "SELECT stock FROM products WHERE id = ?", Int::class.java, 101L
        )
        assertEquals(10, restoredStock)

        val deletedOrder = jdbcTemplate.queryForMap(
            "SELECT * FROM orders WHERE order_number = ?", orderNum
        )
        assertEquals(true, deletedOrder["is_deleted"])
    }

    @Test
    @DisplayName("주문 삭제 실패 - 이미 배송 중인 상품 (SHIPPING)")
    fun deleteOrder_Fail_AlreadyShipping() {
        // 1. 데이터 세팅
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (1, 'buyer@test.com', '1', '구매자', '구매자', '010', '서울', 'ACTIVE', 'BUYER')")
        val orderNum = "ORD-SHIPPING-001"
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted) VALUES (502, 1, ?, 10000, 'SUCCESS', false)",
            orderNum
        )

        // 배송 상태를 SHIPPING으로 설정
        jdbcTemplate.update("INSERT INTO order_delivery (id, order_id, delivery_address, tracking_number, delivery_status) VALUES (802, 502, '주소', 'TRK-2', 'SHIPPING')")
        jdbcTemplate.update(
            "INSERT INTO payments (id, order_id, order_number, total_amount, status, idempotency_key, type, created_at, updated_at) " +
                    "VALUES (902, 502, ?, 10000, 'PAID', ?, 'PAYMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            orderNum,
            "unique-toss-key-902"
        )

        // When & Then
        mvc.perform(
            delete("/api/v1/orders/{orderNumber}", orderNum)
                .with(authentication(getAuthentication(1L, Role.BUYER)))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.CANNOT_CANCEL_SHIPPING_ORDER.code))
            .andDo(print())
    }

    @Test
    @DisplayName("주문 삭제 실패 - 다른 유저의 주문을 삭제 시도")
    fun deleteOrder_Fail_AccessDenied() {
        // 1. 주인 유저(1)와 주문 생성
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (1, 'owner@test.com', '1', '주인', '주인', '010', '서울', 'ACTIVE', 'BUYER')")
        val orderNum = "ORD-OTHER-001"
        jdbcTemplate.update(
            "INSERT INTO orders (id, user_id, order_number, total_amount, status, is_deleted) VALUES (503, 1, ?, 10000, 'PENDING', false)",
            orderNum
        )
        jdbcTemplate.update("INSERT INTO order_delivery (id, order_id, delivery_status) VALUES (803, 503, 'READY')")
        jdbcTemplate.update(
            "INSERT INTO payments (id, order_id, order_number, total_amount, status, idempotency_key, type, created_at, updated_at) " +
                    "VALUES (903, 503, ?, 10000, 'PAID', ?, 'PAYMENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            orderNum,
            "unique-toss-key-902"
        )

        // 2. 공격자 유저(2) 생성
        jdbcTemplate.update("INSERT INTO users (id, email, password, name, nickname, phone_number, address, user_status, role) VALUES (2, 'hacker@test.com', '1', '해커', '해커', '010', '인천', 'ACTIVE', 'BUYER')")

        // When & Then: 유저 2가 유저 1의 주문 삭제 시도
        mvc.perform(
            delete("/api/v1/orders/{orderNumber}", orderNum)
                .with(authentication(getAuthentication(2L, Role.BUYER)))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.code))
            .andDo(print())
    }
}