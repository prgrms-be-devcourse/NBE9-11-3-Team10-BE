package com.team10.backend.e2e_test.seed.helper

import com.team10.backend.domain.user.repository.UserRepository
import com.team10.backend.e2e_test.seed.fixture.UserSeedFixture
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class UserSeedHelper(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun seedAll() {
        // 이미 시딩되었으면 건너뜀
        if (userRepository.existsByEmail("buyer@example.com")) return

        val encodedPassword = passwordEncoder.encode(UserSeedFixture.DEFAULT_PASSWORD) ?: throw IllegalStateException("Password encoding failed")

        // 1. 사용자 엔티티 생성 및 저장
        val savedBuyer = userRepository.save(
            UserSeedFixture.toEntity(UserSeedFixture.SEED_USERS[0], encodedPassword)
        )
        val savedSeller = userRepository.save(
            UserSeedFixture.toEntity(UserSeedFixture.SEED_USERS[1], encodedPassword)
        )

        // ✅ 2. 저장된 엔티티를 Fixture 에 주입 (다른 픽스처에서 참조 가능하도록)
        UserSeedFixture.TEST_BUYER = savedBuyer
        UserSeedFixture.TEST_SELLER = savedSeller
    }
}