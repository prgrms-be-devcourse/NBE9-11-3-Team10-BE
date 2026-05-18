package com.team10.backend.domain.product.service

import com.team10.backend.domain.image.service.ImageUploadService
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

//    @Test
//    @DisplayName("상품 수정 성공")
//    fun updateProduct_success() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        val request = ProductUpdateRequest(
//            "수정된 상품명",
//            "수정된 설명",
//            12000,
//            "https://example.com/new.jpg",
//            ProductType.EBOOK,
//            ProductStatus.SOLD_OUT
//        )
//
//        val response = productService!!.update(1L, savedProduct.getId(), request)
//
//        Assertions.assertThat(response.productId).isEqualTo(savedProduct.getId())
//        Assertions.assertThat(response.productName).isEqualTo("수정된 상품명")
//        Assertions.assertThat(response.description).isEqualTo("수정된 설명")
//        Assertions.assertThat(response.price).isEqualTo(12000)
//        Assertions.assertThat(response.imageUrl).isEqualTo("https://example.com/new.jpg")
//        Assertions.assertThat<ProductType>(response.type).isEqualTo(ProductType.EBOOK)
//        Assertions.assertThat<ProductStatus>(response.status).isEqualTo(ProductStatus.SOLD_OUT)
//        Mockito.verify<ImageUploadService?>(imageUploadService).deleteIfManaged("https://example.com/old.jpg")
//    }
//
//    @Test
//    @DisplayName("상품 수정 시 imageUrl이 null이면 상품 이미지 삭제 - 성공")
//    fun updateProduct_deleteImageWhenImageUrlIsNull_success() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        val request = ProductUpdateRequest(
//            "수정된 상품명",
//            "수정된 설명",
//            12000,
//            null,
//            ProductType.EBOOK,
//            ProductStatus.SELLING
//        )
//
//        val response = productService!!.update(1L, savedProduct.getId(), request)
//
//        Assertions.assertThat(response.productId).isEqualTo(savedProduct.getId())
//        Assertions.assertThat(response.imageUrl).isNull()
//        Mockito.verify<ImageUploadService?>(imageUploadService).deleteIfManaged("https://example.com/old.jpg")
//    }
//
//    @Test
//    @DisplayName("상품 수정 시 imageUrl이 같으면 기존 이미지 삭제하지 않음")
//    fun updateProduct_skipImageDeleteWhenImageUrlIsSame_success() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/same.jpg"
//            )
//        )
//
//        val request = ProductUpdateRequest(
//            "수정된 상품명",
//            "수정된 설명",
//            12000,
//            "https://example.com/same.jpg",
//            ProductType.EBOOK,
//            ProductStatus.SELLING
//        )
//
//        val response = productService!!.update(1L, savedProduct.getId(), request)
//
//        Assertions.assertThat(response.productId).isEqualTo(savedProduct.getId())
//        Assertions.assertThat(response.imageUrl).isEqualTo("https://example.com/same.jpg")
//        Mockito.verify<ImageUploadService?>(imageUploadService, Mockito.never())
//            .deleteIfManaged("https://example.com/same.jpg")
//    }
//
//    @Test
//    @DisplayName("존재하지 않는 상품 수정 시, 예외 발생")
//    fun updateProduct_fail_productNotFound() {
//        val request = ProductUpdateRequest(
//            "수정된 상품명",
//            "수정된 설명",
//            12000,
//            "https://example.com/new.jpg",
//            ProductType.BOOK,
//            ProductStatus.SELLING
//        )
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.update(
//                1L,
//                9999L,
//                request
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage())
//    }
//
//    @Test
//    @DisplayName("본인 상품이 아닌 상품 수정 시, 예외 발생")
//    fun updateProduct_fail_accessDenied() {
//        val owner = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                owner,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        val request = ProductUpdateRequest(
//            "수정된 상품명",
//            "수정된 설명",
//            12000,
//            "https://example.com/new.jpg",
//            ProductType.EBOOK,
//            ProductStatus.SELLING
//        )
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.update(
//                2L,
//                savedProduct.getId(),
//                request
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.ACCESS_DENIED.getMessage())
//    }
//
//    @Test
//    @DisplayName("상품 비활성화 성공")
//    fun inactiveProduct_success() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "비활성화 대상 상품",
//                "상품 설명",
//                10000,
//                10,
//                "https://example.com/book.jpg"
//            )
//        )
//
//        val response = productService!!.inactive(1L, savedProduct.getId())
//
//        Assertions.assertThat(response.productId).isEqualTo(savedProduct.getId())
//        Assertions.assertThat<ProductStatus>(response.status).isEqualTo(ProductStatus.INACTIVE)
//        Assertions.assertThat(response.message).isEqualTo("상품이 삭제되었습니다.")
//
//        val product = productRepository.findById(savedProduct.getId()).orElseThrow()
//        Assertions.assertThat<ProductStatus>(product.status).isEqualTo(ProductStatus.INACTIVE)
//    }
//
//    @Test
//    @DisplayName("이미 비활성화된 상품 재요청 시, 예외 발생")
//    fun inactiveProduct_fail_alreadyInactive() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "이미 비활성화된 상품",
//                "상품 설명",
//                10000,
//                10,
//                "https://example.com/book.jpg"
//            )
//        )
//
//        savedProduct.inactivate()
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.inactive(
//                1L,
//                savedProduct.getId()
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.PRODUCT_ALREADY_INACTIVE.getMessage())
//    }
//
//    @Test
//    @DisplayName("존재하지 않는 상품 비활성화 시, 예외 발생")
//    fun inactiveProduct_fail_productNotFound() {
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.inactive(
//                1L,
//                9999L
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage())
//    }
//
//    @Test
//    @DisplayName("본인 상품이 아닌 상품 비활성화 시, 예외 발생")
//    fun inactiveProduct_fail_accessDenied() {
//        val owner = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                owner,
//                ProductType.BOOK,
//                "비활성화 대상 상품",
//                "상품 설명",
//                10000,
//                10,
//                "https://example.com/book.jpg"
//            )
//        )
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.inactive(
//                2L,
//                savedProduct.getId()
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.ACCESS_DENIED.getMessage())
//    }
//
//    @Test
//    @DisplayName("재고 수정 성공")
//    fun updateStock_success() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        val request = ProductStockRequest(30)
//
//        val response = productService!!.updateStock(1L, savedProduct.getId(), request)
//
//        Assertions.assertThat(response.productId).isEqualTo(savedProduct.getId())
//        Assertions.assertThat(response.stock).isEqualTo(30)
//        Assertions.assertThat(response.message).isEqualTo("상품 재고가 수정되었습니다.")
//
//        val product = productRepository.findById(savedProduct.getId()).orElseThrow()
//        Assertions.assertThat(product.stock).isEqualTo(30)
//        Assertions.assertThat<ProductStatus>(product.status).isEqualTo(ProductStatus.SELLING)
//    }
//
//    @Test
//    @DisplayName("존재하지 않는 상품 재고 수정 시, 예외 발생")
//    fun updateStock_fail_productNotFound() {
//        val request = ProductStockRequest(30)
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.updateStock(
//                1L,
//                9999L,
//                request
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage())
//    }
//
//    @Test
//    @DisplayName("비활성화된 상품 재고 수정 시, 예외 발생")
//    fun updateStock_fail_inactiveProduct() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        savedProduct.inactivate()
//
//        val request = ProductStockRequest(30)
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.updateStock(
//                1L,
//                savedProduct.getId(),
//                request
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.PRODUCT_ALREADY_INACTIVE.getMessage())
//    }
//
//    @Test
//    @DisplayName("본인 상품이 아닌 상품 재고 수정 시, 예외 발생")
//    fun updateStock_fail_accessDenied() {
//        val owner = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                owner,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        val request = ProductStockRequest(30)
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.updateStock(
//                2L,
//                savedProduct.getId(),
//                request
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.ACCESS_DENIED.getMessage())
//    }
//
//    @Test
//    @DisplayName("sellerId로 상품 필터링")
//    fun list_withSellerId_filters() {
//        val seller = userRepository!!.findById(1L).orElseThrow()
//        val anotherSeller = userRepository.findById(2L).orElseThrow()
//
//        productRepository!!.save<Product?>(
//            Product(
//                seller,
//                ProductType.BOOK,
//                "seller 상품",
//                "설명",
//                10000,
//                10,
//                "https://example.com/seller.jpg"
//            )
//        )
//
//        productRepository.save<Product?>(
//            Product(
//                anotherSeller,
//                ProductType.BOOK,
//                "다른 판매자 상품",
//                "설명",
//                12000,
//                10,
//                "https://example.com/another.jpg"
//            )
//        )
//
//        val response = productService!!.list(0, 10, null, null, seller.getId())
//
//        Assertions.assertThat<ProductListResponse>(response.content).isNotEmpty()
//        Assertions.assertThat<ProductListResponse>(response.content)
//            .extracting<Long?, RuntimeException?>(ThrowingExtractor { product: ProductListResponse? -> product!!.sellerId })
//            .containsOnly(seller.getId())
//    }
//
//    @Test
//    @DisplayName("재고를 0으로 수정하면 상태가 SOLD_OUT으로 변경된다")
//    fun updateStock_success_soldOutWhenZero() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        val request = ProductStockRequest(0)
//
//        val response = productService!!.updateStock(1L, savedProduct.getId(), request)
//
//        Assertions.assertThat(response.productId).isEqualTo(savedProduct.getId())
//        Assertions.assertThat(response.stock).isEqualTo(0)
//
//        val product = productRepository.findById(savedProduct.getId()).orElseThrow()
//        Assertions.assertThat(product.stock).isEqualTo(0)
//        Assertions.assertThat<ProductStatus>(product.status).isEqualTo(ProductStatus.SOLD_OUT)
//    }
//
//    @Test
//    @DisplayName("SOLD_OUT 상품의 재고가 0에서 1 이상으로 오르면 SELLING으로 변경")
//    fun updateStock_success_sellingWhenStockBecomesPositive() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val soldOutProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "품절 상품",
//                "설명",
//                10000,
//                0,
//                "https://example.com/book.jpg"
//            )
//        )
//
//        val request = ProductStockRequest(5)
//
//        val response = productService!!.updateStock(1L, soldOutProduct.getId(), request)
//
//        Assertions.assertThat(response.productId).isEqualTo(soldOutProduct.getId())
//        Assertions.assertThat(response.stock).isEqualTo(5)
//
//        val product = productRepository.findById(soldOutProduct.getId()).orElseThrow()
//        Assertions.assertThat(product.stock).isEqualTo(5)
//        Assertions.assertThat<ProductStatus>(product.status).isEqualTo(ProductStatus.SELLING)
//    }
//
//    @Test
//    @DisplayName("음수 재고 입력 시, 예외 발생")
//    fun updateStock_fail_invalidStock() {
//        val user = userRepository!!.findById(1L).orElseThrow()
//
//        val savedProduct = productRepository!!.save<Product>(
//            Product(
//                user,
//                ProductType.BOOK,
//                "기존 상품명",
//                "기존 설명",
//                10000,
//                10,
//                "https://example.com/old.jpg"
//            )
//        )
//
//        val request = ProductStockRequest(-1)
//
//        AssertionsForClassTypes.assertThatThrownBy(ThrowableAssert.ThrowingCallable {
//            productService!!.updateStock(
//                1L,
//                savedProduct.getId(),
//                request
//            )
//        })
//            .isInstanceOf(BusinessException::class.java)
//            .hasMessage(ErrorCode.INVALID_STOCK.getMessage())
//    }

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
