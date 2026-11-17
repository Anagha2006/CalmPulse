package com.calmpulse.backend.client;

import com.calmpulse.backend.dto.MlPredictRequest;
import com.calmpulse.backend.dto.MlPredictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class MLClient {

    private static final Logger log = LoggerFactory.getLogger(MLClient.class);

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public MLClient(RestTemplate restTemplate, @Value("${ml.service.url}") String mlServiceUrl) {
        this.restTemplate = restTemplate;
        this.mlServiceUrl = mlServiceUrl;
    }

    public Double predictStressScore(String role, Object data) {
        String baseUrl = mlServiceUrl.endsWith("/") ? mlServiceUrl.substring(0, mlServiceUrl.length() - 1) : mlServiceUrl;
        String endpoint = baseUrl + "/predict_ml";
        log.info("Sending prediction request for role '{}' to Python ML Service at: {}", role, endpoint);

        MlPredictRequest requestPayload = MlPredictRequest.builder()
                .role(role)
                .data(data)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.USER_AGENT, "CalmPulse-Backend/1.0 (Java/21; SpringBoot)");

        HttpEntity<MlPredictRequest> requestEntity = new HttpEntity<>(requestPayload, headers);

        int maxRetries = 3;
        int delayMs = 2000;
        ResponseEntity<MlPredictResponse> response = null;
        Exception lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("Attempt {}/{} contacting ML service at {}", i + 1, maxRetries, endpoint);
                response = restTemplate.postForEntity(
                        endpoint,
                        requestEntity,
                        MlPredictResponse.class
                );
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    break;
                }
            } catch (HttpStatusCodeException statusEx) {
                lastException = statusEx;
                String responseBody = statusEx.getResponseBodyAsString();
                log.warn("Attempt {} failed with HTTP {}: {}. Response body: {}",
                        i + 1, statusEx.getStatusCode(), statusEx.getMessage(), responseBody);
                if (statusEx.getStatusCode().value() == 400) {
                    // Bad request shouldn't be retried
                    throw new RuntimeException("Invalid prediction input: " + responseBody, statusEx);
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Prediction interrupted", ie);
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} failed to contact ML service: {}. Retrying in {}ms...", i + 1, e.getMessage(), delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Prediction interrupted", ie);
                }
            }
        }

        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            String errorMsg = lastException != null ? lastException.getMessage() : "Invalid response";
            log.error("Failed to contact Python ML service after {} attempts: {}", maxRetries, errorMsg);
            throw new RuntimeException("Unable to contact ML microservice. Details: " + errorMsg, lastException);
        }

        Double score = response.getBody().getStressScore();
        if (score == null) {
            throw new RuntimeException("ML microservice returned null stress_score");
        }

        log.info("Successfully received stress score from ML service: {}", score);
        return score;
    }
}
