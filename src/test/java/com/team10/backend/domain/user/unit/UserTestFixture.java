package com.team10.backend.domain.user.unit;

import com.team10.backend.domain.user.entity.SellerInfo;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.enums.Role;
import com.team10.backend.domain.user.enums.UserStatus;

import java.util.UUID;

public class UserTestFixture {
    public static User createBuyer() {
        return new User(
                null,
                "buyer" + UUID.randomUUID() + "@test.com",
                "password123!",
                "김구매",
                "buyer",
                "010-0000-0000",
                "서울시 동대문구",
                UserStatus.ACTIVE,
                Role.BUYER,
                null
        );
    }

    public static User createSeller() {
        User user = new User(
                null,
                "seller" + UUID.randomUUID() + "@test.com",
                "password123!",
                "송판매",
                "seller",
                "010-1111-1111",
                "서울시 동대문구",
                UserStatus.ACTIVE,
                Role.SELLER,
                null
                );
        SellerInfo sellerInfo = new SellerInfo();
        sellerInfo.updateSellerInfo("안녕하세요", "123-123-12345");
        user.attachSellerInfo(sellerInfo);

        return user;
    }

}
