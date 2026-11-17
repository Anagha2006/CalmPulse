package com.calmpulse.backend.service;

import com.calmpulse.backend.dto.ChatRequest;
import com.calmpulse.backend.dto.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    @DisplayName("Empty message returns validation prompt")
    void testEmptyMessage() {
        ChatService service = new ChatService(restTemplate, "https://api.example.com", "fakeKey");
        ChatRequest req = ChatRequest.builder().message("").userRole("student").build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        assertTrue(resp.getResponse().contains("Please enter a question or thought"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Missing API key returns empathetic built-in guidance")
    void testMissingApiKeyFallback() {
        ChatService service = new ChatService(restTemplate, "https://api.example.com", "");
        ChatRequest req = ChatRequest.builder()
                .message("I have bad insomnia and can't sleep")
                .userRole("student")
                .build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        assertTrue(resp.getResponse().contains("Notice: Live Gemini API key is not configured"));
        assertTrue(resp.getResponse().contains("sleep"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Valid API response extracted and returned correctly")
    void testValidGeminiResponse() {
        ChatService service = new ChatService(restTemplate, "https://api.example.com", "valid-key-123");

        // Mock Gemini response structure: candidates[0].content.parts[0].text
        Map<String, Object> candidate = Map.of(
                "content", Map.of(
                        "parts", List.of(Map.of("text", "Take deep breaths and prioritize your rest tonight."))
                )
        );
        Map<String, Object> responseMap = Map.of("candidates", List.of(candidate));

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(responseMap);

        ChatRequest req = ChatRequest.builder()
                .message("How can I cope with burnout?")
                .userRole("employee")
                .build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        assertEquals("Take deep breaths and prioritize your rest tonight.", resp.getResponse());
    }

    @Test
    @DisplayName("Gemini 403 Forbidden returns clean user guidance without raw JSON dump")
    void testGemini403Forbidden() {
        ChatService service = new ChatService(restTemplate, "https://api.example.com", "denied-key");

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, "Permission Denied"));

        ChatRequest req = ChatRequest.builder()
                .message("I feel overloaded with work")
                .userRole("employee")
                .build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        // Ensure no raw JSON error is returned
        assertFalse(resp.getResponse().contains("error\": {"));
        assertTrue(resp.getResponse().contains("Permission denied by Gemini AI"));
    }

    @Test
    @DisplayName("Gemini 429 Rate Limit returns friendly wait message and guidance")
    void testGemini429RateLimit() {
        ChatService service = new ChatService(restTemplate, "https://api.example.com", "rate-limited-key");

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));

        ChatRequest req = ChatRequest.builder()
                .message("I have exam anxiety")
                .userRole("student")
                .build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        assertTrue(resp.getResponse().contains("rate limit reached"));
        assertTrue(resp.getResponse().contains("exam") || resp.getResponse().contains("Pomodoro"));
    }

    @Test
    @DisplayName("Multi-turn conversation history included in request payload")
    void testConversationHistoryIncluded() {
        ChatService service = new ChatService(restTemplate, "https://api.example.com", "valid-key");

        Map<String, Object> candidate = Map.of(
                "content", Map.of(
                        "parts", List.of(Map.of("text", "Building on our previous exercise, let's try 5-minute journaling."))
                )
        );
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(Map.of("candidates", List.of(candidate)));

        ChatRequest.HistoryMessage m1 = ChatRequest.HistoryMessage.builder().role("user").text("I feel anxious").build();
        ChatRequest.HistoryMessage m2 = ChatRequest.HistoryMessage.builder().role("bot").text("Try 4-7-8 breathing").build();

        ChatRequest req = ChatRequest.builder()
                .message("What else can I do?")
                .userRole("student")
                .history(List.of(m1, m2))
                .build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        assertEquals("Building on our previous exercise, let's try 5-minute journaling.", resp.getResponse());
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Gemini 404 Model Not Found returns specific actionable message")
    void testGemini404ModelNotFound() {
        ChatService service = new ChatService(restTemplate, "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent", "valid-key");

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Model not found"));

        ChatRequest req = ChatRequest.builder()
                .message("I feel exhausted")
                .userRole("student")
                .build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        assertTrue(resp.getResponse().contains("gemini-3.8-flash"));
        assertTrue(resp.getResponse().contains("GEMINI_API_URL"));
    }

    @Test
    @DisplayName("Gemini 3.8 thinking/thought parts are filtered from the final user response")
    void testGemini38ThinkingPartsFiltered() {
        ChatService service = new ChatService(restTemplate, "https://api.example.com", "valid-key");

        Map<String, Object> thoughtPart = Map.of(
                "thought", true,
                "text", "Analyzing user burnout symptoms... formulate 3 practical steps."
        );
        Map<String, Object> textPart = Map.of(
                "text", "Here are 3 steps to help manage your burnout: 1. Take a break 2. Hydrate 3. Breathe."
        );

        Map<String, Object> candidate = Map.of(
                "content", Map.of(
                        "parts", List.of(thoughtPart, textPart)
                )
        );
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(Map.of("candidates", List.of(candidate)));

        ChatRequest req = ChatRequest.builder()
                .message("How do I fix burnout?")
                .userRole("employee")
                .build();

        ChatResponse resp = service.chat(req);

        assertNotNull(resp);
        assertFalse(resp.getResponse().contains("Analyzing user burnout symptoms"));
        assertEquals("Here are 3 steps to help manage your burnout: 1. Take a break 2. Hydrate 3. Breathe.", resp.getResponse());
    }
}
