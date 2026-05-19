package com.team10.backend.domain.product.repository

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.user.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductRepository : JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = ["user", "user.sellerInfo"])
    override fun findAll(
        spec: Specification<Product>,
        pageable: Pageable
    ): Page<Product>

    @Query(
        """
        select p
        from Product p
        join fetch p.user u
        left join fetch u.sellerInfo
        where p.id = :productId
        """
    )
    fun findByIdWithUser(@Param("productId") productId: Long): Product?

    // 상품 엔티티 자체를 잠금 조회해야 하는 경우 사용
    // 재고 증감은 아래 원자적 UPDATE 메서드를 우선 사용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId")
    fun findByIdWithPessimisticLock(@Param("productId") productId: Long): Product?

    // 재고 부족 검증과 재고 차감을 하나의 UPDATE 문으로 원자적으로 처리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
        set p.stock = p.stock - :quantity,
            p.status = case
                when p.stock - :quantity = 0
                then com.team10.backend.domain.product.enums.ProductStatus.SOLD_OUT
                else com.team10.backend.domain.product.enums.ProductStatus.SELLING
            end
        where p.id = :productId
            and p.status <> com.team10.backend.domain.product.enums.ProductStatus.INACTIVE
            and p.stock >= :quantity
        """
    )
    fun decreaseStockAtomically(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Int
    ): Int

    // 주문 취소/결제 롤백 시 재고 복구를 원자적 UPDATE로 처리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Product p
        set p.stock = p.stock + :quantity,
            p.status = com.team10.backend.domain.product.enums.ProductStatus.SELLING
        where p.id = :productId
            and p.status <> com.team10.backend.domain.product.enums.ProductStatus.INACTIVE
        """
    )
    fun increaseStockAtomically(
        @Param("productId") productId: Long,
        @Param("quantity") quantity: Int
    ): Int

    fun countByUserAndProductNameIn(user: User, names: List<String>): Long
    fun findByProductName(name: String): Product?
    fun findByUserAndProductNameIn(user: User, names: Collection<String>): List<Product>
}