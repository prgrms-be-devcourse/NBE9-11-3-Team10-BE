package com.team10.backend.global.security

import com.team10.backend.global.exception.ErrorResponseUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {

        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/favicon.ico",
                        "/h2-console/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                    ).permitAll()

                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/stores/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/sellers/{id}").permitAll()

                    .requestMatchers("/api/v1/sellers/me", "/api/v1/stores/me/products/**").hasRole("SELLER")

                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .sessionManagement {it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)}
            .exceptionHandling { exception ->
                exception.authenticationEntryPoint { request, response, _ ->
                    writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 없습니다.", request)
                }
                exception.accessDeniedHandler { request, response, _ ->
                    writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.", request)
                }
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:3000")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }

    private fun writeError(
        response: HttpServletResponse,
        httpStatus: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest
    ) {
        response.apply {
            contentType = "application/json; charset=UTF-8"
            status = httpStatus.value()

            val errorResponse = ErrorResponseUtil.buildProblemDetail(httpStatus, code, message, request)
            writer.write(
                objectMapper.writeValueAsString(errorResponse)
            )
        }
    }

}
