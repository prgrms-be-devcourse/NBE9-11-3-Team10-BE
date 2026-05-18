package com.team10.backend.domain.auth.unit;

import com.team10.backend.domain.auth.dto.RefreshResult;
import com.team10.backend.domain.auth.entity.RefreshToken;
import com.team10.backend.domain.auth.repository.RefreshTokenRepository;
import com.team10.backend.domain.auth.service.RefreshTokenService;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.unit.UserTestFixture;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.ErrorCode;
import com.team10.backend.global.security.TokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("refreshToken 생성 성공")
    void createRefreshToken_success() {
        // given
        User user = UserTestFixture.createBuyer();

        RefreshToken savedToken = mock(RefreshToken.class);

        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willReturn(savedToken);

        // when
        String result = refreshTokenService.createRefreshToken(user);

        // then
        assertNotNull(result);
        assertFalse(result.isBlank());

        then(refreshTokenRepository).should(times(1)).save(any(RefreshToken.class));
    }


    @Test
    @DisplayName("토큰 재발급 성공")
    void refresh_success() {
        // given
        User user = UserTestFixture.createBuyer();
        ReflectionTestUtils.setField(user, "id", 1L);

        RefreshToken refreshToken = mock(RefreshToken.class);

        given(refreshToken.getUser()).willReturn(user);
        given(refreshToken.getRevoked()).willReturn(false);
        given(refreshToken.getExpiresAt()).willReturn(LocalDateTime.now().plusDays(1));

        given(refreshTokenRepository.findByToken("old-token"))
                .willReturn(refreshToken);

        given(tokenProvider.generateToken(user.getId(), user.getRole()))
                .willReturn("new-access-token");

        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willReturn(mock(RefreshToken.class));

        // when
        RefreshResult result = refreshTokenService.refresh("old-token");

        // then
        assertEquals("new-access-token", result.accessToken);
        assertNotNull(result.refreshToken);

        then(refreshToken).should().revoke();
        then(refreshTokenRepository).should(times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refreshToken이 null인 경우")
    void refresh_fail_null_token() {
        // when & then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.refresh(null)
        );

        assertEquals(ErrorCode.MISSING_REFRESH_TOKEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("refreshToken이 없는 값인 경우")
    void refresh_fail_invalid_token() {
        // given
        given(refreshTokenRepository.findByToken("bad-token"))
                .willReturn(null);

        // when & then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.refresh("bad-token")
        );

        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("폐기된 토큰인 경우(isRevoked = true)")
    void refresh_fail_revoked_token() {
        // given
        User user = UserTestFixture.createBuyer();

        RefreshToken refreshToken = RefreshToken.create("token", user);
        refreshToken.revoke();

        given(refreshTokenRepository.findByToken("token"))
                .willReturn(refreshToken);

        // when & then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.refresh("token")
        );

        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("만료기한이 지난 경우")
    void refresh_fail_expired_token() {
        // given
        RefreshToken refreshToken = mock(RefreshToken.class);

        given(refreshToken.getRevoked()).willReturn(false);
        given(refreshToken.getExpiresAt()).willReturn(LocalDateTime.now().minusDays(1));

        given(refreshTokenRepository.findByToken("token"))
                .willReturn(refreshToken);

        // when & then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.refresh("token")
        );

        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getErrorCode());
    }

}



