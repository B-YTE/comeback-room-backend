package com.comebackroom.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketTicketStore {

    private static final String KEY_PREFIX = "ws-ticket:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public record TicketInfo(Long memberId, Long roomId) {}

    public String issue(Long memberId, Long roomId) {
        String ticket = generateTicket();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + ticket, memberId + ":" + roomId, TTL
        );
        return ticket;
    }

    public Optional<TicketInfo> consume(String ticket) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":");
        return Optional.of(new TicketInfo(Long.valueOf(parts[0]), Long.valueOf(parts[1])));
    }

    private String generateTicket() {
        return UUID.randomUUID().toString();
    }
}
