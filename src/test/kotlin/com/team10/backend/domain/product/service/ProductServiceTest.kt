package com.team10.backend.domain.product.service

import com.team10.backend.domain.image.service.ImageUploadService
import com.team10.backend.domain.product.dto.ProductStockRequest
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.ProductFixture
import com.team10.backend.fixture.UserFixture
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
@ActiveProfiles("test")
internal class ProductServiceTest {

    @Autowired
    lateinit var productService: ProductService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var productRepository: ProductRepository

    @MockitoBean
    lateinit var imageUploadService: ImageUploadService

    @Test
    @DisplayName("상품 생성 성공, SELLING 상태")
    fun createProduct_defaultStatusSelling() {
        val seller = saveSeller()
        val request = ProductFixture.createRequest(imageUrl = null)

        val response = productService.create(seller.id, request)

        assertThat(response.productId).isNotNull()
        assertThat(response.productName).isEqualTo(request.productName)
        assertThat(response.description).isEqualTo(request.description)
        assertThat(response.price).isEqualTo(request.price)
        assertThat(response.stock).isEqualTo(request.stock)
        assertThat(response.type).isEqualTo(request.type)
        assertThat(response.imageUrl).isNull()
        assertThat(response.status).isEqualTo(ProductStatus.SELLING)
        assertThat(productRepository.count()).isEqualTo(1)
    }

    @Test
    @DisplayName("상품 생성 시 imageUrl 저장")
    fun createProduct_withImageUrl() {
        val seller = saveSeller()
        val imageUrl = "https://example.com/product.jpg"
        val request = ProductFixture.createRequest(imageUrl = imageUrl)

        val response = productService.create(seller.id, request)

        assertThat(response.imageUrl).isEqualTo(request.imageUrl)
    }

    @Test
    @DisplayName("상품 생성 시 imageUrl 없어도 저장")
    fun createProduct_withoutImageUrl() {
        val seller = saveSeller()
        val request = ProductFixture.createRequest(imageUrl = null)

        val response = productService.create(seller.id, request)

        assertThat(response.imageUrl).isEqualTo(request.imageUrl)
    }

    @Test
    @DisplayName("상품 전체 조회 시, 상품 목록과 페이지 정보 반환")
    fun listProducts_withPaging() {
        val seller = saveSeller(nickname = "seller1")

        saveProducts(ProductFixture.createList(count = 2, user = seller))

        val response = productService.list(0, 10, null, null, null)

        assertThat(response.content).hasSize(2)
        assertThat(response.page).isEqualTo(1)
        assertThat(response.size).isEqualTo(10)
        assertThat(response.totalElements).isEqualTo(2)
        assertThat(response.totalPages).isEqualTo(1)
    }

