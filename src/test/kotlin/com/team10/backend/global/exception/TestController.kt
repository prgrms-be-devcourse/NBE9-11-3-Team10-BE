package com.team10.backend.global.exception

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test")
class TestController {
    @GetMapping("/business")
    fun business() {
        throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }

    @GetMapping("/unexpected")
    fun unexpected() {
        throw RuntimeException("테스트용 예외 - 노출되면 안 됨")
    }

    @PostMapping(value = ["/valid"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun valid(@Valid @RequestBody dto: TestDto) {
    } // ✅ Spring @RequestBody 사용

    @GetMapping("/param-type")
    fun typeMismatch(@RequestParam id: Int) {
    }

    @GetMapping("/param-missing")
    fun missingParam(@RequestParam name: String) {
    }

    @GetMapping("/validated")
    fun constraintViolation(@RequestParam @NotBlank code: String) {
    }

    @GetMapping("/not-found-test")
    fun notFoundTest() {
    } // 존재하지 않는 경로는 별도로 테스트

    @PostMapping("/method-test")
    fun methodTest() {
    } // GET 으로 호출 시 405 테스트용

    @PostMapping(value = ["/media-type-test"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun mediaTypeTest(@RequestBody body: Map<String, Any>) {
    } // wrong Content-Type 테스트용

    @GetMapping("/db-violation")
    fun dbViolation() {
        // 실제 DB 에러 시뮬레이션
        throw DataIntegrityViolationException("Duplicate entry for key 'email'")
    }

    data class TestDto(
        @field:NotBlank
        val name: String? = null,

        @field:Email
        val email: String? = null
    )
}