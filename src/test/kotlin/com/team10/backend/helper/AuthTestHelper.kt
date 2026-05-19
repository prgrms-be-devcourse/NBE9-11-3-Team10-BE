package com.team10.backend.helper

import com.team10.backend.domain.user.entity.User
import com.team10.backend.global.security.CustomUserPrincipal
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

object AuthTestHelper {
    fun setAuth(user: User) {
        val principal =
            CustomUserPrincipal(user.id, user.role)

        val authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
        )

        SecurityContextHolder.getContext().authentication = authentication
    }
}
