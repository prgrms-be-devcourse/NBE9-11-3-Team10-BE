package com.team10.backend.domain.image.service

import com.team10.backend.domain.image.dto.PresignedUrlRequest
import com.team10.backend.domain.image.dto.PresignedUrlResponse
import com.team10.backend.global.exception.BusinessException
import com.team10.backend.global.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*

//import lombok.extern.slf4j.Slf4j;
@Service //@Slf4j
class ImageUploadService(private val s3Client: S3Client, private val s3Presigner: S3Presigner) {
    @Value("\${cloud.aws.s3.bucket}")
    private lateinit var bucket: String

    @Value("\${cloud.aws.region}")
    private lateinit var region: String

    fun createPresignedUrl(request: PresignedUrlRequest): PresignedUrlResponse {
        validatePresignedUrlRequest(request)

        val key = createObjectKey(request.fileName, request.directory)
        val bucketName = bucket.trim()

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(request.contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGNED_URL_DURATION)
            .putObjectRequest(putObjectRequest)
            .build()

        val presignedRequest = s3Presigner.presignPutObject(presignRequest)

        return PresignedUrlResponse(
            presignedRequest.url().toString(),
            createImageUrl(key)
        )
    }

    // imageUrl에서 S3 object key를 추출해 실제 S3 파일을 삭제한다.
    fun delete(imageUrl: String?) {
        if (!StringUtils.hasText(imageUrl)) {
            return
        }

        val key = extractObjectKey(imageUrl!!)
        val bucketName = bucket.trim()

        val request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        try {
            s3Client.deleteObject(request)
        } catch (e: SdkException) {
//            log.error("S3 image delete failed. bucket={}, key={}, reason={}", bucketName, key, e.getMessage(), e);
            throw BusinessException(ErrorCode.FILE_DELETE_FAILED)
        }
    }

    // 피드/상품 수정 시 기존 이미지가 우리 S3 URL인 경우에만 삭제한다.
    fun deleteIfManaged(imageUrl: String?) {
        if (!isManagedImageUrl(imageUrl)) {
            return
        }

        delete(imageUrl)
    }


    private fun validatePresignedUrlRequest(request: PresignedUrlRequest) {
        if (!StringUtils.hasText(bucket)) {
            throw BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "S3 버킷 설정이 없습니다.")
        }
        if (!StringUtils.hasText(request.fileName)) {
            throw BusinessException(ErrorCode.INVALID_IMAGE_FILE, "파일명은 필수입니다.")
        }
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType)) {
            throw BusinessException(ErrorCode.INVALID_IMAGE_FILE)
        }
    }

    private fun createObjectKey(fileName: String, directory: String?): String {
        val safeDirectory = sanitizeDirectory(directory)
        val extension = getExtension(fileName)
        return safeDirectory + "/" + UUID.randomUUID() + extension
    }

    // directory 값에서 위험하거나 불필요한 문자를 제거해 S3 prefix로 안전하게 사용한다.
    private fun sanitizeDirectory(directory: String?): String {
        if (!StringUtils.hasText(directory)) {
            return "images"
        }
        var sanitized = directory!!.replace("[^a-zA-Z0-9/_-]".toRegex(), "")
        sanitized = sanitized.replace("^/+".toRegex(), "").replace("/+$".toRegex(), "")
        return if (StringUtils.hasText(sanitized)) sanitized else "images"
    }

    // 원본 파일명에서 확장자만 추출해 UUID 파일명 뒤에 붙인다.
    private fun getExtension(filename: String): String {
        if (!StringUtils.hasText(filename)) {
            return ""
        }
        val cleanFilename = StringUtils.cleanPath(filename)
        val extensionIndex = cleanFilename.lastIndexOf(".")
        return if (extensionIndex >= 0) cleanFilename.substring(extensionIndex).lowercase(Locale.getDefault()) else ""
    }

    // 우리 S3 버킷 URL에서만 object key를 뽑아낸다. 예: https://bucket.s3.region.amazonaws.com/feeds/a.jpg -> feeds/a.jpg
    private fun extractObjectKey(imageUrl: String): String {
        try {
            val uri = URI.create(imageUrl.trim { it <= ' ' })
            if (!isManagedImageUrl(uri)) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "우리 S3 버킷 이미지 URL만 삭제할 수 있습니다.")
            }

            val path = uri.getPath()
            if (!StringUtils.hasText(path) || "/" == path) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "삭제할 이미지 경로가 없습니다.")
            }

            return URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "이미지 URL 형식이 올바르지 않습니다.")
        }
    }

    private fun isManagedImageUrl(imageUrl: String?): Boolean {
        if (!StringUtils.hasText(imageUrl)) {
            return false
        }

        try {
            return isManagedImageUrl(URI.create(imageUrl!!.trim()))
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    private fun isManagedImageUrl(uri: URI): Boolean {
        val expectedHost = bucket.trim() + ".s3." + region.trim() + ".amazonaws.com"
        return expectedHost == uri.getHost()
    }

    // S3에 저장된 object key를 기반으로 클라이언트가 사용할 imageUrl을 만든다.
    private fun createImageUrl(key: String): String {
        return "https://" + bucket.trim() + ".s3." + region.trim() + ".amazonaws.com/" + key
    }

    companion object {
        private val PRESIGNED_URL_DURATION: Duration = Duration.ofMinutes(5)

        private val ALLOWED_CONTENT_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
        )
    }
}
