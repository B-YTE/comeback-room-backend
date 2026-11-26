package com.comebackroom.domain.watchingroom.dto.request;

import jakarta.validation.constraints.Pattern;

public record JoinWatchingRoomRequest(
        @Pattern(regexp = "[0-9]{4,10}") // 숫자 4-10자만 허용
        String privateCode
) {
}
