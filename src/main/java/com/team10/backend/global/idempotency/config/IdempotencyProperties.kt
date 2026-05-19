package com.team10.backend.global.idempotency.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "idempotency")
data class IdempotencyProperties(
    val store: String = "memory" // "memory" | "jdbc" | "redis"
)