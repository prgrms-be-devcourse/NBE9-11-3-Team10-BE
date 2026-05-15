package com.team10.backend.domain.order.repository

import com.team10.backend.domain.order.entity.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface OrderRepository : JpaRepository<Order, Long> {

    // 유저 ID로 주문 목록을 찾되, 최신 주문이 위로 오도록 정렬
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<Order>

    // orderNumber로 찾을 때 Fetch Join을 사용하여 쿼리 한 번으로 조회
    @Query("""
        select o from Order o 
        join fetch o.delivery 
        join fetch o.orderProducts op 
        join fetch op.product 
        where o.orderNumber = :orderNumber
    """)
    fun findByOrderNumberWithDetails(@Param("orderNumber") orderNumber: String): Order?

    fun findByOrderNumber(orderNumber: String): Order?
}
