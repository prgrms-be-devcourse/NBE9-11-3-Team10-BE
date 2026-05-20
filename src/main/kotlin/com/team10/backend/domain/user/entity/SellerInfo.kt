package com.team10.backend.domain.user.entity

import com.team10.backend.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "seller_info")
class SellerInfo(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    var user: User? = null,

    @Column(length = 500)
    var bio: String? = null,

    @Column(name = "business_number", unique = true)
    var businessNumber: String? = null
) : BaseEntity() {

    fun linkUser(user: User) {
        this.user = user
    }

    fun updateSellerInfo(bio: String?, businessNumber: String?) {
        this.bio = bio
        this.businessNumber = businessNumber
    }
}
