package com.comebackroom.global.websocket.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketTicketStore {

    private static final String KEY_PREFIX = "ws-ticket:";
    public static final Duration TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;

    public String issue(Long memberId, Long roomId, String tabId) {
        Objects.requireNonNull(memberId, "memberId는 null일 수 없습니다.");
        Objects.requireNonNull(roomId, "roomId는 null일 수 없습니다.");
        Objects.requireNonNull(tabId, "tabId는 null일 수 없습니다.");

        String ticket = generateTicket();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + ticket,
                memberId + ":" + roomId + ":" + tabId,
                TTL
        );
        return ticket;
    }

    public Optional<TicketInfo> consume(String ticket) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 3);
        return Optional.of(
                new TicketInfo(Long.valueOf(parts[0]), Long.valueOf(parts[1]), parts[2])
        );
    }

    private String generateTicket() {
        return UUID.randomUUID().toString();
    }
}
