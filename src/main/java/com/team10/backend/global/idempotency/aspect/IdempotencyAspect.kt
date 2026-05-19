package com.team10.backend.global.idempotency.aspect

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.team10.backend.global.exception.ErrorCode
import com.team10.backend.global.idempotency.IdempotencyStatus
import com.team10.backend.global.idempotency.IdempotencyStore
import com.team10.backend.global.idempotency.Idempotent
import com.team10.backend.global.idempotency.exception.IdempotencyException
import com.team10.backend.global.idempotency.interceptor.IdempotencyKeyInterceptor
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // 다른 AOP보다 먼저 실행
class IdempotencyAspect(
    private val store: IdempotencyStore
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper =
        ObjectMapper().registerModule(JavaTimeModule()).registerModule(KotlinModule.Builder().build())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Around("@annotation(com.team10.backend.global.idempotency.Idempotent)")
    fun handleIdempotency(joinPoint: ProceedingJoinPoint): Any {
        val key = extractIdempotencyKey()
            ?: throw IdempotencyException(
                ErrorCode.IDEMPOTENCY_KEY_MISSING,
                "Missing 'Idempotency-Key' header"
            )

        val methodSignature = joinPoint.signature as MethodSignature
        val annotation = methodSignature.method.getAnnotation(Idempotent::class.java)

        return when (val status = store.checkAndLock(key, annotation.lockTtlSec)) {
            IdempotencyStatus.COMPLETED -> {
                val cachedJson = store.getResponse(key)
                    ?: throw IdempotencyException(
                        ErrorCode.IDEMPOTENCY_CACHE_MISS,
                        "Cache miss for completed key: $key"
                    )
                objectMapper.readValue(cachedJson, methodSignature.returnType)
            }

            IdempotencyStatus.LOCKED -> {
                throw IdempotencyException(
                    ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                    "Request already in progress. Retry after Lock TTL."
                )
            }

            IdempotencyStatus.NONE -> {
                executeAndCache(key, annotation, joinPoint, methodSignature)
            }
        }
    }

    private fun executeAndCache(
        key: String,
        annotation: Idempotent,
        joinPoint: ProceedingJoinPoint,
        methodSignature: MethodSignature
    ): Any {
        return try {
            val result = joinPoint.proceed()

            // 응답 직렬화 및 저장 (경쟁 상태일 수 있으므로 반환값 체크)
            val cachedJson = objectMapper.writeValueAsString(result)
            val success = store.complete(key, cachedJson, annotation.cacheTtlSec)
            if (!success) {
                log.warn("Idempotency cache update failed for key: $key. Possible race condition.")
            }
            result
        } catch (e: Throwable) {
            // 비즈니스 로직 실패 시 LOCK 상태 즉시 해제 (다음 요청이 정상 진입 가능)
            store.release(key)
            throw e
        }
    }

    private fun extractIdempotencyKey(): String? {
        val attrs = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
            ?: return null
        // Interceptor 가 설정한 속성만 읽음 (헤더 직접 읽기 불필요)
        return attrs.request.getAttribute(IdempotencyKeyInterceptor.REQUEST_ATTRIBUTE_KEY) as? String
    }
}