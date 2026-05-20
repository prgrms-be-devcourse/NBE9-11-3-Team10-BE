package com.team10.backend.e2e_test.controller

import com.team10.backend.e2e_test.seed.E2eSeedService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = ["test.e2e.enabled"], havingValue = "true")
class E2eTestController(
    private val e2eSeedService: E2eSeedService
) {

    /**
     * ✅ Mock Server 의 POST /api/v1/__reset 이식
     * - Playwright globalSetup 에서 호출하여 테스트 시작 전 상태 초기화
     */
    @PostMapping("/__reset")
    fun resetAll(): ResponseEntity<Map<String, Any>> {
        try {
            e2eSeedService.resetAndSeed()

            val response: Map<String, Any> = mapOf(
                "success" to true,
                "message" to "All mock data has been reset successfully.",
                "resetAt" to LocalDateTime.now().toString(),
                "stores" to listOf(
                    "StoreProfileStore", "ProductStore", "FeedStore",
                    "CommentStore", "OrderStore"
                )
            )
            return ResponseEntity.ok(response)
        } catch (e: Exception) {
            val errorResponse: Map<String, Any> = mapOf(
                "success" to false,
                "error" to "Failed to reset mock data",
                "message" to (e.message ?: "Unknown error")
            )
            return ResponseEntity.status(500).body(errorResponse)
        }

    }
}