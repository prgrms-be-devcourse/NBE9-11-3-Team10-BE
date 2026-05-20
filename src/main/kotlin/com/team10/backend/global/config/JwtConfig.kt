package com.team10.backend.global.config

import com.team10.backend.global.security.TokenProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JwtConfig(
    @Value("\${custom.jwt.secretKey}")
    private val secretKey: String,

    @Value("\${custom.jwt.expireTime}")
    private val expireTime: Long
) {

    @Bean
    fun tokenProvider(): TokenProvider =
        TokenProvider(secretKey, expireTime)

}
