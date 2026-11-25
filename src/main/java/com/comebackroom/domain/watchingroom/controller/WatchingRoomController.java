package com.comebackroom.domain.watchingroom.controller;

import com.comebackroom.domain.watchingroom.dto.request.JoinWatchingRoomRequest;
import com.comebackroom.domain.watchingroom.dto.response.JoinWatchingRoomResponse;
import com.comebackroom.domain.watchingroom.response.WatchingRoomSuccessCode;
import com.comebackroom.domain.watchingroom.service.WatchingRoomService;
import com.comebackroom.global.response.ApiResponse;
import com.comebackroom.global.security.principal.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class WatchingRoomController {

    private final WatchingRoomService roomService;

    @PostMapping("/watching-rooms/{roomId}/participants")
    public ResponseEntity<ApiResponse<JoinWatchingRoomResponse>> joinRoom(
            @PathVariable(name = "roomId") Long roomId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody @Valid JoinWatchingRoomRequest request
            ) {
        JoinWatchingRoomResponse response = roomService.joinRoom(
                roomId, principal.memberId(), request
        );

        return ApiResponse.success(
                WatchingRoomSuccessCode.WATCHING_ROOM_JOINED, response
        );
    }

}
