package com.comebackroom.global.websocket.message;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RoomMessageType {

    CHAT_MESSAGE("chats"),
    REACTION_BURST("reactions");

    private static final String ROOM_DESTINATION_FORMAT = "/sub/watching-rooms/%d/%s";

    private final String channel;

    public String destination(Long roomId) {
        return ROOM_DESTINATION_FORMAT.formatted(roomId, channel);
    }
}
