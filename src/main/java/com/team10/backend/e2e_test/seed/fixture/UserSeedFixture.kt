package com.team10.backend.e2e_test.seed.fixture

import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.enums.UserStatus

/**
 * ✅ 사용자 시드 데이터 정의 및 외부 참조용 저장소
 * - Spring Bean 이 아닌 단순 Kotlin object 로 정의하여 어디서든 정적 접근 가능
 */
object UserSeedFixture {

    // ✅ 외부 (CommentSeedFixture 등) 에서 참조할 수 있는 변수 선언
    // 초기값은 null 이며, UserSeedHelper 가 실제 저장 후 이 값을 채워줌
    var TEST_BUYER: User? = null
    var TEST_SELLER: User? = null

    /**
     * 테스트용 공통 데이터 정의 (필요시 활용)
     */
    const val DEFAULT_PASSWORD = "TestPass123!"

    data class SeedUserData(
        val email: String,
        val name: String,
        val nickname: String,
        val role: Role,
        val phoneNumber: String,
        val address: String
    )

    val SEED_USERS = listOf(
        SeedUserData(
            email = "buyer@example.com",
            name = "구매자",
            nickname = "구매자님",
            role = Role.BUYER,
            phoneNumber = "010-2222-3333",
            address = "서울시 강남구 테헤란로 123 202호"
        ),
        SeedUserData(
            email = "seller@example.com",
            name = "홍길동",
            nickname = "판매자님",
            role = Role.SELLER,
            phoneNumber = "010-1111-2222",
            address = "경기 성남시 분당구 판교역로 166 1102동 304호"
        )
    )

    /**
     * SeedUserData → User Entity 변환 헬퍼
     */
    fun toEntity(data: SeedUserData, password: String): User {
        return User(
            email = data.email,
            password = password,
            name = data.name,
            nickname = data.nickname,
            phoneNumber = data.phoneNumber,
            address = data.address,
            role = data.role,
            userStatus = UserStatus.ACTIVE
        )
    }
}