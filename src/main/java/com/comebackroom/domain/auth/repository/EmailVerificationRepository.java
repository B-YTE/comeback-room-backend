package com.comebackroom.domain.auth.repository;

import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmailAndPurpose(
            String email,
            EmailVerificationPurpose purpose
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findWithLockByEmailAndPurpose(
            String email,
            EmailVerificationPurpose purpose
    );

    Optional<EmailVerification> findByVerificationTokenHash(String verificationTokenHash);
}
