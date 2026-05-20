package com.team10.backend.domain.user.entity

import com.team10.backend.domain.auth.dto.AuthRegisterRequest
import com.team10.backend.domain.user.enums.Role
import com.team10.backend.domain.user.enums.UserStatus
import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column(nullable = false, unique = true, length = 255)
    var email: String,

    @Column(nullable = false, length = 255)
    var password: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(nullable = false, unique = true, length = 255)
    var nickname: String,

    @Column(name = "phone_number", nullable = false, length = 255)
    var phoneNumber: String,

    @Column(nullable = false, length = 255)
    var address: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false)
    var userStatus: UserStatus,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role,

    @OneToOne(
    mappedBy = "user",
    orphanRemoval = true,
    cascade = [CascadeType.ALL]
    )
    var sellerInfo: SellerInfo? = null
) : BaseEntity() {

    companion object {
        fun create(
            request: AuthRegisterRequest,
            encodedPassword: String,
            role: Role) = User(
                email = request.email,
                password = encodedPassword,
                name = request.name,
                nickname = request.nickname,
                phoneNumber = request.phoneNumber,
                address = request.address,
                userStatus = UserStatus.ACTIVE,
                role = role
            )
    }

    fun attachSellerInfo(sellerInfo: SellerInfo) {
        this.sellerInfo = sellerInfo
        sellerInfo.linkUser(this)
    }

    fun updateUserInfo(
        nickname: String,
        phoneNumber: String,
        address: String
    ) {
        this.nickname = nickname
        this.phoneNumber = phoneNumber
        this.address = address
    }

    fun updateSellerProfile(
        nickname: String,
        phoneNumber: String,
        address: String,
        bio: String?,
        businessNumber: String?
    ) {
        val sellerInfo = requireNotNull(this.sellerInfo)

        updateUserInfo(nickname, phoneNumber, address)
        sellerInfo.updateSellerInfo(bio, businessNumber)
    }

    fun updateProfileImage(imageUrl: String?) {
        this.imageUrl = imageUrl
    }
}
