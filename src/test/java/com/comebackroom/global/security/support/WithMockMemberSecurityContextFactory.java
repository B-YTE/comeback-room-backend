package com.comebackroom.global.security.support;

import com.comebackroom.global.security.principal.MemberPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

public class WithMockMemberSecurityContextFactory
        implements WithSecurityContextFactory<WithMockMember> {

    @Override
    public SecurityContext createSecurityContext(WithMockMember annotation) {
        MemberPrincipal memberPrincipal = new MemberPrincipal(annotation.memberId());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                memberPrincipal,
                null,
                List.of()
        );

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        return securityContext;
    }
}
