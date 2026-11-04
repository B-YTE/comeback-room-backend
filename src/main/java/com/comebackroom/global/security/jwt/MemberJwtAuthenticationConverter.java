package com.comebackroom.global.security.jwt;

import com.comebackroom.global.security.principal.MemberPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class MemberJwtAuthenticationConverter
        implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        try {
            Long memberId = Long.valueOf(jwt.getSubject());
            MemberPrincipal memberPrincipal = new MemberPrincipal(memberId);
            return UsernamePasswordAuthenticationToken.authenticated(
                    memberPrincipal,
                    null,
                    AuthorityUtils.NO_AUTHORITIES
            );
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("JWT 회원 식별자가 올바르지 않습니다.", e);
        }
    }
}
