package com.team10.backend.global.exception

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.retry.ExhaustedRetryException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import java.net.URI
import java.time.LocalDateTime

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        e: BusinessException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(e.status, e.message).apply {
            title = e.status.reasonPhrase
            type = URI.create("https://api.example.com/errors/${e.errorCode.code}")
            setProperty("errorCode", e.errorCode.code)
            setProperty("timestamp", LocalDateTime.now())
            setProperty("instance", request.requestURI)

            request.getAttribute("traceId")?.let {
                setProperty("traceId", it)
            }
        }

        return ResponseEntity.status(e.status).body(problemDetail)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            mapOf(
                "field" to error.field,
                "message" to (error.defaultMessage ?: "입력값이 올바르지 않습니다")
            )
        }

        val details = fieldErrors.joinToString(", ") { error ->
            "${error["field"]}: ${error["message"]}"
        }

        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "입력값 검증에 실패했습니다. ($details)"
        ).apply {
            title = "Bad Request"
            type = URI.create("https://api.example.com/errors/VALIDATION_FAILED")
            setProperty("errorCode", "VALIDATION_FAILED")
            setProperty("instance", request.requestURI)
            setProperty("timestamp", LocalDateTime.now())
            setProperty("validationErrors", fieldErrors)
        }

        return ResponseEntity.badRequest().body(problemDetail)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleJsonParseException(
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "요청 본문 형식이 올바르지 않습니다."
        ).apply {
            title = "Bad Request"
            type = URI.create("https://api.example.com/errors/INVALID_JSON")
            setProperty("errorCode", "INVALID_JSON")
            setProperty("instance", request.requestURI)
            setProperty("timestamp", LocalDateTime.now())
        }

        return ResponseEntity.badRequest().body(problemDetail)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        e: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val detail = "타입 변환 실패: 매개변수 '${e.name}'의 값 '${e.value}' 이(가) 올바르지 않습니다."

        return buildBadRequestProblem("TYPE_MISMATCH", detail, request)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(
        e: MissingServletRequestParameterException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val detail = "필수 파라미터 누락: '${e.parameterName}'"

        return buildBadRequestProblem("PARAM_MISSING", detail, request)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        e: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val detail = e.constraintViolations.joinToString(", ") { violation ->
            "${violation.propertyPath}: ${violation.message}"
        }

        return buildBadRequestProblem("CONSTRAINT_VIOLATION", detail, request)
    }

    // 공통 응답 생성 유틸 (중복 코드 제거용)
    private fun buildBadRequestProblem(
        errorCode: String,
        detail: String,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail).apply {
            title = "Bad Request"
            type = URI.create("https://api.example.com/errors/$errorCode")
            setProperty("errorCode", errorCode)
            setProperty("instance", request.requestURI)
            setProperty("timestamp", LocalDateTime.now())
        }

        return ResponseEntity.badRequest().body(problem)
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFound(
        e: NoHandlerFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        return buildProblemDetail(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            "요청한 리소스를 찾을 수 없습니다. (${e.requestURL})",
            request
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(
        e: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        return buildProblemDetail(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "지원하지 않는 HTTP 메서드입니다. 허용: ${e.supportedHttpMethods}",
            request
        )
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        return buildProblemDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "지원하지 않는 Content-Type 입니다.",
            request
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        // 실제 DB 오류 메시지는 노출하지 않고 일반화
        return buildProblemDetail(
            HttpStatus.CONFLICT,
            "DUPLICATE_RESOURCE",
            "이미 존재하는 리소스입니다. 또는 데이터 무결성 제약조건에 위반됩니다.",
            request
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(
        e: HandlerMethodValidationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val errorMessages = e.parameterValidationResults.flatMap { result ->
            val paramName = result.methodParameter.parameterName ?: "unknown"

            result.resolvableErrors.map { error ->
                val message = error.defaultMessage ?: "유효성 검사 오류"
                "$paramName: $message"
            }
        }

        val detail = if (errorMessages.isEmpty()) {
            "입력값 검증에 실패했습니다."
        } else {
            "입력값 검증에 실패했습니다. (${errorMessages.joinToString(", ")})"
        }

        return buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            detail,
            request
        )
    }

    // 🔹 중복 코드 제거용 공통 응답 생성 메서드
    private fun buildProblemDetail(
        status: HttpStatus,
        errorCode: String,
        detail: String,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = status.reasonPhrase
            type = URI.create("https://api.example.com/errors/$errorCode")
            setProperty("errorCode", errorCode)
            setProperty("instance", request.requestURI)
            setProperty("timestamp", LocalDateTime.now())
        }

        return ResponseEntity.status(status).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        e: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다. 관리자에게 문의하세요."
        ).apply {
            title = "Internal Server Error"
            type = URI.create("https://api.example.com/errors/INTERNAL_ERROR")
            setProperty("errorCode", "INTERNAL_ERROR")
            setProperty("timestamp", LocalDateTime.now())
            setProperty("instance", request.requestURI)

            request.getAttribute("traceId")?.let {
                setProperty("traceId", it)
            }
        }

        return ResponseEntity.internalServerError().body(problemDetail)
    }

    @ExceptionHandler(ExhaustedRetryException::class)
    fun handleRetryExhaustedException(
        e: ExhaustedRetryException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        var rootCause: Throwable? = e

        while (rootCause != null) {
            if (rootCause is BusinessException) {
                val problemDetail = ProblemDetail.forStatusAndDetail(
                    rootCause.status,
                    rootCause.message
                ).apply {
                    title = rootCause.status.reasonPhrase
                    type = URI.create("https://api.example.com/errors/${rootCause.errorCode.code}")
                    setProperty("errorCode", rootCause.errorCode.code)
                    setProperty("timestamp", LocalDateTime.now())
                    setProperty("instance", request.requestURI)

                    request.getAttribute("traceId")?.let {
                        setProperty("traceId", it)
                    }
                }

                return ResponseEntity.status(rootCause.status).body(problemDetail)
            }

            rootCause = rootCause.cause
        }

        return handleUnexpectedException(e, request)
    }
}
