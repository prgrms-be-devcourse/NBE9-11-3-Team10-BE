package com.team10.backend.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI {
        // ProblemDetail 확장 필드 정의
        val errorSchema = Schema<Any?>()
            .type("object")
            .description("RFC 7807 Problem Detail + 확장 필드")
            .addProperty("type", StringSchema().example("https://api.exam   ple.com/errors/USER_001"))
            .addProperty("title", StringSchema().example("Not Found"))
            .addProperty("status", IntegerSchema().example(404))
            .addProperty("detail", StringSchema().example("회원을 찾을 수 없습니다."))
            .addProperty("instance", StringSchema().example("/api/v1/users/999"))
            .addProperty("errorCode", StringSchema().example("USER_001"))
            .addProperty("timestamp", StringSchema().example("2026-04-14T10:30:00"))
            .addProperty("traceId", StringSchema().example("a1b2c3d4"))

        return OpenAPI()
            .components(Components().addSchemas("ProblemDetail", errorSchema))
    }
}