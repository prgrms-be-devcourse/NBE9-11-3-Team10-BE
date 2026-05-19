package com.team10.backend.global.config

import com.team10.backend.global.idempotency.interceptor.IdempotencyKeyInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val idempotencyKeyInterceptor: IdempotencyKeyInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        // 멱등성 키 인터셉터 등록
        registry.addInterceptor(idempotencyKeyInterceptor)
            .addPathPatterns("/api/**")  // 또는 "/api/**"
            .order(1)  // 낮은 번호일수록 먼저 실행
    }
}