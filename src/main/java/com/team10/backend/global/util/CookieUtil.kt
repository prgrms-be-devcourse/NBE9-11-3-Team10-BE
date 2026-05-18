package com.team10.backend.global.util

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component

@Component
class CookieUtil {
    fun addCookie(
        response: HttpServletResponse,
        name: String,
        value: String
    ) {
        val cookie = Cookie(name, value).apply {
            path = "/"
            isHttpOnly = true
            domain = "localhost"
            secure = true
            setAttribute("SameSite", "Strict")
        }
        response.addCookie(cookie)
    }

    fun getCookieValue(
        request: HttpServletRequest,
        name: String
    ): String? =
        request.cookies
                    ?.find { it.name == name }
                    ?.value

    fun deleteCookie(
        response: HttpServletResponse,
        name: String
    ) {
        val cookie = Cookie(name, "").apply {
            path = "/"
            isHttpOnly = true
            domain = "localhost"
            maxAge = 0
            secure = true
        }
        response.addCookie(cookie)
    }
}
