package com.calmpulse.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

    @NotBlank(message = "Message cannot be blank")
    private String message;

    @JsonProperty("user_role")
    private String userRole;

    @JsonProperty("user_id")
    private String userId;

    private List<HistoryMessage> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryMessage {
        private String role; // "user" or "bot" / "model"
        private String text;
    }
}
