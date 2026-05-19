package com.team10.backend.domain.image.service

import com.team10.backend.domain.image.dto.PresignedUrlRequest
import com.team10.backend.global.exception.BusinessException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI

internal class ImageUploadServiceTest {
    private lateinit var s3Client: S3Client
    private lateinit var s3Presigner: S3Presigner
    private lateinit var imageUploadService: ImageUploadService

    @BeforeEach
    fun setUp() {
        s3Client = Mockito.mock(S3Client::class.java)
        s3Presigner = Mockito.mock(S3Presigner::class.java)
        imageUploadService = ImageUploadService(s3Client, s3Presigner)
        ReflectionTestUtils.setField(imageUploadService, "bucket", "team10-images-dev-test")
        ReflectionTestUtils.setField(imageUploadService, "region", "ap-northeast-2")
    }

    @Test
    @Throws(Exception::class)
    fun createPresignedUrlReturnsUploadUrlAndImageUrl() {
        val presignedRequest = Mockito.mock(PresignedPutObjectRequest::class.java)
        Mockito.`when`(presignedRequest.url())
            .thenReturn(URI.create("https://presigned-upload.test/upload").toURL())
        Mockito.`when`(
            s3Presigner.presignPutObject(
                ArgumentMatchers.any(
                    PutObjectPresignRequest::class.java
                )
            )
        ).thenReturn(presignedRequest)

        val response = imageUploadService.createPresignedUrl(
            PresignedUrlRequest("cake.jpg", "image/jpeg", "products")
        )

        Assertions.assertThat(response.uploadUrl).isEqualTo("https://presigned-upload.test/upload")
        Assertions.assertThat(response.imageUrl)
            .startsWith("https://team10-images-dev-test.s3.ap-northeast-2.amazonaws.com/products/")
            .endsWith(".jpg")
        Mockito.verify(s3Presigner)
            .presignPutObject(ArgumentMatchers.any(PutObjectPresignRequest::class.java))
    }

    @Test
    fun createPresignedUrlRejectsNonImageContentType() {
        val request = PresignedUrlRequest("memo.txt", "text/plain", "products")

        Assertions.assertThatThrownBy { imageUploadService.createPresignedUrl(request) }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun deleteImageDeletesS3Object() {
        val imageUrl = "https://team10-images-dev-test.s3.ap-northeast-2.amazonaws.com/feeds/test-image.jpg"

        imageUploadService.delete(imageUrl)

        Mockito.verify(s3Client)
            .deleteObject(ArgumentMatchers.any(DeleteObjectRequest::class.java))
    }

    @Test
    fun deleteRejectsExternalImageUrl() {
        val imageUrl = "https://other-bucket.s3.ap-northeast-2.amazonaws.com/feeds/test-image.jpg"

        Assertions.assertThatThrownBy { imageUploadService.delete(imageUrl) }
            .isInstanceOf(BusinessException::class.java)
        Mockito.verify(s3Client, Mockito.never()).deleteObject(
            ArgumentMatchers.any(
                DeleteObjectRequest::class.java
            )
        )
    }

    @Test
    fun deleteIfManagedSkipsExternalImageUrl() {
        val imageUrl = "https://other-bucket.s3.ap-northeast-2.amazonaws.com/feeds/test-image.jpg"

        imageUploadService.deleteIfManaged(imageUrl)

        Mockito.verify(s3Client, Mockito.never()).deleteObject(
            ArgumentMatchers.any(
                DeleteObjectRequest::class.java
            )
        )
    }
}
