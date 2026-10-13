package com.comebackroom.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// 모든 요청에 요청별 requestId를 붙이고, 요청이 끝날 때 한 줄 요약 로그를 남깁니다
// 요청이 들어오면 requestId를 만들어 MDC에 넣고, 해당 요청에서 찍히는 모든 로그에 [requestId]가 자동으로 붙습니다
// 요청이 끝나면 "GET /path 200 35ms" 형태의 요약 한 줄을 남깁니다
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 가장 먼저 실행
public class MdcLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    // 헬스 체크는 로그에서 제외
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("{} {} {} {}ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed);
            MDC.clear();
        }
    }
}
