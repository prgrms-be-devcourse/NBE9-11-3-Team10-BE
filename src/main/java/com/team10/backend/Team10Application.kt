package com.team10.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.retry.annotation.EnableRetry

@EnableRetry
@SpringBootApplication
@EnableJpaAuditing
class Team10Application {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<Team10Application>(*args)
        }
    }
}
