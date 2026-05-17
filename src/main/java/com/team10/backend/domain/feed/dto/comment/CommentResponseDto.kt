package com.team10.backend.domain.feed.dto.comment

import com.team10.backend.domain.feed.entity.FeedComment
import com.team10.backend.domain.user.entity.User

data class CommentResponseDto(
    @JvmField val commentId: Long,
    @JvmField val writer: Writer,
    @JvmField val content: String,
    val likeCount: Int,
    @JvmField val isLiked: Boolean,
    @JvmField val isMine: Boolean,
    val createdAt: String,
    val updatedAt: String
) {
    data class Writer(
        @JvmField val userId: Long,
        val nickname: String,
        val profileImageUrl: String?
    ) {
        companion object {
            fun from(writer: User): Writer {
                return Writer(
                    writer.getId(),
                    writer.nickname,
                    writer.imageUrl
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun from(comment: FeedComment, isLiked: Boolean, currentUser: User?): CommentResponseDto {
            val isMine = currentUser != null && comment.writer.id == currentUser.id

            return CommentResponseDto(
                comment.getId(),
                Writer.from(comment.writer),
                comment.content,
                comment.likeCount,
                isLiked,
                isMine,
                comment.getCreatedAt().toString(),
                comment.getUpdatedAt().toString()
            )
        }
    }
}
