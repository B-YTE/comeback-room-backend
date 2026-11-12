package com.comebackroom.global.kafka;

public interface RoomActivityHandler {

    RoomActivityType supportedType();

    void handle(RoomActivityMessage message);
}
