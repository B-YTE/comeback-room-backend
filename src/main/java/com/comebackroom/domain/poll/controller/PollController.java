package com.comebackroom.domain.poll.controller;

import com.comebackroom.domain.poll.dto.request.CreatePollRequest;
import com.comebackroom.domain.poll.dto.response.CreatePollResponse;
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
    public ResponseEntity<ApiResponse<CreatePollResponse>> createPoll(
            @PathVariable Long roomId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody @Valid CreatePollRequest request
            ) {
        CreatePollResponse response = pollService.createPoll(roomId, principal.memberId(), request);
        return ApiResponse.success(PollSuccessCode.POLL_CREATED, response);
    }
}
