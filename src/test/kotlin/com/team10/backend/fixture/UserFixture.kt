package com.team10.backend.fixture

import com.team10.backend.domain.auth.dto.AuthRegisterRequest
import com.team10.backend.domain.user.entity.SellerInfo
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.enums.UserStatus
import net.datafaker.Faker

object UserFixture {
    // 한국어 기반 더미 데이터가 필요하면 Faker(Locale("ko")) 권장
    private val faker = Faker()

    /**
     * User 엔티티 생성 팩토리
     * - 기본값: Faker가 생성한 검증 규칙(@Pattern/@Email) 준수 데이터
     * - 테스트 목적에 따라 특정 필드만 오버라이드 가능
     */
    fun create(
        email: String = faker.internet().emailAddress(),
        password: String = generateValidPassword(),
        name: String = faker.name().fullName(),
        nickname: String = generateValidNickname(),
        phoneNumber: String = generateValidPhoneNumber(),
        address: String = faker.address().fullAddress(),
        userStatus: UserStatus = UserStatus.ACTIVE,
        role: Role = Role.BUYER,
        imageUrl: String? = faker.internet().url(),
        sellerInfo: SellerInfo? = null
    ): User {
        return User(
            email = email,
            password = password,
            name = name,
            nickname = nickname,
            phoneNumber = phoneNumber,
            address = address,
            userStatus = userStatus,
            role = role,
            imageUrl = imageUrl,
            sellerInfo = sellerInfo
        )
    }

    /**
     * SellerInfo 가 1:1로 연결된 User 생성
     * 엔티티의 attachSellerInfo()를 사용하여 양방향 관계를 JPA 관례에 맞게 설정
     */
    fun createWithSellerInfo(
        bio: String = faker.lorem().paragraph().take(490),
        businessNumber: String = "BRN-${faker.number().digits(10)}",
        role: Role = Role.SELLER,
        // 나머지 필드는 create() 의 기본값 또는 직접 주입
        email: String = faker.internet().emailAddress(),
        password: String = generateValidPassword(),
        name: String = faker.name().fullName(),
        nickname: String = generateValidNickname(),
        phoneNumber: String = generateValidPhoneNumber(),
        address: String = faker.address().fullAddress(),
    ): User {
        val user = create(
            email = email, password = password, name = name,
            nickname = nickname, phoneNumber = phoneNumber, address = address, role = role
        )

        val sellerInfo = SellerInfo()
        sellerInfo.updateSellerInfo(bio, businessNumber)

        // 엔티티에 정의된 관계 연결 메서드 사용
        user.attachSellerInfo(sellerInfo)
        return user
    }

    /**
     * Controller/Service 레이어 테스트를 위한 DTO 팩토리
     */
    fun createAuthRegisterRequest(
        email: String = faker.internet().emailAddress(),
        password: String = generateValidPassword(),
        name: String = faker.name().fullName(),
        nickname: String = generateValidNickname(),
        phoneNumber: String = generateValidPhoneNumber(),
        address: String = faker.address().fullAddress(),
        role: Role = Role.BUYER
    ) = AuthRegisterRequest(email, password, name, nickname, phoneNumber, address, role)

    // 🔍 검증 어노테이션 제약조건을 100% 만족하는 헬퍼 함수
    private fun generateValidPassword(): String {
        // ^(.*[A-Za-z])(.*\\d).{8,20}$ -> 영문 + 숫자 필수, 8~20자
        return "Test${faker.number().digits(4)}" // 예: Test1234 (8자)
    }

    private fun generateValidNickname(): String {
        // ^[A-Za-z0-9가-힣_]{2,20}$
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_"
        val length = faker.number().numberBetween(2, 18)
        return (1..length).map { allowed.random() }.joinToString("")
    }

    private fun generateValidPhoneNumber(): String {
        // ^010-?\\d{4}-?\\d{4}$
        return "010-${faker.number().digits(4)}-${faker.number().digits(4)}"
    }
}
