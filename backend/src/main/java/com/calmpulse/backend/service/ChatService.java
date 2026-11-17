package com.calmpulse.backend.service;

import com.calmpulse.backend.dto.ChatRequest;
import com.calmpulse.backend.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RestTemplate restTemplate;
    private final String geminiApiUrl;
    private final String geminiApiKey;

    public ChatService(RestTemplate restTemplate,
                       @Value("${gemini.api.url}") String geminiApiUrl,
                       @Value("${gemini.api.key:}") String geminiApiKey) {
        this.restTemplate = restTemplate;
        this.geminiApiUrl = geminiApiUrl;
        this.geminiApiKey = geminiApiKey != null ? geminiApiKey.trim() : "";
    }

    public ChatResponse chat(ChatRequest request) {
        String userMessage = request.getMessage() != null ? request.getMessage().trim() : "";
        if (userMessage.isEmpty()) {
            return ChatResponse.builder()
                    .response("Please enter a question or thought so I can help support your wellness journey.")
                    .build();
        }

        String role = request.getUserRole() != null && !request.getUserRole().isBlank()
                ? request.getUserRole().trim().toLowerCase()
                : "student";

        // If Gemini API Key is not configured, deliver supportive role-based wellness guidance
        if (geminiApiKey.isEmpty() || geminiApiKey.equalsIgnoreCase("YOUR_API_KEY")) {
            log.warn("GEMINI_API_KEY is not configured in environment variables. Providing built-in wellness response.");
            return ChatResponse.builder()
                    .response(generateSupportiveWellnessFallback(userMessage, role,
                            "Notice: Live Gemini API key is not configured on the server. Showing offline guidance."))
                    .build();
        }

        try {
            log.info("Sending chat query to Gemini API for role '{}'", role);

            // Construct Gemini request with System Instruction & Multi-Turn Contents
            Map<String, Object> requestPayload = new HashMap<>();

            // System Instruction
            String systemText = "You are Pulse Bot, a compassionate, supportive AI wellness assistant in CalmPulse (an AI-based burnout and stress detection system). " +
                    "The user is a " + role + ". " +
                    "Provide empathetic, practical, and evidence-based advice for managing stress, burnout, study/workload balance, mindfulness, and mental wellness. " +
                    "Keep answers warm, structured, and easy to read. " +
                    "Important: You are an educational and wellness companion, NOT a medical doctor or licensed therapist. " +
                    "Do not provide clinical diagnoses, medical treatments, or prescriptions. " +
                    "If the user mentions severe self-harm or a medical crisis, gently encourage contacting professional emergency hotlines.";

            Map<String, Object> sysInstruction = Map.of(
                    "parts", List.of(Map.of("text", systemText))
            );
            requestPayload.put("systemInstruction", sysInstruction);

            // Contents Array (including conversation history if available)
            List<Map<String, Object>> contents = new ArrayList<>();

            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                // Include up to 8 recent exchanges to preserve context within limits
                int start = Math.max(0, request.getHistory().size() - 8);
                for (int i = start; i < request.getHistory().size(); i++) {
                    ChatRequest.HistoryMessage hm = request.getHistory().get(i);
                    if (hm != null && hm.getText() != null && !hm.getText().isBlank()) {
                        String gRole = ("user".equalsIgnoreCase(hm.getRole())) ? "user" : "model";
                        contents.add(Map.of(
                                "role", gRole,
                                "parts", List.of(Map.of("text", hm.getText()))
                        ));
                    }
                }
            }

            // Append current user message
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", userMessage))
            ));
            requestPayload.put("contents", contents);

            // Optional generation configuration
            requestPayload.put("generationConfig", Map.of(
                    "temperature", 0.7,
                    "maxOutputTokens", 800
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.USER_AGENT, "CalmPulse-Backend/1.0");

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestPayload, headers);

            String urlWithKey = geminiApiUrl + (geminiApiUrl.contains("?") ? "&key=" : "?key=") + geminiApiKey;

            Map responseMap = restTemplate.postForObject(urlWithKey, httpEntity, Map.class);
            if (responseMap == null) {
                log.warn("Gemini API returned an empty body.");
                return ChatResponse.builder()
                        .response(generateSupportiveWellnessFallback(userMessage, role, null))
                        .build();
            }

            // Extract candidate text
            List candidates = (List) responseMap.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map candidate = (Map) candidates.get(0);
                Map contentMap = (Map) candidate.get("content");
                if (contentMap != null) {
                    List parts = (List) contentMap.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Map partMap = (Map) parts.get(0);
                        String text = (String) partMap.get("text");
                        if (text != null && !text.isBlank()) {
                            return ChatResponse.builder().response(text.trim()).build();
                        }
                    }
                }
            }

            log.warn("Gemini returned unexpected structure: {}", responseMap);
            return ChatResponse.builder()
                    .response(generateSupportiveWellnessFallback(userMessage, role, null))
                    .build();

        } catch (HttpStatusCodeException httpEx) {
            int code = httpEx.getStatusCode().value();
            log.error("Gemini API HTTP Error {}: {}", code, httpEx.getResponseBodyAsString());

            String userFriendlyNotice;
            if (code == 400) {
                userFriendlyNotice = "The AI service received an invalid request or key configuration. Please verify your GEMINI_API_KEY in Google AI Studio.";
            } else if (code == 403) {
                userFriendlyNotice = "Permission denied by Gemini AI. Please check that the API key has access to the Generative Language API.";
            } else if (code == 429) {
                userFriendlyNotice = "Pulse Bot is experiencing high volume (rate limit reached). Please wait a few moments and try again.";
            } else {
                userFriendlyNotice = "The AI service is temporarily unavailable (HTTP " + code + ").";
            }

            return ChatResponse.builder()
                    .response(generateSupportiveWellnessFallback(userMessage, role, userFriendlyNotice))
                    .build();

        } catch (Exception e) {
            log.error("Error communicating with Gemini API: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .response(generateSupportiveWellnessFallback(userMessage, role,
                            "We couldn't connect to the AI service right now. Here is some immediate guidance:"))
                    .build();
        }
    }

    private String generateSupportiveWellnessFallback(String userMessage, String role, String notice) {
        StringBuilder sb = new StringBuilder();
        if (notice != null && !notice.isBlank()) {
            sb.append("[").append(notice).append("]\n\n");
        }

        String lower = userMessage.toLowerCase();
        sb.append("Hello! I'm here to support you. ");

        if (lower.contains("sleep") || lower.contains("insomnia") || lower.contains("tired")) {
            sb.append("When feeling exhausted, sleep hygiene is vital:\n")
              .append("• Avoid screens 30-45 minutes before bedtime to allow melatonin release.\n")
              .append("• Try a 4-7-8 breathing exercise: Inhale for 4s, hold for 7s, exhale slowly for 8s.\n")
              .append("• Keep your sleeping space dark, quiet, and cool.");
        } else if (lower.contains("exam") || lower.contains("study") || lower.contains("grade") || lower.contains("academic")) {
            sb.append("Academic pressure can feel overwhelming, but breaking tasks down brings relief:\n")
              .append("• Use the Pomodoro Technique: Focus on one task for 25 minutes, then take a genuine 5-minute break.\n")
              .append("• Write down the top 3 priorities for today, and let go of trying to do everything at once.\n")
              .append("• Speak with a mentor or peer—you don't have to carry the academic load alone.");
        } else if (lower.contains("meeting") || lower.contains("boss") || lower.contains("deadline") || lower.contains("work")) {
            sb.append("Workplace fatigue and burnout build up quickly during intense projects:\n")
              .append("• Block out 15-minute 'focus buffer' windows between virtual meetings.\n")
              .append("• Clarify task urgency with your team to avoid multitasking on low-priority items.\n")
              .append("• Disconnect from email and work chats once your workday concludes.");
        } else {
            if ("employee".equalsIgnoreCase(role)) {
                sb.append("Managing workplace burnout begins with intentional micro-recovery:\n")
                  .append("• Stand up, stretch, and step away from your workstation for 5 minutes.\n")
                  .append("• Practice physiological sighing (two quick inhales through the nose, long exhale through the mouth).\n")
                  .append("• Set gentle boundaries around your time and energy today.");
            } else {
                sb.append("Coping with academic stress requires pacing and self-compassion:\n")
                  .append("• Take a 5-minute pause to reset your attention and hydrate.\n")
                  .append("• Remember that your value is not defined by test scores or constant productivity.\n")
                  .append("• Reach out to friends, family, or campus support when things feel heavy.");
            }
        }

        return sb.toString();
    }
}
