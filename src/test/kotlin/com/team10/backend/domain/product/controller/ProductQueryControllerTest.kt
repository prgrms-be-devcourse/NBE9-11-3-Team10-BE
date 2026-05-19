package com.team10.backend.domain.product.controller

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.ProductFixture
import com.team10.backend.fixture.UserFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
internal class ProductQueryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    @Test
    @DisplayName("상품 전체 조회 성공")
    fun listProducts_success() {
        val seller = saveSeller()
        saveProducts(ProductFixture.createList(count = 2, user = seller))

        mockMvc.perform(
            get("/api/v1/products")
                .param("page", "1")
                .param("size", "10")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(1))
    }

    @Test
    @DisplayName("상품 전체 조회 시, type 필터 적용 성공")
    fun listProducts_withTypeFilter_success() {
        val seller = saveSeller()
        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.BOOK))
        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.EBOOK))

        mockMvc.perform(
            get("/api/v1/products")
                .param("page", "1")
                .param("size", "10")
                .param("type", ProductType.BOOK.name)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].type").value(ProductType.BOOK.name))
    }

    @Test
    @DisplayName("상품 전체 조회 시, status 필터 적용 성공")
    fun listProducts_withStatusFilter_success() {
        val seller = saveSeller()
        saveProduct(ProductFixture.createSelling(user = seller))
        saveProduct(ProductFixture.createInactive(user = seller))

        mockMvc.perform(
            get("/api/v1/products")
                .param("page", "1")
                .param("size", "10")
                .param("status", ProductStatus.SELLING.name)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].status").value(ProductStatus.SELLING.name))
    }

    @Test
    @DisplayName("상품 전체 조회 시, type/status 필터 적용 성공")
    fun listProducts_withTypeAndStatusFilter_success() {
        val seller = saveSeller()
        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.BOOK))
        saveProduct(ProductFixture.createInactive(user = seller, type = ProductType.BOOK))
        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.EBOOK))

        mockMvc.perform(
            get("/api/v1/products")
                .param("page", "1")
                .param("size", "10")
                .param("type", ProductType.BOOK.name)
                .param("status", ProductStatus.SELLING.name)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].type").value(ProductType.BOOK.name))
            .andExpect(jsonPath("$.data.content[0].status").value(ProductStatus.SELLING.name))
    }

    private fun saveSeller(): User {
        return userRepository.save(UserFixture.create(role = Role.SELLER))
    }

    private fun saveProduct(product: Product): Product {
        return productRepository.save(product)
    }

    private fun saveProducts(products: List<Product>): List<Product> {
        return productRepository.saveAll(products)
    }
}