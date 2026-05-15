package com.team10.backend.domain.product.service

import com.team10.backend.domain.image.service.ImageUploadService
import com.team10.backend.domain.product.dto.*
import com.team10.backend.domain.product.dto.ProductStockResponse.Companion.of
import com.team10.backend.domain.product.entity.Product
import com.team10.backend.domain.product.enums.ProductStatus
import com.team10.backend.domain.product.enums.ProductType
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val imageUploadService: ImageUploadService
) {
    @Transactional
    fun create(userId: Long, request: ProductCreateRequest): ProductDetailResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        val product = Product(
            user,
            request.type,
            request.productName,
            request.description,
            request.price,
            request.stock,
            request.imageUrl
        )

        val savedProduct = productRepository.save(product)
        return ProductDetailResponse.from(savedProduct)
    }

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int, type: ProductType?, status: ProductStatus?, sellerId: Long?): ProductPageResponse {
        val pageable: Pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        val spec = Specification<Product> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (type != null) {
                predicates.add(cb.equal(root.get<ProductType>("type"), type))
            }

            if (status != null) {
                predicates.add(cb.equal(root.get<ProductStatus>("status"), status))
            } else {
                predicates.add(cb.notEqual(root.get<ProductStatus>("status"), ProductStatus.INACTIVE))
            }

            if (sellerId != null) {
                predicates.add(cb.equal(root.get<User>("user").get<Long>("id"), sellerId))
            }

            cb.and(*predicates.toTypedArray())
        }

        val productPage: Page<Product> = productRepository.findAll(spec, pageable)

        val content = productPage.content
            .map { product -> ProductListResponse.from(product) }

        return ProductPageResponse(
            content,
            productPage.number + 1,
            productPage.size,
            productPage.totalElements,
            productPage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun detail(productId: Long): ProductDetailResponse {
        val product = productRepository.findById(productId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }

        if (product.status == ProductStatus.INACTIVE) {
            throw BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
        }

        return ProductDetailResponse.from(product)
    }

    @Transactional
    fun update(userId: Long, productId: Long, request: ProductUpdateRequest): ProductDetailResponse {
        val product = getAuthorizedProduct(userId, productId)
        deletePreviousImageIfChanged(product.imageUrl, request.imageUrl)

        product.update(
            request.type,
            request.productName,
            request.description,
            request.price,
            request.imageUrl,
            request.status
        )

        return ProductDetailResponse.from(product)
    }

    @Transactional
    fun inactive(userId: Long, productId: Long): ProductInactiveResponse {
        val product = getAuthorizedProduct(userId, productId)

        if (product.status == ProductStatus.INACTIVE) {
            throw BusinessException(ErrorCode.PRODUCT_ALREADY_INACTIVE)
        }

        product.inactivate()

        return ProductInactiveResponse.from(product)
    }

    @Transactional
    fun updateStock(userId: Long, productId: Long, request: ProductStockRequest): ProductStockResponse {
        val product = getAuthorizedProductWithLock(userId, productId)

        if (product.status == ProductStatus.INACTIVE) {
            throw BusinessException(ErrorCode.PRODUCT_ALREADY_INACTIVE)
        }

        product.updateStock(request.stock)

        return of(product.id, product.stock)
    }

    private fun getAuthorizedProduct(userId: Long, productId: Long): Product {
        val product = productRepository.findById(productId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }

        if (product.user.id != userId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        return product
    }

    private fun deletePreviousImageIfChanged(oldImageUrl: String?, newImageUrl: String?) {
        if (oldImageUrl != newImageUrl) {
            imageUploadService.deleteIfManaged(oldImageUrl)
        }
    }

    private fun getAuthorizedProductWithLock(userId: Long, productId: Long): Product {
        val product = productRepository.findByIdWithPessimisticLock(productId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }

        if (product.user.id != userId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

        return product
    }
}