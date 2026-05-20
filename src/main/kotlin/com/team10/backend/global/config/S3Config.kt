package com.team10.backend.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
class S3Config {
    @Bean
    fun s3Client(@Value("\${cloud.aws.region}") region: String): S3Client {
        return S3Client.builder()
            .region(Region.of(region))
            .build()
    }

    @Bean
    fun s3Presigner(@Value("\${cloud.aws.region}") region: String): S3Presigner {
        return S3Presigner.builder()
            .region(Region.of(region))
            .build()
    }
}