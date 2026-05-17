package com.team10.backend.domain.auth.entity

import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class RefreshToken(
    @Column(nullable = false, unique = true)
    var token: String,

    var expiresAt: LocalDateTime,

    var isRevoked: Boolean,

    @ManyToOne(fetch = FetchType.LAZY)
    var user: User
) : BaseEntity() {

    fun update(token: String) {
        this.token = token
        this.expiresAt = LocalDateTime.now().plusDays(30)
    }

    fun revoke() {
        this.isRevoked = true
    }

    companion object {
        @JvmStatic
        fun create(token: String, user: User): RefreshToken {
            return RefreshToken(
                token,
                LocalDateTime.now().plusDays(30),
                false,
                user
            )
        }
    }
}
