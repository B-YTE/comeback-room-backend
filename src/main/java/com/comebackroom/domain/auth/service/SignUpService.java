package com.comebackroom.domain.auth.service;

import com.comebackroom.domain.auth.dto.request.SignUpRequest;
import com.comebackroom.domain.auth.dto.response.SignUpResponse;
import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import com.comebackroom.domain.auth.exception.AuthErrorCode;
import com.comebackroom.domain.auth.repository.EmailVerificationRepository;
import com.comebackroom.domain.member.entity.Member;
import com.comebackroom.domain.member.entity.Profile;
import com.comebackroom.domain.member.repository.MemberRepository;
import com.comebackroom.domain.member.repository.ProfileRepository;
import com.comebackroom.global.exception.BusinessException;
import com.comebackroom.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SignUpService {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("[a-z0-9_]{4,20}");
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("[가-힣a-zA-Z0-9]{2,10}");
    private static final Pattern ENGLISH_LETTER = Pattern.compile("[a-zA-Z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final Set<String> MEMBER_UNIQUE_CONSTRAINTS = Set.of(
            "uk_members_login_id", "uk_members_email", "uk_profiles_nickname"
    );

    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationHasher hasher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        if (!matches(LOGIN_ID_PATTERN, loginId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return !memberRepository.existsByLoginId(loginId);
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        if (!matches(NICKNAME_PATTERN, nickname)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return !profileRepository.existsByNickname(nickname);
    }

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        validateSignUp(request);
        String email = EmailNormalizer.normalize(request.email());
        EmailVerification verification = emailVerificationRepository
                .findWithLockByEmailAndPurpose(email, EmailVerificationPurpose.SIGN_UP)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE));
        LocalDateTime consumedAt = LocalDateTime.now(clock);
        validateVerificationToken(verification, request.emailVerificationToken(), consumedAt);

        if (memberRepository.existsByLoginId(request.loginId())
                || memberRepository.existsByEmailIgnoreCase(email)
                || profileRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_MEMBER);
        }

        Member member = Member.createLocalMember(
                request.loginId(), passwordEncoder.encode(request.password()), email
        );
        try {
            memberRepository.save(member);
            profileRepository.saveAndFlush(Profile.builder()
                    .member(member)
                    .nickname(request.nickname())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && MEMBER_UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                throw new BusinessException(AuthErrorCode.DUPLICATE_MEMBER);
            }
            throw exception;
        }

        verification.consumeToken(consumedAt);
        return new SignUpResponse(member.getId(), member.getEmail(), request.nickname());
    }

    private void validateVerificationToken(
            EmailVerification verification, String token, LocalDateTime consumedAt
    ) {
        String tokenHash = hasher.hashToken(verification.getEmail(), verification.getPurpose(), token);
        if (!verification.canConsumeToken(consumedAt)
                || !hasher.matches(verification.getVerificationTokenHash(), tokenHash)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void validateSignUp(SignUpRequest request) {
        String password = request.password();
        if (!matches(LOGIN_ID_PATTERN, request.loginId())
                || !matches(NICKNAME_PATTERN, request.nickname())
                || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES
                || !ENGLISH_LETTER.matcher(password).find()
                || !DIGIT.matcher(password).find()
                || !password.equals(request.passwordConfirm())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }
}
