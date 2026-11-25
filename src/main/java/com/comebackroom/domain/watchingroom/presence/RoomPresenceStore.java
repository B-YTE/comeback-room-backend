package com.comebackroom.domain.watchingroom.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RoomPresenceStore {
    // 마지막 확인 후 이 시간이 지나면 끊긴 것으로 봄
    public static final Duration LEASE = Duration.ofSeconds(30);
    // 키 수명 (갱신되거나 정리되지 않고 남아있는 키 정리)
    private static final Duration KEY_TTL = LEASE.multipliedBy(2);

    private static final String MEMBERS_KEY_FORMAT = "presence:room:%d:members";
    private static final String SESSIONS_KEY_FORMAT = "presence:room:%d:member:%d";
    private static final String RESERVED_KEY_FORMAT = "presence:room:%d:reserved";

    // 탭 연결 스크립트 - 회원이 방에 새로 입장 시 1 반환 (이미 다른 탭으로 입장해 있었을 경우 0)
    // KEYS[1]: 이 회원의 탭 ZSET / KEYS[2]: 방의 회원 ZSET / KEYS[3]: 방의 예약 ZSET
    // ARGV: [1]=지금(ms) [2]=기준시각 (지금 - LEASE) [3]=tabId [4]=memberId [5]=키 수명(ms)
    private static final RedisScript<Long> CONNECT_SCRIPT = RedisScript.of("""
            -- 회원의 탭 중 점수가 기준 시각 미만인 것을 삭제 (죽은 탭 청소)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. ARGV[2])
            -- 방의 회원 중 죽은 회원도 청소
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', '(' .. ARGV[2])
            -- 탭을 기록하거나 점수(현재 접속한 순간의 시간)를 갱신
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3])
            -- joined = 새로 들어온 회원이면 1, 이미 있으면 0
            local joined = redis.call('ZADD', KEYS[2], ARGV[1], ARGV[4])
            -- 이 회원의 예약 삭제 (예약이 없는 경우 아무 일도 하지 않음)
            redis.call('ZREM', KEYS[3], ARGV[4])
            -- 두 키의 수명을 갱신 (60초)
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            redis.call('PEXPIRE', KEYS[2], ARGV[5])
            return joined
            """, Long.class);

    // 하트비트 스크립트 - 갱신 시 1, 이미 정리된(존재하지 않는) 탭일 시 0 반환
    // KEYS[1]: 이 회원의 탭 ZSET / KEYS[2]: 방의 회원 ZSET
    // ARGV: [1]=지금(ms) [2]=기준시각 (지금 - LEASE) [3]=tabId [4]=memberId [5]=키 수명(ms)
    private static final RedisScript<Long> HEARTBEAT_SCRIPT = RedisScript.of("""
            -- 회원의 탭 중 점수가 기준 시각 미만인 것을 삭제 (죽은 탭 청소)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. ARGV[2])
            -- 탭이 없으면(이미 정리되었으면) 되살리지 않고 0 반환
            if not redis.call('ZSCORE', KEYS[1], ARGV[3]) then return 0 end
            -- 회원의 탭의 점수(마지막 확인 시각) 갱신
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3])
            -- 회원의 방 점수 갱신 (방 점수 = 같은 방에 대한 해당 회원의 탭들 중 가장 최근 확인 시각)
            redis.call('ZADD', KEYS[2], ARGV[1], ARGV[4])
            -- 두 키의 수명 갱신
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            redis.call('PEXPIRE', KEYS[2], ARGV[5])
            return 1
            """, Long.class);

    // 탭 해제 스크립트 - 회원이 방에서 완전히 나간 경우 1
    // (다른 탭이 남아있어 여전히 방 접속 상태거나 존재하지 않는 탭인 경우 0) 반환
    // KEYS[1]: 이 회원의 탭 ZSET / KEYS[2]: 방의 회원 ZSET
    // ARGV: [1]=기준시각 [2]=tabId [3]=memberId
    private static final RedisScript<Long> DISCONNECT_SCRIPT = RedisScript.of("""
            -- 회원의 탭 중 점수가 기준 시각 미만인 것을 삭제 (죽은 탭 청소)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. ARGV[1])
            -- 이 탭을 제거
            redis.call('ZREM', KEYS[1], ARGV[2])
            -- 남은 탭이 하나도 없으면 방의 회원에서도 제거
            if redis.call('ZCARD', KEYS[1]) == 0 then
                return redis.call('ZREM', KEYS[2], ARGV[3])
            end
            return 0
            """, Long.class);

    // 자리 예약 스크립트 - 자리를 확보했으면 1, 정원이 가득 차 잡지 못했으면 0
    // KEYS[1]: 방의 회원 ZSET / KEYS[2]: 방의 예약 ZSET
    // ARGV: [1]=지금(ms) [2]=기준시각(지금 - LEASE) [3]=memberId [4]=정원
    //       [5]=예약 만료 시각(ms) [6]=키 수명(ms)
    private static final RedisScript<Long> RESERVE_SCRIPT = RedisScript.of("""
            -- 죽은 접속(기준 시각 미만)과 만료된 예약(만료 시각 <= 지금) 청소
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
            -- 이미 접속 중인 회원이면 이미 자리 차지 중이므로 아무것도 하지 않고 통과
            if redis.call('ZSCORE', KEYS[1], ARGV[3]) then return 1 end
            -- 이미 예약 중인 회원이면 만료만 연장
            if redis.call('ZSCORE', KEYS[2], ARGV[3]) then
                redis.call('ZADD', KEYS[2], ARGV[5], ARGV[3])
                redis.call('PEXPIRE', KEYS[2], ARGV[6])
                return 1
            end
            -- 새 사람 : 접속 중인 회원 수 + 예약 중인 회원 수가 정원 이상이면 가득 참
            local taken = redis.call('ZCARD', KEYS[1]) + redis.call('ZCARD', KEYS[2])
            if taken >= tonumber(ARGV[4]) then return 0 end
            -- 자리가 있으면 예약 기록
            redis.call('ZADD', KEYS[2], ARGV[5], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[6])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;


    // 탭 연결 - 회원이 방에 새로 입장했으면 true, 이미 다른 탭으로 입장해 있었으면 false
    public boolean connect(Long roomId, Long memberId, String tabId) {
        Objects.requireNonNull(tabId, "tabId는 null일 수 없습니다.");
        long now = clock.millis();
        String sessionsKey = createSessionsKey(roomId, memberId);
        String membersKey = createMembersKey(roomId);
        String reservedKey = createReservedKey(roomId);

        Long result = redisTemplate.execute(
                CONNECT_SCRIPT,
                // KEYS[1]: 이 회원의 탭 ZSET, KEYS[2]:방의 회원 ZSET, KEYS[3]: 방의 예약 ZSET
                List.of(sessionsKey, membersKey, reservedKey),
                String.valueOf(now),                    // ARGV[1]: 지금(ms)
                String.valueOf(now - LEASE.toMillis()), // ARGV[2]: 기준시각 (지금 - LEASE)
                tabId,                              // ARGV[3]: tabId
                String.valueOf(memberId),               // ARGV[4]: memberId
                String.valueOf(KEY_TTL.toMillis())      // ARGV[5]: 키 수명(ms)
        );
        return toBoolean(result);
    }
    

    // 하트비트 - 갱신했으면 true, 이미 정리된(모르는) 탭이면 false
    public boolean heartbeat(Long roomId, Long memberId, String tabId) {
        Objects.requireNonNull(tabId, "tabId는 null일 수 없습니다.");
        long now = clock.millis();
        String sessionsKey = createSessionsKey(roomId, memberId);
        String membersKey = createMembersKey(roomId);

        Long result = redisTemplate.execute(
                HEARTBEAT_SCRIPT,
                List.of(sessionsKey, membersKey),       // KEYS[1]: 이 회원의 탭 ZSET, KEYS[2]:방의 회원 ZSET
                String.valueOf(now),                    // ARGV[1]: 지금(ms)
                String.valueOf(now - LEASE.toMillis()), // ARGV[2]: 기준시각 (지금 - LEASE)
                tabId,                              // ARGV[3]: tabId
                String.valueOf(memberId),               // ARGV[4]: memberId
                String.valueOf(KEY_TTL.toMillis())      // ARGV[5]: 키 수명(ms)
        );
        return toBoolean(result);
    }

    // 탭 해제 - 회원이 방에서 완전히 나갔으면 true, 다른 탭이 남아있거나 모르는 탭이면 false
    public boolean disconnect(Long roomId, Long memberId, String tabId) {
        Objects.requireNonNull(tabId, "tabId는 null일 수 없습니다.");
        long cutoff = clock.millis() - LEASE.toMillis();
        String sessionsKey = createSessionsKey(roomId, memberId);
        String membersKey = createMembersKey(roomId);

        Long result = redisTemplate.execute(
                DISCONNECT_SCRIPT,
                List.of(sessionsKey, membersKey),       // KEYS[1]: 이 회원의 탭 ZSET, KEYS[2]:방의 회원 ZSET
                String.valueOf(cutoff),                 // ARGV[1]: 기준시각
                tabId,                              // ARGV[2]: tabId
                String.valueOf(memberId)                // ARGV[3]: memberId
        );
        return toBoolean(result);
    }

    // 방 인원 수
    public long countMembers(Long roomId) {
        Long count = redisTemplate.opsForZSet()
                .count(createMembersKey(roomId), calculateCutoff(), Double.POSITIVE_INFINITY);
        return count == null ? 0L : count;
    }

    // 방에 접속 중인 회원 id 목록
    public List<Long> findMemberIds(Long roomId) {
        Set<String> memberIds = redisTemplate.opsForZSet()
                .rangeByScore(createMembersKey(roomId), calculateCutoff(), Double.POSITIVE_INFINITY);
        if (memberIds == null) return List.of();
        return memberIds.stream().map(Long::valueOf).toList();
    }

    // 자리 예약 - 통과(이미 접속 중, 이미 예약 중, 새로 예약)하면 true, 정원이 가득 차 실패하면 false
    public boolean reserve(Long roomId, Long memberId, int maxParticipants, Duration hold) {
        Objects.requireNonNull(memberId, "memberId는 null일 수 없습니다.");
        Objects.requireNonNull(hold, "hold는 null일 수 없습니다.");
        if (hold.compareTo(KEY_TTL) > 0) {
            throw new IllegalArgumentException("hold는 키 수명보다 길 수 없습니다.");
        }
        long now = clock.millis();

        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(createMembersKey(roomId), createReservedKey(roomId)), // KEYS[1]: 방의 회원 ZSET, KEYS[2]: 방의 예약 ZSET
                String.valueOf(now),                        // ARGV[1]: 지금(ms)
                String.valueOf(now - LEASE.toMillis()),  // ARGV[2]: 기준시각 (지금 - LEASE)
                String.valueOf(memberId),                  // ARGV[3]: memberId
                String.valueOf(maxParticipants),           // ARGV[4]: 정원
                String.valueOf(now + hold.toMillis()),  // ARGV[5]: 예약 만료 시각
                String.valueOf(KEY_TTL.toMillis())        // ARGV[6]: 키 수명(ms)
        );
        return toBoolean(result);
    }



    // 방의 예약 ZSET 키 (presence:room:1:reserved)
    private static String createReservedKey(Long roomId) {
        Objects.requireNonNull(roomId, "roomId는 null일 수 없습니다.");
        return RESERVED_KEY_FORMAT.formatted(roomId);
    }

    // 방의 회원 ZSET 키 (presence:room:1:members)
    private static String createMembersKey(Long roomId) {
        Objects.requireNonNull(roomId, "roomId는 null일 수 없습니다.");
        return MEMBERS_KEY_FORMAT.formatted(roomId);
    }

    // 한 회원의 탭 ZSET 키 (예: presence:room:1:member:2)
    private static String createSessionsKey(Long roomId, Long memberId) {
        Objects.requireNonNull(roomId, "roomId는 null일 수 없습니다.");
        Objects.requireNonNull(memberId, "memberId는 null일 수 없습니다.");
        return SESSIONS_KEY_FORMAT.formatted(roomId, memberId);
    }

    private boolean toBoolean(Long result) {
        return result != null && result == 1L;
    }

    private double calculateCutoff() {
        // 이 시각보다 오래된 연결은 죽은 항목으로 봄
        return clock.millis() - LEASE.toMillis();
    }
}
