package com.team10.backend.domain.user.repository

import com.team10.backend.domain.user.entity.User
import com.team10.backend.domain.user.enums.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun findByEmail(email: String): User?
    fun findByEmailAndRole(email: String, role: Role): User?

    @Query(
        """
            SELECT u.id FROM User u WHERE u.role = "SELLER"
            """
    )
    fun findAllSellerIds(): List<Long>
}
