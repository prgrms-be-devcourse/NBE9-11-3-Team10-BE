package com.team10.backend.global.idempotency.aspect

import com.fasterxml.jackson.databind.DeserializationFeature
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
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.ParameterizedType

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // 다른 AOP보다 먼저 실행
class IdempotencyAspect(
    private val store: IdempotencyStore
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper =
        ObjectMapper().registerModule(JavaTimeModule())  // ✅ Java 8 시간 API 지원
            .registerModule(KotlinModule.Builder().build())  // ✅ Kotlin 데이터 클래스 지원
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)  // ✅ ISO-8601 문자열 형식 유지
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)  // ✅ 유연한 역직렬화

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
                    ?: throw IdempotencyException(ErrorCode.IDEMPOTENCY_CACHE_MISS, "Cache miss for key: $key")

                val javaType = methodSignature.method.genericReturnType?.let { genericType ->
                    when (genericType) {
                        is ParameterizedType -> {
                            val actualType = genericType.actualTypeArguments.firstOrNull()
                            actualType?.let { objectMapper.typeFactory.constructType(it) }
                                ?: objectMapper.typeFactory.constructType(genericType)
                        }
                        is Class<*> -> objectMapper.typeFactory.constructType(genericType)
                        else -> objectMapper.typeFactory.constructType(genericType)
                    }
                } ?: objectMapper.typeFactory.constructType(methodSignature.returnType)

                @Suppress("UNCHECKED_CAST")
                val responseBody = objectMapper.readValue(cachedJson, javaType) as Any?
                ResponseEntity.ok(responseBody)
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

            // ✅ ResponseEntity 에서 실제 응답 본문 추출
            val responseBody = if (result is ResponseEntity<*>) {
                result.body ?: throw IdempotencyException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Response body is null"
                )
            } else result

            // 본문만 직렬화하여 저장
            val cachedJson = objectMapper.writeValueAsString(responseBody)
            val success = store.complete(key, cachedJson, annotation.cacheTtlSec)

            if (!success) {
                log.warn("Idempotency cache update failed for key: $key")
            }

            result // 원래 ResponseEntity 그대로 반환
        } catch (e: Throwable) {
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