package com.team10.backend.global.security

import com.team10.backend.global.constant.CookieConstants.ACCESS_TOKEN
import com.team10.backend.global.util.CookieUtil
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val cookieUtil: CookieUtil,
    private val tokenProvider: TokenProvider
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = cookieUtil.getCookieValue(request, ACCESS_TOKEN)

        if (token.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val authentication = tokenProvider.getAuthentication(token)
            SecurityContextHolder.getContext().authentication = authentication
        } catch (e: ExpiredJwtException) {
            log.debug("JWT expired")
        } catch (e: JwtException) {
            log.warn("Invalid JWT")
        } catch (e: Exception) {
            log.error("Unexpected error in JWT filter", e)
        }

        filterChain.doFilter(request, response)
    }
}
