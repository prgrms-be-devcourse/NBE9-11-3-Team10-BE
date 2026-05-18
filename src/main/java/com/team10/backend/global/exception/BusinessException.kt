package com.team10.backend.global.exception

import org.springframework.http.HttpStatus

class BusinessException : RuntimeException {
    val errorCode: ErrorCode

    constructor(errorCode: ErrorCode) : super(errorCode.getMessage()) {
        this.errorCode = errorCode
    }

    constructor(errorCode: ErrorCode, detail: String?) : super(detail) // errorCode.message 대신 상세 메시지 사용
    {
        this.errorCode = errorCode
    }

    val status: HttpStatus
        get() = errorCode.getStatus()
}
