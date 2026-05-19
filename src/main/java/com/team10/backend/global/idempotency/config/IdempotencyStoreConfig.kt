package com.team10.backend.global.idempotency.config

import com.team10.backend.global.idempotency.IdempotencyStore
import global.idempotency.impl.InMemoryIdempotencyStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IdempotencyStoreConfig {

    @Bean
    @ConditionalOnProperty(name = ["idempotency.store"], havingValue = "memory", matchIfMissing = true)
    fun memoryStore(): IdempotencyStore = InMemoryIdempotencyStore()
}