package com.team10.backend.domain.user.repository

import com.team10.backend.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface UserRepository : JpaRepository<User, Long> {
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean

    // TODO: Optional 제거 예정
    fun findByEmail(email: String): Optional<User>


    @Query(
        """
            SELECT u.id FROM User u WHERE u.role = "SELLER"
            """
    )
    fun findAllSellerIds(): List<Long>
}
