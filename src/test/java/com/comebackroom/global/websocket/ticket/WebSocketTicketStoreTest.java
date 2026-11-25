package com.comebackroom.global.websocket.ticket;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class WebSocketTicketStoreTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 1L;
    private static final String TAB_ID = "tab-1";

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    private final WebSocketTicketStore ticketStore =
            new WebSocketTicketStore(new StringRedisTemplate(connectionFactory));

    @BeforeAll
    static void connect() {
        // 컨테이너 시작 후에만 접속 주소를 알 수 있으므로, 연결 팩토리를 여기서 생성
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }


    @Test
    @DisplayName("발급된 ticket으로 회원과 방 정보를 조회한다")
    void useTicket_returnsMemberAndRoom() {
        String ticket = ticketStore.issue(MEMBER_ID, ROOM_ID, TAB_ID);
        Optional<TicketInfo> result = ticketStore.consume(ticket);

        assertThat(result).contains(new TicketInfo(MEMBER_ID, ROOM_ID, TAB_ID));
    }

    @Test
    @DisplayName("이미 사용된 ticket은 다시 사용할 수 없다")
    void useTicketTwice_isRejected() {
        String ticket = ticketStore.issue(MEMBER_ID, ROOM_ID, TAB_ID);
        ticketStore.consume(ticket);

        assertThat(ticketStore.consume(ticket)).isEmpty();
    }

    @Test
    @Timeout(10)
    @DisplayName("동일한 ticket을 동시에 사용하면 한 번만 성공한다")
    void useTicketConcurrently_succeedsOnlyOnce() throws Exception {
        String ticket = ticketStore.issue(MEMBER_ID, ROOM_ID, TAB_ID);
        int threads = 50; // 50개의 스레드가 동시에 티켓 사용 시도

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads); // 모든 스레드가 대기 상태임을 확인하기 위한 래치
        CountDownLatch start = new CountDownLatch(1); // 모든 스레드를 동시에 시작시키기 위한 래치
        AtomicInteger successCount = new AtomicInteger();

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (ticketStore.consume(ticket).isPresent()) {
                        successCount.incrementAndGet();
                    }
                    return null; // 값을 반환하는 람다로 작성해 예외를 던질 수 있는 Callable로 submit
                }));
            }

            ready.await();
            start.countDown(); // 스레드 생성 시점의 차이로 요청이 분산되지 않도록 모든 스레드를 한 번에 실행
            for (Future<?> future : futures) { // 모든 작업의 종료 대기
                future.get();
            }
            assertThat(successCount.get()).isEqualTo(1); // 한 번만 성공했는지 확인
        } finally {
            pool.shutdown();
        }
    }

}
