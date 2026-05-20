package com.team10.backend.global.exception

import org.springframework.http.HttpStatus

open class BusinessException : RuntimeException {
    val errorCode: ErrorCode

    constructor(errorCode: ErrorCode) : super(errorCode.message) {
        this.errorCode = errorCode
    }

    constructor(errorCode: ErrorCode, detail: String?) : super(detail) // errorCode.message 대신 상세 메시지 사용
    {
        this.errorCode = errorCode
    }

    protected constructor(errorCode: ErrorCode, message: String, cause: Throwable?)
            : super(message, cause) {
        this.errorCode = errorCode
    }

    val status: HttpStatus
        get() = errorCode.status
}
