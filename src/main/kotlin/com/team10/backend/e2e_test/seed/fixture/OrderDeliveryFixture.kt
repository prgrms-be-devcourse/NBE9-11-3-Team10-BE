package com.team10.backend.e2e_test.seed.fixture

import com.team10.backend.domain.order.entity.Order
import com.team10.backend.domain.order.entity.OrderDelivery
import com.team10.backend.domain.order.enums.DeliveryStatus
import net.datafaker.Faker

/**
 * ✅ OrderDelivery 엔티티 전용 테스트 데이터 생성 픽스처
 * - 도메인 로직 (startReady, updateTracking 등) 준수
 * - 현실적인 한국 주소/송장번호 생성
 * - OrderSeedFixture 에서 직접 활용 가능
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
        trackingNumber: String? = null
    ): OrderDelivery {
        return OrderDelivery(
            deliveryAddress = deliveryAddress,
            trackingNumber = trackingNumber
        )
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
        trackingNumber: String? = null
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
        trackingNumber: String = generateTrackingNumber()
    ): OrderDelivery {
        return create(deliveryAddress).apply {
            updateTracking(trackingNumber)
        }
    }

    /**
     * [시나리오] 배송 완료 (COMPLETED)
     * - SHIPPING 상태 이후 수동으로 상태 전이
     */
    fun createCompleted(
        deliveryAddress: String = generateRealisticKoreanAddress(),
        trackingNumber: String = generateTrackingNumber()
    ): OrderDelivery {
        return createShipping(deliveryAddress, trackingNumber).apply {
            // status 필드가 protected setter 이므로 리플렉션 또는 테스트 전용 확장 함수 활용
            // 또는 도메인에 completed() 메서드가 있다면 해당 메서드 호출 권장
            this::class.java.getDeclaredField("status").apply {
                isAccessible = true
                set(this@apply, DeliveryStatus.COMPLETED)
            }
        }
    }

    /**
     * [시나리오] 배송 취소 (CANCELLED)
     * - 주문 취소와 연동된 시나리오

    fun createCancelled(
        deliveryAddress: String = generateRealisticKoreanAddress()
    ): OrderDelivery {
        return create(deliveryAddress).apply {
            this::class.java.getDeclaredField("status").apply {
                isAccessible = true
                set(this@apply, DeliveryStatus.CANCELLED)
            }
        }
    }
     */
    // ─────────────────────────────────────────────
    // 🔗 Order 와의 양방향 관계 설정 헬퍼
    // ─────────────────────────────────────────────

    /**
     * Order 엔티티와 연결된 배송 정보 생성
     * - OrderSeedFixture.createEntity() 와 연동하여 사용 권장
     */
    fun createWithOrder(
        order: Order,
        deliveryAddress: String = generateRealisticKoreanAddress(),
        trackingNumber: String? = null,
        initialStatus: DeliveryStatus = DeliveryStatus.READY
    ): OrderDelivery {
        val delivery = when (initialStatus) {
            DeliveryStatus.READY -> createReady(deliveryAddress, trackingNumber)
            DeliveryStatus.SHIPPING -> createShipping(deliveryAddress, trackingNumber ?: generateTrackingNumber())
            DeliveryStatus.COMPLETED -> createCompleted(deliveryAddress, trackingNumber ?: generateTrackingNumber())
//            DeliveryStatus.CANCELLED -> createCancelled(deliveryAddress)
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
     * - 시/도 + 구/군 + 동 + 번지 + 호수 조합
     */
    fun generateRealisticKoreanAddress(): String {
        val cities = listOf(
            "서울특별시", "부산광역시", "인천광역시", "대전광역시",
            "대구광역시", "광주광역시", "울산광역시", "세종특별자치시"
        )
        val districts = listOf(
            "강남구", "서초구", "송파구", "마포구", "영등포구", "종로구",
            "해운대구", "연수구", "유성구", "수성구", "북구", "중구"
        )
        val streets = listOf(
            "테헤란로", "강남대로", "역삼로", "봉은사로", "선릉로", "학동로",
            "해운대해변로", "중앙로", "대덕대로", "동대구로"
        )

        val city = cities.random()
        val district = districts.random()
        val street = streets.random()
        val buildingNumber = faker.number().numberBetween(1, 300)
        val detail = "${faker.number().numberBetween(101, 2504)}호"

        return "$city $district $street $buildingNumber $detail"
    }

    /**
     * 국내 택배사 형식의 송장번호 생성
     * - 택배사별 포맷 준수 (자리수, 접두사)
     */
    fun generateTrackingNumber(
        carrier: String = listOf("CJ", "KOREA_POST", "HANJIN", "LOGEN", "CU", "KYUNG_DONG").random()
    ): String {
        return when (carrier) {
            "CJ" -> "123${faker.number().digits(9)}"           // CJ대한통운: 12자리
            "KOREA_POST" -> "990${faker.number().digits(10)}"  // 우체국택배: 13자리
            "HANJIN" -> "305${faker.number().digits(9)}"       // 한진택배: 12자리
            "LOGEN" -> "170${faker.number().digits(9)}"        // 로젠택배: 12자리
            "CU" -> "CU${faker.number().digits(10)}"           // CU택배: 12자리
            "KYUNG_DONG" -> "KD${faker.number().digits(10)}"   // 경동택배: 12자리
            else -> faker.number().digits(12)                  // 폴백: 12자리 숫자
        }
    }

    /**
     * 수령인 이름 생성 (한글 + 영문 혼합, 테스트 다양성 확보)
     */
    fun generateRecipientName(): String {
        return if (faker.bool().bool()) {
            // 한글 이름 (성 + 이름 2 글자)
            val surnames = listOf("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")
            val givenNames = listOf(
                "민수", "지은", "서준", "하은", "도윤", "예은", "시우", "아인",
                "준호", "수빈", "현우", "지우", "은서", "서연", "민지", "태양"
            )
            "${surnames.random()}${givenNames.random()}"
        } else {
            faker.name().fullName()
        }
    }
}