package com.team10.backend.domain.product.repository

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.user.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface ProductRepository : JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    fun findByType(type: ProductType, pageable: Pageable): Page<Product>
    fun findByStatus(status: ProductStatus, pageable: Pageable): Page<Product>
    fun findByStatusNot(status: ProductStatus, pageable: Pageable): Page<Product>
    fun findByTypeAndStatus(type: ProductType, status: ProductStatus, pageable: Pageable): Page<Product>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId")
    fun findByIdWithPessimisticLock(@Param("productId") productId: Long): Optional<Product>

    fun countByUserAndProductNameIn(user: User, names: List<String>): Long
    fun findByProductName(name: String): Product?
    fun findByUserAndProductNameIn(user: User, names: Collection<String>): List<Product>
}
