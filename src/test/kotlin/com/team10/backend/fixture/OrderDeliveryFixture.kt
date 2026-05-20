package com.team10.backend.fixture

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderDelivery
import com.team10.backend.domain.order.enums.DeliveryStatus
import net.datafaker.Faker

/**
 * ⚠️ TEST ONLY - DO NOT USE IN PRODUCTION CODE
 *
 * 이 클래스는 테스트 환경에서만 사용됩니다.
 * 운영 코드에서 직접 호출하면 안 됩니다.
 */

object OrderDeliveryFixture {
    private val faker = Faker()

    // ─────────────────────────────────────────────
    // ✅ 기본 생성 팩토리 (생성자 로직 준수)
    // ─────────────────────────────────────────────

    /**
     * [기본] 배송 정보 생성
     * - 생성자 호출 시점에는 status 가 아직 설정되지 않음 (null)
     * - 이후 startReady() 등의 비즈니스 메서드 호출로 상태 전이
     */
    fun create(
        deliveryAddress: String = generateRealisticKoreanAddress(),
        trackingNumber: String = generateTrackingNumber()
    ): OrderDelivery {
        return OrderDelivery.builder()
            .delivery_address(deliveryAddress)
            .tracking_number(trackingNumber)
            .build()
    }

    // ─────────────────────────────────────────────
    // 🚚 시나리오 기반 팩토리 (상태별)
    // ─────────────────────────────────────────────

    /**
     * [시나리오] 결제 완료 직후, 배송 준비 중 (READY)
     * - startReady() 호출로 상태 초기화
     */
    fun createReady(
        deliveryAddress: String = generateRealisticKoreanAddress(),
        trackingNumber: String = generateTrackingNumber()
    ): OrderDelivery {
        return create(deliveryAddress, trackingNumber).apply {
            startReady()
        }
    }

    /**
     * [시나리오] 송장 번호 입력 후 배송 중 (SHIPPING)
     * - updateTracking() 호출로 상태 + 트래킹 번호 동시 업데이트
     */
    fun createShipping(
        deliveryAddress: String = generateRealisticKoreanAddress(),
        trackingNumber: String = generateTrackingNumber() // 새 송장번호로 덮어씀
    ): OrderDelivery {
        return create(deliveryAddress).apply {
            updateTracking(trackingNumber) // 이 메서드 내부에서 status = SHIPPING 으로 설정
        }
    }

    /**
     * [시나리오] 배송 완료 (COMPLETED)

    fun createDelivered(
        deliveryAddress: String = generateRealisticKoreanAddress(),
        trackingNumber: String = generateTrackingNumber()
    ): OrderDelivery {
        return createShipping(deliveryAddress, trackingNumber).apply {
             this.status = DeliveryStatus.COMPLETED
        }
    }
     */

    /**
     * [시나리오] 배송 취소 (CANCELLED)
     * - 주문 취소와 연동된 시나리오

    fun createCancelled(
        deliveryAddress: String = generateRealisticKoreanAddress()
    ): OrderDelivery {
        return create(deliveryAddress).apply {
            // enum 에 따라 아래 주석 해제 후 사용
//             this.status = DeliveryStatus.CANCELLED
        }
    }
     */

    // ─────────────────────────────────────────────
    // # 🔗 Order 와의 양방향 관계 설정 헬퍼
    // ─────────────────────────────────────────────

    /**
     * Order 엔티티와 연결된 배송 정보 생성
     * - OrderFixture.create() 와 연동하여 사용 권장
     */
    fun createWithOrder(
        order: Order,
        deliveryAddress: String = generateRealisticKoreanAddress(),
        trackingNumber: String = generateTrackingNumber(),
        initialStatus: DeliveryStatus = DeliveryStatus.READY
    ): OrderDelivery {
        val delivery = when (initialStatus) {
            DeliveryStatus.READY -> createReady(deliveryAddress, trackingNumber)
            DeliveryStatus.SHIPPING -> createShipping(deliveryAddress, trackingNumber)
            // DeliveryStatus.DELIVERED -> createDelivered(...)
            // DeliveryStatus.CANCELLED -> createCancelled(...)
            else -> create(deliveryAddress, trackingNumber)
        }

        // 양방향 관계 설정 (OrderDelivery → Order)
        delivery.assignOrder(order)
        return delivery
    }

    // ─────────────────────────────────────────────
    // 🔁 유틸리티 메서드 (현실적인 더미 데이터 생성)
    // ─────────────────────────────────────────────

    /**
     * 한국 형식에 가까운 주소 생성
     * - 시/도 + 구/군 + 동 + 번지 조합
     */
    fun generateRealisticKoreanAddress(): String {
        val cities = listOf("서울특별시", "부산광역시", "인천광역시", "대전광역시", "대구광역시", "광주광역시")
        val districts = listOf("강남구", "서초구", "송파구", "마포구", "영등포구", "종로구", "해운대구", "연수구")
        val streets = listOf("테헤란로", "강남대로", "역삼로", "봉은사로", "선릉로", "학동로")

        val city = cities.random()
        val district = districts.random()
        val street = streets.random()
        val buildingNumber = faker.number().numberBetween(1, 300)
        val detail = "${faker.number().numberBetween(101, 2504)}호"

        return "$city $district $street $buildingNumber $detail"
    }

    /**
     * 국내 택배사 형식의 송장번호 생성
     * - 예: CJ대한통운: 123456789012, 우체국: 9901234567890
     */
    fun generateTrackingNumber(
        carrier: String = listOf("CJ", "KOREA_POST", "HANJIN", "LOGEN").random()
    ): String {
        return when (carrier) {
            "CJ" -> "123${faker.number().digits(9)}"       // 12자리
            "KOREA_POST" -> "990${faker.number().digits(10)}" // 13자리
            "HANJIN" -> "305${faker.number().digits(9)}"     // 12자리
            "LOGEN" -> "170${faker.number().digits(9)}"      // 12자리
            else -> faker.number().digits(12)
        }
    }

    /**
     * 수령인 이름 생성 (한글 + 영문 혼합)
     */
    fun generateRecipientName(): String {
        return if (faker.bool().bool()) {
            // 한글 이름 (성 + 이름 2글자)
            val surnames = listOf("김", "이", "박", "최", "정", "강", "조", "윤")
            val givenNames = listOf("민수", "지은", "서준", "하은", "도윤", "예은", "시우", "아인")
            "${surnames.random()}${givenNames.random()}"
        } else {
            faker.name().fullName()
        }
    }
}