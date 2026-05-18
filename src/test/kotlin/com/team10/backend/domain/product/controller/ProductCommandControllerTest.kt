package com.team10.backend.domain.product.controller

import com.team10.backend.domain.product.dto.ProductStockRequest
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.ProductFixture
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.security.CustomUserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
internal class ProductCommandControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    @DisplayName("비로그인 사용자의 상품 등록 요청은 401")
    fun createProduct_fail_unauthorized() {
        val request = ProductFixture.createRequest()

        mockMvc.perform(
            post("/api/v1/stores/me/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("BUYER 권한 사용자의 상품 등록 요청은 403")
    fun createProduct_fail_forbidden() {
        val buyer = saveBuyer()
        val request = ProductFixture.createRequest()

        mockMvc.perform(
            post("/api/v1/stores/me/products")
                .with(authenticatedUser(buyer.id, Role.BUYER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden())
    }

    @Test
    @DisplayName("상품 등록 성공")
    fun createProduct_success() {
        val seller = saveSeller()
        val request = ProductFixture.createRequest()

        mockMvc.perform(
            post("/api/v1/stores/me/products")
                .with(authenticatedUser(seller.id, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.productId").exists())
            .andExpect(jsonPath("$.data.productName").value(request.productName))
            .andExpect(jsonPath("$.data.price").value(request.price))
            .andExpect(jsonPath("$.data.stock").value(request.stock))
            .andExpect(jsonPath("$.data.imageUrl").value(request.imageUrl))
            .andExpect(jsonPath("$.data.type").value(request.type.name))
            .andExpect(jsonPath("$.data.status").value(ProductStatus.SELLING.name))

        val product = productRepository.findAll().first()

        assertThat(product.productName).isEqualTo(request.productName)
        assertThat(product.price).isEqualTo(request.price)
        assertThat(product.stock).isEqualTo(request.stock)
        assertThat(product.imageUrl).isEqualTo(request.imageUrl)
        assertThat(product.type).isEqualTo(request.type)
        assertThat(product.status).isEqualTo(ProductStatus.SELLING)
        assertThat(product.user.id).isEqualTo(seller.id)
    }

    @Test
    @DisplayName("상품 수정 성공")
    fun updateProduct_success() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))
        val request = ProductFixture.updateRequest()

        mockMvc.perform(
            put("/api/v1/stores/me/products/{productId}", savedProduct.id)
                .with(authenticatedUser(seller.id, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.productId").value(savedProduct.id))
            .andExpect(jsonPath("$.data.productName").value(request.productName))
            .andExpect(jsonPath("$.data.description").value(request.description))
            .andExpect(jsonPath("$.data.price").value(request.price))
            .andExpect(jsonPath("$.data.stock").value(savedProduct.stock))
            .andExpect(jsonPath("$.data.imageUrl").value(request.imageUrl))
            .andExpect(jsonPath("$.data.type").value(request.type.name))
            .andExpect(jsonPath("$.data.status").value(request.status.name))

        val product = productRepository.findById(savedProduct.id).orElseThrow()

        assertThat(product.productName).isEqualTo(request.productName)
        assertThat(product.description).isEqualTo(request.description)
        assertThat(product.price).isEqualTo(request.price)
        assertThat(product.stock).isEqualTo(savedProduct.stock)
        assertThat(product.imageUrl).isEqualTo(request.imageUrl)
        assertThat(product.type).isEqualTo(request.type)
        assertThat(product.status).isEqualTo(request.status)
    }

    @Test
    @DisplayName("본인 상품이 아닌 상품 수정 요청은 403")
    fun updateProduct_fail_accessDenied() {
        val owner = saveSeller()
        val anotherSeller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = owner))
        val request = ProductFixture.updateRequest()

        mockMvc.perform(
            put("/api/v1/stores/me/products/{productId}", savedProduct.id)
                .with(authenticatedUser(anotherSeller.id, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("COMMON_003"))
    }

    @Test
    @DisplayName("상품 비활성화 성공")
    fun inactiveProduct_success() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))

        mockMvc.perform(
            patch("/api/v1/stores/me/products/{productId}/inactive", savedProduct.id)
                .with(authenticatedUser(seller.id, Role.SELLER))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.productId").value(savedProduct.id))
            .andExpect(jsonPath("$.data.status").value(ProductStatus.INACTIVE.name))
            .andExpect(jsonPath("$.data.message").value("상품이 삭제되었습니다."))

        val product = productRepository.findById(savedProduct.id).orElseThrow()

        assertThat(product.status).isEqualTo(ProductStatus.INACTIVE)
    }

    @Test
    @DisplayName("이미 비활성화된 상품 재요청 시 409")
    fun inactiveProduct_fail_alreadyInactive() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createInactive(user = seller))

        mockMvc.perform(
            patch("/api/v1/stores/me/products/{productId}/inactive", savedProduct.id)
                .with(authenticatedUser(seller.id, Role.SELLER))
        )
            .andExpect(status().isConflict())
    }

    @Test
    @DisplayName("재고 수정 성공")
    fun updateStock_success() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))
        val updatedStock = 30
        val request = ProductStockRequest(updatedStock)

        mockMvc.perform(
            patch("/api/v1/stores/me/products/{productId}/stock", savedProduct.id)
                .with(authenticatedUser(seller.id, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.productId").value(savedProduct.id))
            .andExpect(jsonPath("$.data.stock").value(updatedStock))
            .andExpect(jsonPath("$.data.message").value("상품 재고가 수정되었습니다."))
    }

    @Test
    @DisplayName("재고를 음수로 수정하면 검증 실패")
    fun updateStock_fail_negativeStock() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))
        val request = ProductStockRequest(-1)

        mockMvc.perform(
            patch("/api/v1/stores/me/products/{productId}/stock", savedProduct.id)
                .with(authenticatedUser(seller.id, Role.SELLER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
    }

    // 테스트 헬퍼 메서드
    private fun saveSeller(): User {
        return userRepository.save(UserFixture.create(role = Role.SELLER))
    }

    private fun saveBuyer(): User {
        return userRepository.save(UserFixture.create(role = Role.BUYER))
    }

    private fun saveProduct(product: Product): Product {
        return productRepository.save(product)
    }

    private fun authenticatedUser(userId: Long, role: Role): RequestPostProcessor {
        return authentication(
            UsernamePasswordAuthenticationToken(
                CustomUserPrincipal(userId, role),
                null,
                listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
            )
        )
    }
}