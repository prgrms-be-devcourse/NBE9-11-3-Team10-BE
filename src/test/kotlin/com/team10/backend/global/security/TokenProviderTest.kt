package com.team10.backend.global.security

import com.team10.backend.domain.user.enums.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import javax.crypto.SecretKey

internal class TokenProviderTest{

    private lateinit var tokenProvider: TokenProvider
    private lateinit var key: SecretKey

    @BeforeEach
    fun setUp() {
        val secretKey = "RjnNJn0bpT1nvG1AgH2vbTOvkB1iMzlX3+Evusj2n/U="
        val expireTime = 1L

        tokenProvider = TokenProvider(secretKey, expireTime)
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey))
    }

    @Test
    @DisplayName("토큰 생성 테스트")
    fun generateToken_success() {
        val token = tokenProvider.generateToken(1L, Role.BUYER)

        assertTrue(token.isNotBlank())
    }

    @Test
    @DisplayName("토큰에 id와 role이 포함된다")
    fun token_contains_claims() {
        val token = tokenProvider.generateToken(1L, Role.BUYER)

        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()

        assertEquals("1", claims.subject)
        assertEquals("BUYER", claims["role"].toString())
    }

    @Test
    @DisplayName("토큰 파싱이 정상적으로 된다")
    fun parseClaims_success() {
        val token = tokenProvider.generateToken(1L, Role.BUYER)

        val claims = tokenProvider.parseClaims(token)

        assertEquals("1", claims.subject)
        assertEquals("BUYER", claims["role"].toString())
    }

    @Test
    @DisplayName("Authentication 객체가 정상 생성되고 권한 정보가 제대로 들어간다")
    fun getAuthentication_success() {
        val token = tokenProvider.generateToken(1L, Role.BUYER)

        val authentication = tokenProvider.getAuthentication(token)

        val principal = authentication.principal as CustomUserPrincipal

        assertEquals(1L, principal.userId)
        assertEquals(Role.BUYER, principal.role)

        assertEquals(1, authentication.authorities.size)
        assertTrue(authentication.authorities.any { it.authority == "ROLE_BUYER" })}

//    @Test
//    @DisplayName("시간이 만료된 토큰은 예외가 발생한다")
//    fun parseClaims_fail_expireToken() {
//        val token = tokenProvider.generateToken(1L, Role.BUYER)
//
//        Thread.sleep(60001)
//
//        assertThrows(ExpiredJwtException::class.java) {
//            tokenProvider.parseClaims(token)
//        }
//    }

    @Test
    @DisplayName("잘못된 토큰은 예외가 발생한다")
    fun parseClaims_fail() {
        val invalidToken = "fake.token.value"

        assertThrows(Exception::class.java) {
            tokenProvider.parseClaims(invalidToken)
        }
    }
}
