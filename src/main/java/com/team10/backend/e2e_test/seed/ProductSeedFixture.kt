package com.team10.backend.e2e_test.seed

import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.user.entity.User
import java.time.LocalDateTime

/**
 * ✅ Mock Server 의 mock-product-data.ts 에 정의된 제품 데이터 매핑
 * - E2E 테스트용 고정 데이터 (재현성 보장)
 * - productId 는 DB 의 IDENTITY 로 자동 생성되므로 무시
 */
object ProductSeedFixture {

    data class SeedProductData(
        val productName: String,
        val description: String?,
        val price: Int,
        val stock: Int,
        val type: ProductType,
        val imageUrl: String?,
        val status: ProductStatus,
        val createdAt: LocalDateTime = LocalDateTime.now(),
        val updatedAt: LocalDateTime = LocalDateTime.now()
    )

    // Mock Server 의 products 배열과 1:1 매핑
    val SEED_PRODUCTS: List<SeedProductData> = listOf(
        SeedProductData(
            productName = "스프링 입문",
            description = "자바 스프링 프레임워크 기초 가이드",
            price = 18000,
            stock = 50,
            type = ProductType.BOOK,
            imageUrl = "https://example.com/images/spring-intro.jpg",
            status = ProductStatus.SELLING
        ),
        SeedProductData(
            productName = "ABC",
            description = "책 설명입니다.",
            price = 10000,
            stock = 100,
            type = ProductType.EBOOK,
            imageUrl = "https://example.com/images/book1.jpg",
            status = ProductStatus.SELLING
        ),
        SeedProductData(
            productName = "품절된 상품",
            description = "재고가 없는 상품입니다.",
            price = 25000,
            stock = 0,  // stock=0 → SOLD_OUT 상태 유지
            type = ProductType.EBOOK,
            imageUrl = null,
            status = ProductStatus.SOLD_OUT
        ),
        // ✅ [E2E 전용] 결제 테스트용 상품 (고정 ID 대신 이름으로 식별)
        SeedProductData(
            productName = "[E2E-TEST] 결제 테스트 상품",
            description = "자동화된 결제 테스트를 위한 전용 상품입니다. 실제 판매되지 않습니다.",
            price = 1000,  // 테스트용 소액
            stock = 9999,  // 재고 무한
            type = ProductType.EBOOK,
            imageUrl = null,
            status = ProductStatus.SELLING
        )
    )

    /**
     * SeedProductData → 실제 Product Entity 로 변환
     * - Product 생성자의 도메인 로직 (stock=0 → SOLD_OUT 등) 이 자동 적용됨
     */
    fun toEntity(seller: User, data: SeedProductData): Product {
        return Product(
            user = seller,
            type = data.type,
            productName = data.productName,
            description = data.description ?: "",
            price = data.price,
            stock = data.stock,
            imageUrl = data.imageUrl
            // ✅ 생성자 내부에서:
            // - stock == 0 이면 status = SOLD_OUT
            // - 그 외면 status = SELLING
            // - createdAt/updatedAt 는 @CreatedDate/@LastModifiedDate 로 자동 설정
        )
    }
}