    @Test
    @DisplayName("상품 전체 조회 시, type 필터로 상품 조회")
    fun listProducts_withTypeFilter() {
        val seller = saveSeller()

        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.BOOK))
        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.EBOOK))

        val response = productService.list(0, 10, ProductType.BOOK, null, null)

        assertThat(response.content).hasSize(1)
        assertThat(response.content[0].type).isEqualTo(ProductType.BOOK)
    }

    @Test
    @DisplayName("상품 전체 조회 시, status 필터로 상품 조회")
    fun listProducts_withStatusFilter() {
        val seller = saveSeller()

        saveProduct(ProductFixture.createSelling(user = seller))
        saveProduct(ProductFixture.createInactive(user = seller))

        val response = productService.list(0, 10, null, ProductStatus.SELLING, null)

        assertThat(response.content).hasSize(1)
        assertThat(response.content[0].status).isEqualTo(ProductStatus.SELLING)
    }

    @Test
    @DisplayName("상품 전체 조회 시, type과 status 필터로 상품 조회")
    fun listProducts_withTypeAndStatusFilter() {
        val seller = saveSeller()

        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.BOOK))
        saveProduct(ProductFixture.createInactive(user = seller, type = ProductType.BOOK))
        saveProduct(ProductFixture.createSelling(user = seller, type = ProductType.EBOOK))

        val response = productService.list(0, 10, ProductType.BOOK, ProductStatus.SELLING, null)

        assertThat(response.content).hasSize(1)
        assertThat(response.content[0].type).isEqualTo(ProductType.BOOK)
        assertThat(response.content[0].status).isEqualTo(ProductStatus.SELLING)
    }

    @Test
    @DisplayName("sellerId로 상품 필터링")
    fun list_withSellerId_filters() {
        val seller = saveSeller()
        val anotherSeller = saveSeller()

        saveProduct(ProductFixture.createSelling(user = seller))
        saveProduct(ProductFixture.createSelling(user = anotherSeller))

        val response = productService.list(0, 10, null, null, seller.id)

        assertThat(response.content).isNotEmpty()
        assertThat(response.content.map { it.sellerId }).containsOnly(seller.id)
    }

    @Test
    @DisplayName("상품 상세 조회 성공")
    fun detail_success() {
        val seller = saveSeller(nickname = "seller1")
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))

        val response = productService.detail(savedProduct.id)

        assertThat(response.productId).isEqualTo(savedProduct.id)
        assertThat(response.productName).isEqualTo(savedProduct.productName)
        assertThat(response.description).isEqualTo(savedProduct.description)
        assertThat(response.nickname).isEqualTo("seller1")
        assertThat(response.price).isEqualTo(savedProduct.price)
        assertThat(response.stock).isEqualTo(savedProduct.stock)
        assertThat(response.type).isEqualTo(savedProduct.type)
        assertThat(response.imageUrl).isEqualTo(savedProduct.imageUrl)
        assertThat(response.status).isEqualTo(savedProduct.status)
    }

    @Test
    @DisplayName("존재하지 않는 상품 상세 조회 시, 예외 발생")
    fun detail_fail_productNotFound() {
        val exception = assertThrows<BusinessException> {
            productService.detail(NOT_FOUND_ID)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
    }

    @Test
    @DisplayName("상품 수정 성공")
    fun updateProduct_success() {
        val seller = saveSeller()
        val oldImageUrl = "https://example.com/old.jpg"
        val savedProduct = saveProduct(
            ProductFixture.createSelling(
                user = seller,
                imageUrl = oldImageUrl
            )
        )
        val request = ProductFixture.updateRequest()

        val response = productService.update(seller.id, savedProduct.id, request)

        assertThat(response.productId).isEqualTo(savedProduct.id)
        assertThat(response.productName).isEqualTo(request.productName)
        assertThat(response.description).isEqualTo(request.description)
        assertThat(response.price).isEqualTo(request.price)
        assertThat(response.imageUrl).isEqualTo(request.imageUrl)
        assertThat(response.type).isEqualTo(request.type)
        assertThat(response.status).isEqualTo(request.status)
        verify(imageUploadService).deleteIfManaged(oldImageUrl)
    }

    @Test
    @DisplayName("상품 수정 시 이미지를 제거하면 기존 이미지 삭제 성공")
    fun updateProduct_deleteImageWhenImageUrlIsNull_success() {
        val seller = saveSeller()
        val oldImageUrl = "https://example.com/old.jpg"
        val savedProduct = saveProduct(
            ProductFixture.createSelling(
                user = seller,
                imageUrl = oldImageUrl
            )
        )
        val request = ProductFixture.updateRequest(imageUrl = null)

        val response = productService.update(seller.id, savedProduct.id, request)

        assertThat(response.productId).isEqualTo(savedProduct.id)
        assertThat(response.imageUrl).isNull()
        verify(imageUploadService).deleteIfManaged(oldImageUrl)
    }

    @Test
    @DisplayName("상품 수정 시 imageUrl이 같으면 기존 이미지 삭제하지 않음")
    fun updateProduct_skipImageDeleteWhenImageUrlIsSame_success() {
        val seller = saveSeller()
        val sameImageUrl = "https://example.com/same.jpg"
        val savedProduct = saveProduct(
            ProductFixture.createSelling(
                user = seller,
                imageUrl = sameImageUrl
            )
        )
        val request = ProductFixture.updateRequest(imageUrl = sameImageUrl)

        val response = productService.update(seller.id, savedProduct.id, request)

        assertThat(response.productId).isEqualTo(savedProduct.id)
        assertThat(response.imageUrl).isEqualTo(sameImageUrl)
        verify(imageUploadService, never()).deleteIfManaged(sameImageUrl)
    }

    @Test
    @DisplayName("존재하지 않는 상품 수정 시, 예외 발생")
    fun updateProduct_fail_productNotFound() {
        val seller = saveSeller()
        val request = ProductFixture.updateRequest()

        val exception = assertThrows<BusinessException> {
            productService.update(seller.id, NOT_FOUND_ID, request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
    }

    @Test
    @DisplayName("본인 상품이 아닌 상품을 수정 시, 예외 발생")
    fun updateProduct_fail_accessDenied() {
        val owner = saveSeller()
        val anotherSeller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = owner))
        val request = ProductFixture.updateRequest()

        val exception = assertThrows<BusinessException> {
            productService.update(anotherSeller.id, savedProduct.id, request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.ACCESS_DENIED)
    }

    @Test
    @DisplayName("상품 비활성화 성공")
    fun inactiveProduct_success() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))

        val response = productService.inactive(seller.id, savedProduct.id)

        assertThat(response.productId).isEqualTo(savedProduct.id)
        assertThat(response.status).isEqualTo(ProductStatus.INACTIVE)
        assertThat(response.message).isEqualTo("상품이 삭제되었습니다.")
    }

    @Test
    @DisplayName("이미 비활성화된 상품 재요청 시, 예외 발생")
    fun inactiveProduct_fail_alreadyInactive() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createInactive(user = seller))

        val exception = assertThrows<BusinessException> {
            productService.inactive(seller.id, savedProduct.id)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.PRODUCT_ALREADY_INACTIVE)
    }

    @Test
    @DisplayName("존재하지 않는 상품 비활성화 시, 예외 발생")
    fun inactiveProduct_fail_productNotFound() {
        val seller = saveSeller()

        val exception = assertThrows<BusinessException> {
            productService.inactive(seller.id, NOT_FOUND_ID)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
    }

    @Test
    @DisplayName("본인 상품이 아닌 상품 비활성화 시, 예외 발생")
    fun inactiveProduct_fail_accessDenied() {
        val owner = saveSeller()
        val anotherSeller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = owner))

        val exception = assertThrows<BusinessException> {
            productService.inactive(anotherSeller.id, savedProduct.id)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.ACCESS_DENIED)
    }

    @Test
    @DisplayName("재고 수정 성공")
    fun updateStock_success() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))
        val updatedStock = 30
        val request = ProductStockRequest(updatedStock)

        val response = productService.updateStock(seller.id, savedProduct.id, request)

        assertThat(response.productId).isEqualTo(savedProduct.id)
        assertThat(response.stock).isEqualTo(updatedStock)
        assertThat(response.message).isEqualTo("상품 재고가 수정되었습니다.")
    }

    @Test
    @DisplayName("존재하지 않는 상품 재고 수정 시, 예외 발생")
    fun updateStock_fail_productNotFound() {
        val seller = saveSeller()
        val request = ProductStockRequest(30)

        val exception = assertThrows<BusinessException> {
            productService.updateStock(seller.id, NOT_FOUND_ID, request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
    }

    @Test
    @DisplayName("비활성화된 상품 재고 수정 시, 예외 발생")
    fun updateStock_fail_inactiveProduct() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createInactive(user = seller))
        val request = ProductStockRequest(30)

        val exception = assertThrows<BusinessException> {
            productService.updateStock(seller.id, savedProduct.id, request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.PRODUCT_ALREADY_INACTIVE)
    }

    @Test
    @DisplayName("본인 상품이 아닌 상품 재고 수정 시, 예외 발생")
    fun updateStock_fail_accessDenied() {
        val owner = saveSeller()
        val anotherSeller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = owner))
        val request = ProductStockRequest(30)

        val exception = assertThrows<BusinessException> {
            productService.updateStock(anotherSeller.id, savedProduct.id, request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.ACCESS_DENIED)
    }

    @Test
    @DisplayName("재고를 0으로 수정하면 상태가 SOLD_OUT으로 변경된다")
    fun updateStock_success_soldOutWhenZero() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))
        val request = ProductStockRequest(0)

        val response = productService.updateStock(seller.id, savedProduct.id, request)

        assertThat(response.productId).isEqualTo(savedProduct.id)
        assertThat(response.stock).isEqualTo(0)

        val product = productRepository.findById(savedProduct.id).orElseThrow()
        assertThat(product.status).isEqualTo(ProductStatus.SOLD_OUT)
    }

    @Test
    @DisplayName("SOLD_OUT 상품의 재고가 0에서 1 이상으로 오르면 SELLING으로 변경")
    fun updateStock_success_sellingWhenStockBecomesPositive() {
        val seller = saveSeller()
        val soldOutProduct = saveProduct(ProductFixture.createSoldOut(user = seller))
        val restockedStock = 5
        val request = ProductStockRequest(restockedStock)

        val response = productService.updateStock(seller.id, soldOutProduct.id, request)

        assertThat(response.productId).isEqualTo(soldOutProduct.id)
        assertThat(response.stock).isEqualTo(restockedStock)

        val product = productRepository.findById(soldOutProduct.id).orElseThrow()
        assertThat(product.status).isEqualTo(ProductStatus.SELLING)
    }

    @Test
    @DisplayName("음수 재고 입력 시, 예외 발생")
    fun updateStock_fail_invalidStock() {
        val seller = saveSeller()
        val savedProduct = saveProduct(ProductFixture.createSelling(user = seller))
        val request = ProductStockRequest(-1)

        val exception = assertThrows<BusinessException> {
            productService.updateStock(seller.id, savedProduct.id, request)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_STOCK)
    }

    private fun saveSeller(nickname: String? = null): User {
        val seller = if (nickname == null) {
            UserFixture.create(role = Role.SELLER)
        } else {
            UserFixture.create(role = Role.SELLER, nickname = nickname)
        }

        return userRepository.save(seller)
    }

    private fun saveProduct(product: Product): Product {
        return productRepository.save(product)
    }

    private fun saveProducts(products: List<Product>): List<Product> {
        return productRepository.saveAll(products)
    }

    companion object {
        private const val NOT_FOUND_ID = 9999L
    }
}
