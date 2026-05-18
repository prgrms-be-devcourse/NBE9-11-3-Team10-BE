package com.team10.backend.global.security

import com.team10.backend.domain.user.enums.Role
import com.team10.backend.global.constant.JwtConstants.CLAIMS_ROLE
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.Date
import javax.crypto.SecretKey

class TokenProvider(
    secretKey: String,
    private val expireTime: Long
) {
    private val key: SecretKey =
        Keys.hmacShaKeyFor(
            Decoders.BASE64.decode(secretKey)
        )

    fun generateToken(id: Long, role: Role): String {
        val issuedAt = Date()
        val expiresAt = calculateExpiresAt(issuedAt)

        return Jwts.builder()
            .subject(id.toString())
            .claim(CLAIMS_ROLE, role)
            .issuedAt(issuedAt)
            .expiration(expiresAt)
            .signWith(key)
            .compact()
    }

    private fun calculateExpiresAt(issuedAt: Date): Date =
        Date(issuedAt.time + expireTime * 1000 * 60)

    fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun getAuthentication(token: String): Authentication {
        val claims = parseClaims(token)
        val userId = claims.subject.toLong()
        val role = Role.valueOf(
            claims.get(CLAIMS_ROLE, String::class.java)
        )

        return UsernamePasswordAuthenticationToken(
            CustomUserPrincipal(userId, role),
            null,
            listOf(
                SimpleGrantedAuthority("ROLE_" + role.name)
            )
        )
    }
}
