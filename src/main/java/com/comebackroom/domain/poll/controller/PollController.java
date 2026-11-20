package com.comebackroom.domain.poll.controller;

import com.comebackroom.domain.poll.dto.request.CreatePollRequest;
import com.comebackroom.domain.poll.dto.response.PollListResponse;
import com.comebackroom.domain.poll.dto.response.PollResponse;
import com.comebackroom.domain.poll.response.PollSuccessCode;
import com.comebackroom.domain.poll.service.PollService;
import com.comebackroom.global.response.ApiResponse;
import com.comebackroom.global.security.principal.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @PostMapping("/watching-rooms/{roomId}/polls")
    public ResponseEntity<ApiResponse<PollResponse>> createPoll(
            @PathVariable Long roomId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody @Valid CreatePollRequest request
            ) {
        PollResponse response = pollService.createPoll(roomId, principal.memberId(), request);
        return ApiResponse.success(PollSuccessCode.POLL_CREATED, response);
    }

    @GetMapping("/watching-rooms/{roomId}/polls")
    public ResponseEntity<ApiResponse<PollListResponse>> getPolls(
            @PathVariable Long roomId,
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        PollListResponse response = pollService.getPolls(roomId, principal.memberId());
        return ApiResponse.success(PollSuccessCode.POLL_LIST_RETRIEVED, response);
    }
}
