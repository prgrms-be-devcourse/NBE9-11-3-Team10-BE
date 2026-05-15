package com.team10.backend.fixture

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import net.datafaker.Faker

object ProductFixture {
    private val faker = Faker()

    // ─────────────────────────────────────────────
    // ✅ 시나리오 기반 팩토리 메서드 (추천)
    // ─────────────────────────────────────────────

    /**
     * [기본] 정상 판매 중인 상품 (SELLING, stock > 0)
     */
    fun createSelling(
        user: User = UserFixture.create(role = Role.SELLER),
        type: ProductType = ProductType.BOOK,
        productName: String = faker.commerce().productName(),
        description: String = faker.lorem().paragraph(2),
        price: Int = faker.number().numberBetween(1_000, 1_000_000),
        stock: Int = faker.number().numberBetween(1, 100),
        imageUrl: String = faker.internet().url()
    ): Product {
        require(stock > 0) { "SELLING 상태 상품은 stock 이 1 이상이어야 합니다." }
        return Product(user, type, productName, description, price, stock, imageUrl)
    }

    /**
     * [시나리오] 재고 소진으로 자동 SOLD_OUT 된 상품
     * - Product 생성자 내부 로직에 의해 status 가 SOLD_OUT 으로 설정됨
     */
    fun createSoldOut(
        user: User = UserFixture.create(role = Role.SELLER),
        type: ProductType = ProductType.BOOK,
        productName: String = faker.commerce().productName(),
        description: String = faker.lorem().paragraph(2),
        price: Int = faker.number().numberBetween(1_000, 1_000_000),
        imageUrl: String = faker.internet().url()
    ): Product {
        // stock = 0 전달 → 생성자에서 자동 SOLD_OUT 전환
        return Product(user, type, productName, description, price, 0, imageUrl)
    }

    /**
     * [시나리오] 관리자에 의해 비활성화된 상품 (INACTIVE)
     * - 생성 후 inactivate() 호출하여 도메인 로직 준수
     */
    fun createInactive(
        user: User = UserFixture.create(role = Role.SELLER),
        type: ProductType = ProductType.BOOK,
        productName: String = faker.commerce().productName(),
        description: String = faker.lorem().paragraph(2),
        price: Int = faker.number().numberBetween(1_000, 1_000_000),
        stock: Int = faker.number().numberBetween(1, 100),
        imageUrl: String = faker.internet().url()
    ): Product {
        val product = Product(user, type, productName, description, price, stock, imageUrl)
        product.inactivate() // 도메인 메서드 호출
        return product
    }

    // ─────────────────────────────────────────────
    // ⚠️ 예외 테스트용 팩토리 (Negative Testing)
    // ─────────────────────────────────────────────

    /**
     * [예외] 재고 음수 생성 시도 → BusinessException(ErrorCode.INVALID_STOCK) 발생
     * - 테스트에서 assertThrows 와 함께 사용
     */
    fun createWithInvalidStock(
        user: User = UserFixture.create(role = Role.SELLER),
        type: ProductType = ProductType.BOOK,
        stock: Int = -1 // 기본값은 의도적으로 유효하지 않은 값
    ): () -> Product = {
        Product(
            user,
            type,
            "InvalidStockProduct",
            "Test",
            1000,
            stock,
            "https://example.com/img.jpg"
        )
    }

    /**
     * [예외] 재고 증감 수량 검증용 → decreaseStock/increaseStock 호출 시 예외 발생
     */
    fun createForStockValidation(
        stock: Int = 10,
        status: ProductStatus = ProductStatus.SELLING
    ): Product {
        val user = UserFixture.create(role = Role.SELLER)
        return Product(user, ProductType.BOOK, "ValidationTest", "Desc", 1000, stock, "url").apply {
            if (status == ProductStatus.INACTIVE) inactivate()
        }
    }

    // ─────────────────────────────────────────────
    // 🔁 유틸리티 메서드
    // ─────────────────────────────────────────────

    /**
     * 대량 상품 생성 (페이징, 목록 조회 테스트용)
     */
    fun createList(
        count: Int,
        user: User = UserFixture.create(role = Role.SELLER),
        type: ProductType = ProductType.BOOK,
        priceRange: IntRange = 1_000..500_000,
        stockRange: IntRange = 0..100 // 0 포함 시 SOLD_OUT 혼합 생성
    ): List<Product> {
        return (1..count).map {
            val stock = faker.number().numberBetween(stockRange.first, stockRange.last + 1)
            Product(
                user,
                type,
                faker.commerce().productName(),
                faker.lorem().paragraph(),
                faker.number().numberBetween(priceRange.first, priceRange.last + 1),
                stock,
                faker.internet().url()
            )
        }
    }

    /**
     * 특정 ProductType 만 필터링하여 생성
     */
    fun createByType(
        type: ProductType,
        count: Int = 1,
        user: User = UserFixture.create(role = Role.SELLER)
    ): List<Product> {
        return (1..count).map { createSelling(user = user, type = type) }
    }
}