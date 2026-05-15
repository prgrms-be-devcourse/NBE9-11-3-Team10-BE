package com.team10.backend.fixture.domain.settlement.unit.service

import com.team10.backend.domain.order.entity.Payment
import com.team10.backend.domain.settlement.service.SettlementReconciliationService
import com.team10.backend.fixture.OrderFixture
import com.team10.backend.fixture.PaymentFixture
import com.team10.backend.fixture.UserFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SettlementReconciliationServiceTest {

    private lateinit var reconciliationService: SettlementReconciliationService
    private val testSellerId = 999L

    @BeforeEach
    fun setUp() {
        reconciliationService = SettlementReconciliationService()
    }

    @Nested
    @DisplayName("reconcile() - 정상 매칭 시나리오")
    inner class ReconcileSuccess {

        @Test
        fun `모든 결제가 정상 매칭되면 matched 에만 포함된다`() {
            // given
            val seller = UserFixture.createWithSellerInfo()
            val payments = listOf(
                PaymentFixture.createPaid(order = OrderFixture.create(user = seller), amount = 10_000),
                PaymentFixture.createPaid(order = OrderFixture.create(user = seller), amount = 25_500),
                PaymentFixture.createPaid(order = OrderFixture.create(user = seller), amount = 100_000)
            )
            val targetDate = LocalDate.of(2026, 5, 14)

            // when
            val result = reconciliationService.reconcileInternal(payments, testSellerId, targetDate)

            // then
            assertThat(result.matched).hasSize(3)
            assertThat(result.matched).containsExactlyInAnyOrderElementsOf(payments)
            assertThat(result.unmatched).isEmpty()
            assertThat(result.pgResponseTime).isNotNull()
        }

        @Test
        fun `결제 목록이 비어있으면 빈 결과를 반환한다`() {
            // given
            val payments = emptyList<Payment>()
            val targetDate = LocalDate.now()

            // when
            val result = reconciliationService.reconcileInternal(payments, testSellerId, targetDate)

            // then
            assertThat(result.matched).isEmpty()
            assertThat(result.unmatched).isEmpty()
            assertThat(result.pgResponseTime).isNotNull()
        }

        @Test
        fun `단일 결제도 정상 처리된다`() {
            // given
            val seller = UserFixture.createWithSellerInfo()
            val payment = PaymentFixture.createPaid(
                order = OrderFixture.create(user = seller),
                amount = 50_000
            )
            val targetDate = LocalDate.now()

            // when
            val result = reconciliationService.reconcileInternal(listOf(payment), testSellerId, targetDate)

            // then
            assertThat(result.matched).hasSize(1)
            assertThat(result.matched[0].orderNumber).isEqualTo(payment.orderNumber)
            assertThat(result.unmatched).isEmpty()
        }
    }

    @Nested
    @DisplayName("reconcile() - 예외 상황 처리")
    inner class ReconcileEdgeCases {

        @Test
        fun `UNCERTAIN 상태 결제도 매칭 로직에는 포함된다`() {
            // given: 현재 모의 구현은 상태와 무관하게 금액만 비교하므로
            val seller = UserFixture.createWithSellerInfo()
            val uncertainPayment = PaymentFixture.createUncertain(
                order = OrderFixture.create(user = seller),
                amount = 30_000
            )
            val targetDate = LocalDate.now()

            // when
            val result = reconciliationService.reconcileInternal(listOf(uncertainPayment), testSellerId, targetDate)

            // then: 금액이 일치하므로 matched 에 포함됨 (상태 검증은 별도 로직에서 처리)
            assertThat(result.matched).hasSize(1)
            assertThat(result.unmatched).isEmpty()
        }

        @Test
        fun `대량 결제 건도 청크 로직 없이 정상 처리된다`() {
            // given: 현재 서비스는 청크 처리를 하지 않으므로 대량 데이터도 단순 반복
            val seller = UserFixture.createWithSellerInfo()
            val payments = (1..100).map {
                PaymentFixture.createPaid(
                    order = OrderFixture.create(user = seller),
                    amount = 1_000 * it
                )
            }
            val targetDate = LocalDate.now()

            // when
            val result = reconciliationService.reconcileInternal(payments, testSellerId, targetDate)

            // then
            assertThat(result.matched).hasSize(100)
            assertThat(result.unmatched).isEmpty()
        }
    }
}