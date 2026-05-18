package com.team10.backend.domain.product.entity

import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.fixture.ProductFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ProductTest {

    @Test
    @DisplayName("재고 차감 성공")
    fun decreaseStock_success() {
        val product = ProductFixture.createSelling(stock = 10)

        product.decreaseStock(3)

        assertThat(product.stock).isEqualTo(7)
        assertThat(product.status).isEqualTo(ProductStatus.SELLING)
    }

    @Test
    @DisplayName("재고를 모두 차감하면 상태가 SOLD_OUT으로 변경")
    fun decreaseStock_success_soldOutWhenZero() {
        val product = ProductFixture.createSelling(stock = 3)

        product.decreaseStock(3)

        assertThat(product.stock).isEqualTo(0)
        assertThat(product.status).isEqualTo(ProductStatus.SOLD_OUT)
    }

    @Test
    @DisplayName("재고보다 많이 차감하면 예외 발생")
    fun decreaseStock_fail_insufficientStock() {
        val product = ProductFixture.createSelling(stock = 3)

        val exception = assertThrows<BusinessException> {
            product.decreaseStock(5)
        }

        assertThat(exception.message).isEqualTo(ErrorCode.INSUFFICIENT_STOCK.message)
    }

    @Test
    @DisplayName("재고 증가 성공")
    fun increaseStock_success() {
        val product = ProductFixture.createSoldOut()

        product.increaseStock(5)

        assertThat(product.stock).isEqualTo(5)
        assertThat(product.status).isEqualTo(ProductStatus.SELLING)
    }

    @Test
    @DisplayName("0 이하 수량 증가 요청 시 예외가 발생한다")
    fun increaseStock_fail_invalidQuantity() {
        val product = ProductFixture.createSelling(stock = 10)

        assertThrows<BusinessException> {
            product.increaseStock(0)
        }
    }

    @Test
    @DisplayName("0 이하 수량 차감 요청 시 예외가 발생한다")
    fun decreaseStock_fail_invalidQuantity() {
        val product = ProductFixture.createSelling(stock = 10)

        assertThrows<BusinessException> {
            product.decreaseStock(0)
        }
    }
}