package com.comebackroom.global.security.principal;

import java.security.Principal;

public record MemberPrincipal(Long memberId) implements Principal {

    public MemberPrincipal {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId 값은 필수입니다.");
        }
    }

    @Override
    public String getName() {
        return memberId.toString();
    }
}
