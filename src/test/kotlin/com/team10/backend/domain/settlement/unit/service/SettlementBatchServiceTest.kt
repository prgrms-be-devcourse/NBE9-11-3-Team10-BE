package com.team10.backend.domain.settlement.unit.service

import com.team10.backend.domain.order.repository.PaymentRepository
import com.team10.backend.domain.settlement.dto.SettlementBatchResult
import com.team10.backend.domain.settlement.entity.Settlement
import com.team10.backend.domain.settlement.enums.UnmatchedReason
import com.team10.backend.domain.settlement.repository.SettlementRepository
import com.team10.backend.domain.settlement.service.FeeCalculator
import com.team10.backend.domain.settlement.service.FeePolicy
import com.team10.backend.domain.settlement.service.SettlementBatchService
import com.team10.backend.domain.settlement.service.SettlementReconciliationService
import com.team10.backend.domain.settlement.service.internal.ReconciliationInternalResult
import com.team10.backend.domain.settlement.service.internal.UnmatchedInternalDetail
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.fixture.OrderFixture
import com.team10.backend.fixture.PaymentFixture
import com.team10.backend.fixture.UserFixture
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.util.*

@ExtendWith(MockKExtension::class)  // ✅ MockK 확장 활성화
class SettlementBatchServiceTest {

    // 🔹 Mocks
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var userRepository: UserRepository
    private lateinit var feeCalculator: FeeCalculator
    private lateinit var reconciliationService: SettlementReconciliationService

    // 🔹 SUT (System Under Test)
    private lateinit var settlementBatchService: SettlementBatchService

    // 🔹 Test Data
    private val testSellerId: Long = 100L
    private val targetDate: LocalDate = LocalDate.of(2026, 5, 14)
    private lateinit var testSeller: User

    @BeforeEach
    fun setUp() {
        // 1. Mock 초기화
        paymentRepository = mockk()
        settlementRepository = mockk()
        userRepository = mockk()
        feeCalculator = mockk()
        reconciliationService = mockk()

        // 2. SUT 생성
        settlementBatchService = SettlementBatchService(
            paymentRepository = paymentRepository,
            settlementRepository = settlementRepository,
            userRepository = userRepository,
            feeCalculator = feeCalculator,
            reconciliationService = reconciliationService
        )

        // 3. 테스트용 판매자 생성 (Fixture 활용)
        testSeller = UserFixture.createWithSellerInfo(
            email = "seller@test.com",
            role = Role.SELLER
        )
        // JPA ID 가 없는 테스트 객체를 위해 임시 ID 주입 (Reflection 활용)
        ReflectionTestUtils.setField(testSeller, "id", testSellerId)
    }

    @Nested
    @DisplayName("executeDailySettlement() - 멱등성 체크")
    inner class IdempotencyTest {

        @Test
        fun `기존 정산 이력이 있으면 SKIPPED 를 반환한다`() {
            // given
            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                testSellerId, targetDate, targetDate
            ) } returns true

            // when
            val result = settlementBatchService.executeDailySettlement(
                sellerId = testSellerId,
                targetDate = targetDate,
                force = false
            )

            // then
            assertThat(result.status).isEqualTo(SettlementBatchResult.Status.SKIPPED)

