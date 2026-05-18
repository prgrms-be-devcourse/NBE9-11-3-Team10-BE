package com.team10.backend.e2e_test.seed
import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.enums.UserStatus
import com.team10.backend.domain.user.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class UserSeedFixture(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun seedAll() {
        // 이미 존재하면 중복 저장 방지
        if (userRepository.existsByEmail("buyer@example.com")) return

        val encodedPassword =
            passwordEncoder.encode("TestPass123!") ?: throw IllegalStateException("Password encoding failed")

        // 1. BUYER 시딩
        val buyer = User(
            email = "buyer@example.com",
            password = encodedPassword,
            name = "구매자",
            nickname = "구매자님",
            phoneNumber = "010-2222-3333",
            address = "서울시 강남구 테헤란로 123 202호",
            role = Role.BUYER,
            userStatus = UserStatus.ACTIVE,
        )
        userRepository.save(buyer)

        // 2. SELLER 시딩 (프로필은 별도 Helper 에서 연결)
        val seller = User(
            email = "seller@example.com",
            password = encodedPassword,
            name = "홍길동",
            nickname = "판매자님",
            phoneNumber = "010-1111-2222",
            address = "경기 성남시 분당구 판교역로 166 1102동 304호",
            role = Role.SELLER,
            userStatus = UserStatus.ACTIVE,
        )
        userRepository.save(seller)
    }
}