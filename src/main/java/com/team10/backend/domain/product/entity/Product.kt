package com.team10.backend.domain.product.entity

import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.*

@Entity
@Table(name = "products")
class Product(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: ProductType,

    @Column(name = "product_name", nullable = false)
    var productName: String,

    @Column(columnDefinition = "TEXT")
    var description: String?,

    @Column(nullable = false)
    var price: Int,

    @Column(nullable = false)
    var stock: Int,

    @Column(name = "image_url")
    var imageUrl: String?
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = if (stock == 0) ProductStatus.SOLD_OUT else ProductStatus.SELLING
        protected set

    init {
        validateStock(stock)
    }

    fun update(
        type: ProductType,
        productName: String,
        description: String?,
        price: Int,
        imageUrl: String?,
        status: ProductStatus
    ) {
        this.type = type
        this.productName = productName
        this.description = description
        this.price = price
        this.imageUrl = imageUrl
        this.status = status
    }

    fun updateStock(stock: Int) {
        validateStock(stock)
        applyStock(stock)
    }

    fun decreaseStock(quantity: Int) {
        validateQuantity(quantity)
        validateActive()

        if (this.stock < quantity) {
            throw BusinessException(ErrorCode.INSUFFICIENT_STOCK)
        }

        applyStock(this.stock - quantity)
    }

    fun increaseStock(quantity: Int) {
        validateQuantity(quantity)
        validateActive()

        applyStock(this.stock + quantity)
    }

    fun inactivate() {
        this.status = ProductStatus.INACTIVE
    }

    private fun applyStock(stock: Int) {
        this.stock = stock
        updateStatusByStock()
    }

    private fun validateActive() {
        if (this.status == ProductStatus.INACTIVE) {
            throw BusinessException(ErrorCode.PRODUCT_ALREADY_INACTIVE)
        }
    }

    private fun updateStatusByStock() {
        if (this.status == ProductStatus.INACTIVE) return
        this.status = if (this.stock == 0) ProductStatus.SOLD_OUT else ProductStatus.SELLING
    }

    private fun validateStock(stock: Int) {
        if (stock < 0) {
            throw BusinessException(ErrorCode.INVALID_STOCK)
        }
    }

    private fun validateQuantity(quantity: Int) {
        if (quantity <= 0) {
            throw BusinessException(ErrorCode.INVALID_STOCK_QUANTITY)
        }
    }
}