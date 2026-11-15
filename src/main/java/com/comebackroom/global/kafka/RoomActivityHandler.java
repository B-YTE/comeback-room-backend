package com.comebackroom.global.kafka;

/**
 * room-activity 메시지 type 하나를 처리하는 처리기.
 *
 * <p>Kafka는 같은 메시지를 다시 전달할 수 있으므로(at-least-once) 모든 처리기는 아래 중복 처리 규칙을 지킨다.
 * <ol>
 *     <li>처리 결과를 저장하는 업무 테이블에 중복 방지 키를 unique 제약으로 둔다.
 *         CHAT은 {@code chats.message_id}, REACTION은 {@code reactions.message_id},
 *         ROOM_CLOSED는 {@code reports.room_id}를 쓴다.</li>
 *     <li>저장하기 전에 같은 키가 이미 있는지 확인하고, 있으면 아무것도 하지 않고 반환한다.</li>
 *     <li>동시에 처리되어 unique 제약 위반이 나면 예외를 잡지 않고 그대로 던진다.
 *         에러 핸들러가 재시도할 때 2번 확인에서 이미 저장된 것을 보고 건너뛴다.</li>
 *     <li>처리는 리스너 스레드에서 동기로 끝내고, DB 작업은 하나의 트랜잭션으로 묶는다.</li>
 * </ol>
 * 저장할 업무 데이터가 없는 type을 추가할 때는 이 규칙 대신 별도의 처리 이력 테이블 도입을 검토한다.
 */
public interface RoomActivityHandler {

    RoomActivityType supportedType();

    void handle(RoomActivityMessage message);
}
