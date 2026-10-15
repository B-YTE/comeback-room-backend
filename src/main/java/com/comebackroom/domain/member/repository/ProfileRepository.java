package com.comebackroom.domain.member.repository;

import com.comebackroom.domain.member.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
}
