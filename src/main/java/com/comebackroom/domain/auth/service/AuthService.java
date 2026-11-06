package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.dto.LoginTokenResult;
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
    public LoginTokenResult login(LoginRequest request) {
        Member member = getAuthenticatedLocalMember(request);

        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = refreshTokenGenerator.generateToken();
        String refreshTokenHash = refreshTokenHasher.hashToken(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                clock.instant().plus(refreshTokenProperties.expiration()),
                APPLICATION_ZONE_ID
        );

        refreshTokenRepository.save(RefreshToken.create(
                member,
                refreshTokenHash,
                expiresAt
        ));

        log.info("자체 로그인 성공 memberId={}", member.getId());
        return new LoginTokenResult(accessToken, refreshToken);
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
}
