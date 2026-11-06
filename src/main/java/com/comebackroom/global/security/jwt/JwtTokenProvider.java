package com.comebackroom.global.security.jwt;

import com.comebackroom.global.security.principal.MemberPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class JwtTokenProvider {

    static final String TOKEN_TYPE_CLAIM = "type";
    static final String ACCESS_TOKEN_TYPE = "ACCESS";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock jwtClock;

    public JwtTokenProvider(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock jwtClock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.jwtClock = jwtClock;
    }

    public String createAccessToken(Long memberId) {
        MemberPrincipal memberPrincipal = new MemberPrincipal(memberId);
        Instant issuedAt = jwtClock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenExpiration());

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(memberPrincipal.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
