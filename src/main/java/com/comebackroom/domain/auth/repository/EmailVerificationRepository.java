package com.comebackroom.domain.auth.repository;

import com.comebackroom.domain.auth.entity.EmailVerification;
import com.comebackroom.domain.auth.enums.EmailVerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    @Query(value = """
            SELECT pg_advisory_xact_lock(
                hashtextextended(CONCAT(:email, ':', :purpose), 0)
            )
            """, nativeQuery = true)
    void acquireTransactionLock(
            @Param("email") String email,
            @Param("purpose") String purpose
    );

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
