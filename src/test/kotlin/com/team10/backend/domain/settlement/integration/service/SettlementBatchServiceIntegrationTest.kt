package com.team10.backend.domain.settlement.integration.service

import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.order.enums.PaymentStatus
import com.team10.backend.domain.order.repository.OrderRepository
import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.domain.product.repository.ProductRepository
import com.team10.backend.domain.settlement.dto.SettlementBatchResult
import com.team10.backend.domain.settlement.repository.SettlementRepository
import com.team10.backend.domain.settlement.service.DefaultFeePolicy
import com.team10.backend.domain.settlement.service.FeeCalculator
import com.team10.backend.domain.settlement.service.SettlementBatchService
import com.team10.backend.domain.settlement.service.SettlementReconciliationService
import com.team10.backend.domain.settlement.service.internal.ReconciliationInternalResult
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.UserFixture
import com.team10.backend.fixture.helper.OrderTestHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@DataJpaTest  // ✅ JPA 관련 빈만 로드 (빠른 시작)
@Import(
    FeeCalculator::class,
    DefaultFeePolicy::class
)  // ✅ 테스트할 서비스만 수동 등록
@ActiveProfiles("test")  // ✅ 테스트용 application-test.yml 적용
@Transactional  // ✅ 각 테스트 종료 시 자동 롤백 (데이터 오염 방지 🎯)
class SettlementBatchServiceIntegrationTest {
    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var reconciliationService: SettlementReconciliationService
    private lateinit var settlementBatchService: SettlementBatchService

    private lateinit var orderHelper: OrderTestHelper
    private lateinit var testSeller: User
    private val targetDate: LocalDate = LocalDate.now()

    private fun OrderTestHelper.OrderContext.getManagedPayment(): Payment {
        // 1. 미처리된 INSERT 를 실행하여 ID 가 DB 에 반영되도록 함
        entityManager.flush()

        // 2. JPQL 로 직접 조회하여 관리되는 (managed) 인스턴스 확보
        return entityManager.createQuery(
            "SELECT p FROM Payment p WHERE p.order.id = :orderId AND p.status = :status",
            Payment::class.java
        )
            .setParameter("orderId", this.order.id)
            .setParameter("status", PaymentStatus.PAID)
            .resultList
            .firstOrNull()
            ?: throw IllegalStateException("Payment not found for order ${this.order.id}")
    }

    @BeforeEach
    fun setUp() {
        // 1. MockK 목 객체 생성
        reconciliationService = mockk()

        // 2. FeeCalculator, DefaultFeePolicy 는 @Import 로 자동 주입되므로 직접 생성
        //    또는 @Autowired 로 주입받아도 됨
        val feeCalculator = FeeCalculator(DefaultFeePolicy())

        // 3. SettlementBatchService 수동 생성 (의존성 주입)
        settlementBatchService = SettlementBatchService(
            paymentRepository = paymentRepository,
            settlementRepository = settlementRepository,
            userRepository = userRepository,
            feeCalculator = feeCalculator,
            reconciliationService = reconciliationService,
            entityManager = entityManager,
        )

        // 4. 헬퍼 초기화
        orderHelper = OrderTestHelper(
            userRepository = userRepository,
            productRepository = productRepository,
            orderRepository = orderRepository,
        )

        // 5. Fixture 로 생성한 후 실제 DB 에 저장
        testSeller = userRepository.save(
            UserFixture.createWithSellerInfo(
                email = "integration-seller@test.com",
                role = Role.SELLER
            )
        )
    }

