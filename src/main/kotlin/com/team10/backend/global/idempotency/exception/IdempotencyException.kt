package com.team10.backend.global.idempotency.exception

import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode

class IdempotencyException(
    errorCode: ErrorCode,
    message: String? = null,
    cause: Throwable? = null
) : BusinessException(
    errorCode = errorCode,
    message = message ?: errorCode.message,
    cause = cause
)