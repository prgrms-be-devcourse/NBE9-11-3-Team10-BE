package com.team10.backend.global.idempotency

import java.lang.annotation.Inherited

@Target(
    AnnotationTarget.FUNCTION,                    // Kotlin 용
    AnnotationTarget.ANNOTATION_CLASS             // 메타 어노테이션 용
)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class Idempotent(
    val lockTtlSec: Int = 15,          // 처리 중 락 유지 시간 (초)
    val cacheTtlSec: Int = 86400       // 완료 후 응답 캐시 시간 (초, 기본 24h)
)