package com.team10.backend.global.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
class ApiResponse<T> private constructor(
    val success: Boolean, // 성공, 실패 여부
    val data: T?, // 실제 데이터 (상품, 주문 등등)
    val error: ErrorInfo? // 에러 정보
) {
    data class ErrorInfo(
        val code: String,
        val message: String
    )

    companion object {
        // 성공했을 때, 데이터 있음 (조회 메서드에서 많이 쓸 것 같습니다)
        // 예시 { "success": true, "data": { "name": "ex1", "price": 5000 } }
        fun <T> ok(data: T): ApiResponse<T> {
            return ApiResponse(true, data, null)
        }

        // 성공했을 때, 데이터 없음 (delete?)
        // 예시 { "success": true }
        fun ok(): ApiResponse<Void> {
            return ApiResponse(true, null, null)
        }

        fun error(code: String, message: String): ApiResponse<Void> {
            return ApiResponse(false, null, ErrorInfo(code, message))
        }
    }
}
