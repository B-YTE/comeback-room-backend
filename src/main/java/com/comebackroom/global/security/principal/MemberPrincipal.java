package com.comebackroom.global.security.principal;

import java.security.Principal;

public record MemberPrincipal(Long memberId) implements Principal {

    public MemberPrincipal {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId 값은 1 이상이어야 합니다.");
        }
    }

    @Override
    public String getName() {
        return memberId.toString();
    }
}