    @Test
    @DisplayName("실제 DB 에서 미정산 결제 조회 후 정산 생성까지 전체 플로우 검증")
    fun `full settlement flow with real JPA entities`() {
        // given: 실제 DB 에 결제 3 건 저장
        val orderCtx1 = orderHelper.createCompleteOrder(seller = testSeller, orderNumber = "ORD-INT-001")
        val orderCtx2 = orderHelper.createCompleteOrder(seller = testSeller, orderNumber = "ORD-INT-002")
        val orderCtx3 = orderHelper.createCompleteOrder(seller = testSeller, orderNumber = "ORD-INT-003")

        val payment1 = orderCtx1.getManagedPayment()
        val payment2 = orderCtx2.getManagedPayment()
        val payment3 = orderCtx3.getManagedPayment()


        val paymentListSlot = slot<List<Payment>>()

        // ReconciliationService 목킹: 모두 매칭된 결과 반환
        every { reconciliationService.reconcileInternal(
            capture(paymentListSlot),
            any(),
            any()
        ) } answers {
            val payments = firstArg<List<Payment>>()
            ReconciliationInternalResult(
                matched = paymentListSlot.captured,
                unmatched = emptyList(),
                pgResponseTime = null
            )
        }

        // when: 배치 실행
        val result = settlementBatchService.executeDailySettlement(
            sellerId = testSeller.id!!,
            targetDate = targetDate,
            force = false
        )

        // then: 결과 검증
        assertThat(result.status).isEqualTo(SettlementBatchResult.Status.SUCCESS)
        assertThat(result.summary.settledCount).isEqualTo(3)
        assertThat(result.summary.totalGrossAmount).isEqualTo(
            (orderCtx1.payment!!.totalAmount + orderCtx2.payment!!.totalAmount + orderCtx3.payment!!.totalAmount).toLong()
        )

        // ✅ 실제 DB 에 정산 마스터가 저장되었는지 검증
        val savedSettlement = settlementRepository.findBySellerIdAndPeriodStartAndPeriodEnd(
            testSeller.id!!, targetDate, targetDate
        )

        assertThat(savedSettlement).isNotNull()
        assertThat(savedSettlement!!.settlementNo).isEqualTo(result.settlementNo)
        assertThat(savedSettlement.netSettlementAmount).isGreaterThan(0)

        // ✅ 정산 명세도 함께 저장되었는지 검증
        assertThat(savedSettlement.details).hasSize(3)
        assertThat(savedSettlement.details.map { it.payment.id })
            .containsExactlyInAnyOrder(payment1.id, payment2.id, payment3.id)

        // ✅ 원 결제 건의 settlement 관계가 설정되었는지 검증 (양방향)
        savedSettlement.details.forEach { detail ->
            assertThat(detail.settlement).isEqualTo(savedSettlement)
        }
    }

    @Test
    @DisplayName("동일 날짜 재실행 시 멱등성에 의해 스킵된다")
    fun `idempotency prevents duplicate settlement on re-run`() {
        // given: 첫 번째 실행으로 정산 생성
        val orderCtx = orderHelper.createMinimalOrder(seller = testSeller)
        val payment = orderCtx.payment!!

        val paymentListSlot = slot<List<Payment>>()

        every { reconciliationService.reconcileInternal(
            capture(paymentListSlot),
            any(),
            any()
        ) } answers {
            val payments = firstArg<List<Payment>>()
            ReconciliationInternalResult(
                matched = paymentListSlot.captured,
                unmatched = emptyList(),
                pgResponseTime = null
            )
        }

        // 첫 번째 실행
        val firstResult = settlementBatchService.executeDailySettlement(
            sellerId = testSeller.id!!,
            targetDate = targetDate
        )
        assertThat(firstResult.status).isNotEqualTo(SettlementBatchResult.Status.SKIPPED)

        // when: 동일 조건으로 재실행 (force=false)
        val secondResult = settlementBatchService.executeDailySettlement(
            sellerId = testSeller.id!!,
            targetDate = targetDate,
            force = false  // ✅ 기본값: 멱등성 체크 활성화
        )

        // then: 두 번째는 SKIPPED
        assertThat(secondResult.status).isEqualTo(SettlementBatchResult.Status.SKIPPED)

        // ✅ DB 에 정산이 하나만 존재해야 함
        val count = settlementRepository.countBySellerIdAndPeriodStartAndPeriodEnd(
            testSeller.id!!, targetDate, targetDate
        )
        assertThat(count).isEqualTo(1L)
    }
}