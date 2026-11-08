package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.dto.IssuedTokenResult;
import com.comebackroom.domain.auth.dto.request.LoginRequest;
import com.comebackroom.domain.auth.entity.RefreshToken;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.RefreshTokenRepository;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.enums.Provider;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.security.jwt.JwtTokenProvider;
import com.comebackroom.global.security.refresh.RefreshTokenGenerator;
import com.comebackroom.global.security.refresh.RefreshTokenHasher;
import com.comebackroom.global.security.refresh.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final ZoneId APPLICATION_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenProperties refreshTokenProperties;
    private final Clock clock;

    @Transactional
    public IssuedTokenResult login(LoginRequest request) {
        Member member = getAuthenticatedLocalMember(request);
        IssuedTokenResult issuedTokenResult = issueTokens(member);

        log.info("자체 로그인 성공 memberId={}", member.getId());
        return issuedTokenResult;
    }

    @Transactional
    public IssuedTokenResult refresh(String rawRefreshToken) {
        RefreshToken refreshToken = getValidRefreshToken(rawRefreshToken);
        Member member = refreshToken.getMember();

        refreshTokenRepository.delete(refreshToken);
        refreshTokenRepository.flush();

        IssuedTokenResult issuedTokenResult = issueTokens(member);
        log.info("토큰 재발급 성공 memberId={}", member.getId());
        return issuedTokenResult;
    }

    private Member getAuthenticatedLocalMember(LoginRequest request) {
        if (request == null
                || !StringUtils.hasText(request.loginId())
                || !StringUtils.hasText(request.password())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (member.getProvider() != Provider.LOCAL
                || !StringUtils.hasText(member.getPassword())
                || !passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return member;
    }

    private RefreshToken getValidRefreshToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String refreshTokenHash = refreshTokenHasher.hashToken(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository
                .findWithLockByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!refreshToken.getExpiresAt().isAfter(currentDateTime())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return refreshToken;
    }

    private IssuedTokenResult issueTokens(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = refreshTokenGenerator.generateToken();
        String refreshTokenHash = refreshTokenHasher.hashToken(refreshToken);
        LocalDateTime expiresAt = currentDateTime().plus(refreshTokenProperties.expiration());

        refreshTokenRepository.save(RefreshToken.issueFor(
                member,
                refreshTokenHash,
                expiresAt
        ));

        return new IssuedTokenResult(accessToken, refreshToken);
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(clock.instant(), APPLICATION_ZONE_ID);
    }
}