            // verify: 이후 로직은 전혀 실행되지 않음
            verify(exactly = 0) { userRepository.findById(any()) }
            verify(exactly = 0) { paymentRepository.findUnsettledPaymentsBySellerAndPeriod(any(), any(), any()) }
        }

        @Test
        fun `force=true 이면 기존 이력이 있어도 정산을 실행한다`() {
            // given
            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                testSellerId, targetDate, targetDate
            ) } returns true
            every { userRepository.findById(testSellerId) } returns Optional.of(testSeller)
            every { paymentRepository.findUnsettledPaymentsBySellerAndPeriod(
                testSellerId, targetDate, targetDate.plusDays(1)
            ) } returns emptyList()  // 결제 건 없음 시나리오

            // when
            val result = settlementBatchService.executeDailySettlement(
                sellerId = testSellerId,
                targetDate = targetDate,
                force = true  // ✅ 강제 실행
            )

            // then
            assertThat(result.status).isNotEqualTo(SettlementBatchResult.Status.SKIPPED)
            verify { userRepository.findById(testSellerId) }  // 판매자 조회는 실행됨
        }
    }

    @Nested
    @DisplayName("executeDailySettlement() - 판매자/결제 검증")
    inner class ValidationTest {

        @Test
        fun `존재하지 않는 판매자 ID 면 예외를 던진다`() {
            // given
            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                any(), any(), any()
            ) } returns false
            every { userRepository.findById(testSellerId) } returns Optional.empty()

            // when & then
            val exception = assertThrows<IllegalArgumentException> {
                settlementBatchService.executeDailySettlement(
                    sellerId = testSellerId,
                    targetDate = targetDate
                )
            }
            assertThat(exception.message).contains("Seller not found")
        }

        @Test
        fun `미정산 결제가 없으면 성공 상태로 빈 결과를 반환한다`() {
            // given
            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                any(), any(), any()
            ) } returns false
            every { userRepository.findById(testSellerId) } returns Optional.of(testSeller)
            every { paymentRepository.findUnsettledPaymentsBySellerAndPeriod(
                testSellerId, targetDate, targetDate.plusDays(1)
            ) } returns emptyList()

            // when
            val result = settlementBatchService.executeDailySettlement(
                sellerId = testSellerId,
                targetDate = targetDate
            )

            // then
            assertThat(result.status).isEqualTo(SettlementBatchResult.Status.SUCCESS)
            assertThat(result.summary.settledCount).isEqualTo(0)
            assertThat(result.summary.totalGrossAmount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("executeDailySettlement() - 정산 대조(Reconciliation) 연동")
    inner class ReconciliationTest {

        @Test
        fun `불일치 결제는 UNCERTAIN 으로 마킹되고 정산에서 제외된다`() {
            // given
            val order = OrderFixture.create(user = testSeller)
            val matchedPayment = PaymentFixture.createPaid(order = order, amount = 10_000)
            val unmatchedPayment = PaymentFixture.createPaid(order = order, amount = 20_000)

            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                any(), any(), any()
            ) } returns false
            every { userRepository.findById(testSellerId) } returns Optional.of(testSeller)
            every { paymentRepository.findUnsettledPaymentsBySellerAndPeriod(
                testSellerId, targetDate, targetDate.plusDays(1)
            ) } returns listOf(matchedPayment, unmatchedPayment)

            // Reconciliation 결과 목킹: 1 건 매칭, 1 건 불일치(금액 차이)
            every { reconciliationService.reconcileInternal(
                listOf(matchedPayment, unmatchedPayment),
                testSellerId,
                targetDate
            ) } returns ReconciliationInternalResult(
                matched = listOf(matchedPayment),
                unmatched = listOf(
                    UnmatchedInternalDetail(
                        payment = unmatchedPayment,
                        reason = UnmatchedReason.AMOUNT_MISMATCH,
                        pgAmount = 19_900,
                        dbAmount = 20_000
                    )
                ),
                pgResponseTime = null
            )

            // FeeCalculator 목킹
            val mockPolicy = mockk<FeePolicy>()
            every { feeCalculator.getPolicy(matchedPayment) } returns mockPolicy
            every { mockPolicy.calculate(10_000) } returns 300L  // 3% + 100 원

            // SettlementRepository save 목킹 (저장된 것처럼 가짜 Settlement 반환)
            val savedSettlement = mockk<Settlement>(relaxed = true)
            every { savedSettlement.id } returns 999L
            every { savedSettlement.settlementNo } returns "STL-20260514-0100"
            every { settlementRepository.save(any()) } returns savedSettlement

            // when
            val result = settlementBatchService.executeDailySettlement(
                sellerId = testSellerId,
                targetDate = targetDate
            )

            // then
            assertThat(result.status).isEqualTo(SettlementBatchResult.Status.PARTIAL_SUCCESS)
            assertThat(result.summary.settledCount).isEqualTo(1)  // 매칭된 1 건만 정산
            assertThat(result.summary.uncertainCount).isEqualTo(1)  // 불일치 1 건
            assertThat(result.summary.netSettlementAmount).isEqualTo(10_000 - 300)  // gross - fee

            // verify: 불일치 결제에 markAsUncertain() 호출됨
            assertThat(unmatchedPayment.status.name).isEqualTo("UNCERTAIN")
        }
    }

    @Nested
    @DisplayName("executeDailySettlement() - 정산 명세 생성 및 집계")
    inner class SettlementDetailTest {

        @Test
        fun `청크 단위로 정산 명세가 생성되고 집계값이 정확히 계산된다`() {
            // given: 150 건 결제 (청크 사이즈 100 으로 2 청크 처리)
            val payments = (1..150).map { i ->
                val order = OrderFixture.create(user = testSeller, orderNumber = "ORD-TEST-$i")
                PaymentFixture.createPaid(order = order, amount = 1_000 * i)
            }

            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                any(), any(), any()
            ) } returns false
            every { userRepository.findById(testSellerId) } returns Optional.of(testSeller)
            every { paymentRepository.findUnsettledPaymentsBySellerAndPeriod(
                testSellerId, targetDate, targetDate.plusDays(1)
            ) } returns payments
            every { reconciliationService.reconcileInternal(any(), any(), any()) } returns
                    ReconciliationInternalResult(
                        matched = payments,  // 모두 매칭
                        unmatched = emptyList(),
                        pgResponseTime = null
                    )

            // FeeCalculator: 정률 3% + 정액 100 원 정책
            val mockPolicy = mockk<FeePolicy>(relaxed = true)
            every { feeCalculator.getPolicy(any()) } returns mockPolicy
            every { mockPolicy.calculate(any()) } answers {
                val amount = firstArg<Long>()
                (amount * 0.03).toLong() + 100
            }

            val savedSettlement = mockk<Settlement>(relaxed = true)
            every { savedSettlement.id } returns 888L
            every { savedSettlement.settlementNo } returns "STL-20260514-0100"
            every { settlementRepository.save(any()) } returns savedSettlement

            // when
            val result = settlementBatchService.executeDailySettlement(
                sellerId = testSellerId,
                targetDate = targetDate
            )

            // then
            assertThat(result.status).isEqualTo(SettlementBatchResult.Status.SUCCESS)
            assertThat(result.summary.settledCount).isEqualTo(150)

            // 집계값 검증: Σ(1000*i) = 1000 * (1+150)*150/2 = 11,325,000
            val expectedGross = (1..150).sumOf { 1_000L * it }
            assertThat(result.summary.totalGrossAmount).isEqualTo(expectedGross)

            // fee = gross * 0.03 + 100 * 150
            val expectedFee = (1..150).sumOf { (1_000L * it * 0.03).toLong() + 100 }
            assertThat(result.summary.totalFeeAmount).isEqualTo(expectedFee)

            // net = gross - fee (refund=0)
            assertThat(result.summary.netSettlementAmount)
                .isEqualTo(expectedGross - expectedFee)
        }

        @Test
        fun `정산 명세 생성 중 예외 발생 시 에러는 기록되고 배치 상태는 FAILED 가 된다`() {
            // given
            val order = OrderFixture.create(user = testSeller)
            val goodPayment = PaymentFixture.createPaid(order = order, amount = 10_000)
            val badPayment = PaymentFixture.createPaid(order = order, amount = -100)  // 음수 금액으로 예외 유발

            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                any(), any(), any()
            ) } returns false
            every { userRepository.findById(testSellerId) } returns Optional.of(testSeller)
            every { paymentRepository.findUnsettledPaymentsBySellerAndPeriod(
                testSellerId, targetDate, targetDate.plusDays(1)
            ) } returns listOf(goodPayment, badPayment)
            every { reconciliationService.reconcileInternal(any(), any(), any()) } returns
                    ReconciliationInternalResult(
                        matched = listOf(goodPayment, badPayment),
                        unmatched = emptyList(),
                        pgResponseTime = null
                    )

            val mockPolicy = mockk<FeePolicy>(relaxed = true)
            every { feeCalculator.getPolicy(goodPayment) } returns mockPolicy
            every { mockPolicy.calculate(10_000) } returns 400L
            // badPayment 는 계산 중 예외 발생
            every { feeCalculator.getPolicy(badPayment) } throws IllegalArgumentException("Invalid amount")

            val savedSettlement = mockk<Settlement>(relaxed = true)
            every { savedSettlement.id } returns 777L
            every { savedSettlement.settlementNo } returns "STL-20260514-0100"
            every { settlementRepository.save(any()) } returns savedSettlement

            // when
            val result = settlementBatchService.executeDailySettlement(
                sellerId = testSellerId,
                targetDate = targetDate
            )

            // then: ✅ FAILED 가 맞음 (계산 오류 발생 시)
            assertThat(result.status).isEqualTo(SettlementBatchResult.Status.FAILED)  // ← 수정됨
            assertThat(result.summary.settledCount).isEqualTo(1)  // 성공한 건은 여전히 처리됨
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].orderNumber).isEqualTo(badPayment.orderNumber)
            assertThat(result.errors[0].errorCode).isEqualTo("CALCULATION_ERROR")

            // ✅ Settlement 은 저장되었지만, 실패 상태로 관리됨
            verify { settlementRepository.save(any()) }
        }

        @Test
        fun `재무 불일치 (unmatched) 만 있는 경우 PARTIAL_SUCCESS 가 된다`() {
            // given
            val order = OrderFixture.create(user = testSeller)
            val matchedPayment = PaymentFixture.createPaid(order = order, amount = 10_000)
            val unmatchedPayment = PaymentFixture.createPaid(order = order, amount = 20_000)

            every { settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                any(), any(), any()
            ) } returns false
            every { userRepository.findById(testSellerId) } returns Optional.of(testSeller)
            every { paymentRepository.findUnsettledPaymentsBySellerAndPeriod(
                testSellerId, targetDate, targetDate.plusDays(1)
            ) } returns listOf(matchedPayment, unmatchedPayment)

            // Reconciliation: 1 건 매칭, 1 건 불일치 (금액 차이)
            every { reconciliationService.reconcileInternal(any(), any(), any()) } returns
                    ReconciliationInternalResult(
                        matched = listOf(matchedPayment),
                        unmatched = listOf(
                            UnmatchedInternalDetail(
                                payment = unmatchedPayment,
                                reason = UnmatchedReason.AMOUNT_MISMATCH,
                                pgAmount = 19_900,
                                dbAmount = 20_000
                            )
                        ),
                        pgResponseTime = null
                    )

            // FeeCalculator: 정상 동작
            val mockPolicy = mockk<FeePolicy>()
            every { feeCalculator.getPolicy(matchedPayment) } returns mockPolicy
            every { mockPolicy.calculate(10_000) } returns 300L

            val savedSettlement = mockk<Settlement>(relaxed = true)
            every { savedSettlement.id } returns 999L
            every { savedSettlement.settlementNo } returns "STL-20260514-0100"
            every { settlementRepository.save(any()) } returns savedSettlement

            // when
            val result = settlementBatchService.executeDailySettlement(
                sellerId = testSellerId,
                targetDate = targetDate
            )

            // then: ✅ PARTIAL_SUCCESS (errors 는 없고, unmatched 만 있음)
            assertThat(result.status).isEqualTo(SettlementBatchResult.Status.PARTIAL_SUCCESS)
            assertThat(result.summary.settledCount).isEqualTo(1)
            assertThat(result.summary.uncertainCount).isEqualTo(1)
            assertThat(result.errors).isEmpty()  // ✅ 계산 오류 없음
        }
    }
}