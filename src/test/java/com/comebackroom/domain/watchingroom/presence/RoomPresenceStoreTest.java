package com.comebackroom.domain.watchingroom.presence;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class RoomPresenceStoreTest {

    private static final Instant START_TIME = Instant.parse("2026-11-01T00:00:00Z");
    private static final Long MEMBER_ID = 1L;
    private static final String TAB_A = "tab-a";
    private static final String TAB_B = "tab-b";
    private static final int REFRESH_ROUNDS = 200;
    private static final int MAX_PARTICIPANTS = 5;
    private static final Duration HOLD = Duration.ofSeconds(10);

    // 컨테이너를 공유하는 테스트끼리 겹치지 않도록 매번 새 id 발급
    private static final AtomicLong ROOM_ID_SEQUENCE = new AtomicLong();
    // 테스트마다 새 인스턴스를 만들며 방 번호 새로 생성
    private final Long roomId = ROOM_ID_SEQUENCE.incrementAndGet();

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7"))
                    .withExposedPorts(6379);
    static LettuceConnectionFactory connectionFactory;

    private final MutableClock clock = new MutableClock(START_TIME);
    private RoomPresenceStore store;

    @BeforeAll
    static void connectRedis() {
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void disconnectRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        store = new RoomPresenceStore(
                new StringRedisTemplate(connectionFactory), clock
        );
    }


    @Test
    @DisplayName("연결하면 방 인원이 1명이 되고, 해제하면 0명이 된다")
    void connectAndDisconnect_changesCountOneThenZero() {
        // when
        boolean joined = store.connect(roomId, MEMBER_ID, TAB_A);

        // then
        assertThat(joined).isTrue();
        assertThat(store.countMembers(roomId)).isEqualTo(1L);
        assertThat(store.findMemberIds(roomId)).containsExactly(MEMBER_ID);

        // when
        boolean left = store.disconnect(roomId, MEMBER_ID, TAB_A);

        // then
        assertThat(left).isTrue();
        assertThat(store.countMembers(roomId)).isZero();
    }

    @Test
    @DisplayName("같은 회원이 탭 2개로 접속해도 인원은 1명이다")
    void connectWithTwoTabs_countsMemberOnce() {
        // when
        boolean firstTabJoined = store.connect(roomId, MEMBER_ID, TAB_A);
        boolean secondTabJoined = store.connect(roomId, MEMBER_ID, TAB_B);

        // then
        assertThat(firstTabJoined).isTrue();
        assertThat(secondTabJoined).isFalse();
        assertThat(store.countMembers(roomId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("탭 하나가 나가도 다른 탭이 남아있으면 접속 중이고, 모든 탭이 나가면 인원에서 빠진다")
    void disconnectOneOfTwoTabs_keepsMemberUntilLastTab() {
        // given
        store.connect(roomId, MEMBER_ID, TAB_A);
        store.connect(roomId, MEMBER_ID, TAB_B);

        // when
        boolean leftAfterFirstTab = store.disconnect(roomId, MEMBER_ID, TAB_A);

        // then
        assertThat(leftAfterFirstTab).isFalse();
        assertThat(store.countMembers(roomId)).isEqualTo(1L);

        // when
        boolean leftAfterLastTab = store.disconnect(roomId, MEMBER_ID, TAB_B);

        // then
        assertThat(leftAfterLastTab).isTrue();
        assertThat(store.countMembers(roomId)).isZero();
    }

    @Test
    @DisplayName("같은 회원이 서로 다른 방에 접속하면 각 방에서 따로 카운트한다")
    void connectToDifferentRooms_countsEachRoomSeparately() {
        // given
        Long otherRoomId = ROOM_ID_SEQUENCE.incrementAndGet();

        // when
        store.connect(roomId, MEMBER_ID, TAB_A);
        store.connect(otherRoomId, MEMBER_ID, TAB_B);

        // then
        assertThat(store.countMembers(roomId)).isEqualTo(1L);
        assertThat(store.countMembers(otherRoomId)).isEqualTo(1L);

        // when
        store.disconnect(roomId, MEMBER_ID, TAB_A);

        // then
        assertThat(store.countMembers(roomId)).isZero();
        assertThat(store.countMembers(otherRoomId)).isEqualTo(1L);
    }

    @Test
    @Timeout(10)
    @DisplayName("같은 회원이 탭 2개로 동시에 접속하면 두 탭이 모두 기록되며 인원은 1명으로 카운트된다")
    void connectTwoTabsConcurrently_recordsBothTabs() throws Exception {
        // when
        runConcurrently(
                () -> store.connect(roomId, MEMBER_ID, TAB_A),
                () -> store.connect(roomId, MEMBER_ID, TAB_B)
        );

        // then
        assertThat(store.countMembers(roomId)).isEqualTo(1L);
        assertThat(store.disconnect(roomId, MEMBER_ID, TAB_A)).isFalse();
        assertThat(store.countMembers(roomId)).isEqualTo(1L);
    }

    @Test
    @Timeout(30)
    @DisplayName("탭을 나가는 것과 새 탭으로 들어오는 것이 동시에 일어나도 최종 인원은 1명으로 카운트된다")
    void disconnectAndConnectConcurrently_keepsMember() throws Exception {
        for (int round = 0; round < REFRESH_ROUNDS; round++) {
            // given
            Long refreshRoomId = ROOM_ID_SEQUENCE.incrementAndGet();
            store.connect(refreshRoomId, MEMBER_ID, TAB_A);

            // when
            runConcurrently(
                    () -> store.disconnect(refreshRoomId, MEMBER_ID, TAB_A),
                    () -> store.connect(refreshRoomId, MEMBER_ID, TAB_B)
            );

            // then
            assertThat(store.countMembers(refreshRoomId)).as("round %d", round)
                    .isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("하트비트 없이 접속 유효 시간(LEASE)이 지나면 인원에서 빠진다")
    void noHeartbeat_removesMemberAfterLease() {
        // given
        store.connect(roomId, MEMBER_ID, TAB_A);

        // when
        clock.advance(RoomPresenceStore.LEASE.minusSeconds(1));

        // then
        assertThat(store.countMembers(roomId)).isEqualTo(1L);

        // when
        clock.advance(Duration.ofSeconds(2));

        // then
        assertThat(store.countMembers(roomId)).isZero();
        assertThat(store.findMemberIds(roomId)).isEmpty();
    }

    @Test
    @DisplayName("하트비트를 보내는 같은 회원의 탭 A와 끊긴 탭 B가 있으면 탭 B는 정리되고 회원은 유지된다")
    void heartbeatOfOneTab_cleansUpDeadTabAndKeepsMember() {
        // given
        store.connect(roomId, MEMBER_ID, TAB_A);
        store.connect(roomId, MEMBER_ID, TAB_B);

        // when
        clock.advance(Duration.ofSeconds(20));
        boolean firstHeartbeat = store.heartbeat(roomId, MEMBER_ID, TAB_A);
        clock.advance(Duration.ofSeconds(20));
        boolean secondHeartbeat = store.heartbeat(roomId, MEMBER_ID, TAB_A);

        // then
        assertThat(firstHeartbeat).isTrue();
        assertThat(secondHeartbeat).isTrue();
        assertThat(store.countMembers(roomId)).isEqualTo(1L);
        assertThat(store.disconnect(roomId, MEMBER_ID, TAB_A)).isTrue();
        assertThat(store.countMembers(roomId)).isZero();
    }

    @Test
    @Timeout(10)
    @DisplayName("자리가 1개 남은 방에 100명이 동시에 예약하면 1명만 성공한다")
    void reserveConcurrently_grantsOnlyRemainingSeats() throws Exception {
        // given
        for (long memberId = 101; memberId <= 104; memberId++) {
            store.connect(roomId, memberId, "tab-" + memberId);
        }
        AtomicInteger granted = new AtomicInteger();
        Runnable[] tasks = LongStream.rangeClosed(1, 100)
                .<Runnable>mapToObj(memberId ->
                        () -> {
                            if (store.reserve(roomId, memberId, MAX_PARTICIPANTS, HOLD)) {
                                granted.incrementAndGet();
                            }
                        })
                .toArray(Runnable[]::new);

        // when
        runConcurrently(tasks);

        // then
        assertThat(granted.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 찬 방에서 접속 중인 회원이 다시 예약하면 추가 자리가 생기지 않는다")
    void reserveByConnectedMember_doesNotTakeExtraSeat() {
        // given
        for (long memberId = 1; memberId <= MAX_PARTICIPANTS; memberId++) {
            store.connect(roomId, memberId, "tab-" + memberId);
        }

        // when & then
        assertThat(store.reserve(roomId, 1L, MAX_PARTICIPANTS, HOLD)).isTrue();
        assertThat(store.reserve(roomId, 99L, MAX_PARTICIPANTS, HOLD)).isFalse();

        store.disconnect(roomId, 2L, "tab-2");
        assertThat(store.reserve(roomId, 99L, MAX_PARTICIPANTS, HOLD)).isTrue();
    }

    @Test
    @DisplayName("예약만 하고 연결하지 않으면 hold가 지난 후 다시 자리가 생긴다")
    void reserveWithoutConnect_releasesSeatAfterHold() {
        // given
        assertThat(store.reserve(roomId, 1L, 1, HOLD)).isTrue();

        // when & then
        clock.advance(HOLD.minusSeconds(1));
        assertThat(store.reserve(roomId, 2L, 1, HOLD)).isFalse();

        clock.advance(Duration.ofSeconds(2));
        assertThat(store.reserve(roomId, 2L, 1, HOLD)).isTrue();
    }

    @Test
    @DisplayName("예약한 회원이 연결하면 예약이 접속으로 바뀌어 자리를 두 번 차지하지 않는다")
    void connectAfterReserve_movesReservationToMember() {
        // given
        store.reserve(roomId, 1L, 3, HOLD);
        store.connect(roomId, 1L, TAB_A);
        store.reserve(roomId, 2L, 3, HOLD);

        // when
        boolean third = store.reserve(roomId, 3L, 3, HOLD);
        boolean fourth = store.reserve(roomId, 4L, 3, HOLD);

        // then
        assertThat(third).isTrue();
        assertThat(fourth).isFalse();
    }


    private void runConcurrently(Runnable... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    task.run();
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
