package com.calmpulse.backend.service;

import com.calmpulse.backend.client.MLClient;
import com.calmpulse.backend.dto.PredictRequest;
import com.calmpulse.backend.dto.PredictResponse;
import com.calmpulse.backend.entity.StressHistory;
import com.calmpulse.backend.entity.User;
import com.calmpulse.backend.repository.StressHistoryRepository;
import com.calmpulse.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PredictService {

    private static final Logger log = LoggerFactory.getLogger(PredictService.class);

    private final MLClient mlClient;
    private final UserRepository userRepository;
    private final StressHistoryRepository stressHistoryRepository;

    public PredictService(MLClient mlClient, UserRepository userRepository, StressHistoryRepository stressHistoryRepository) {
        this.mlClient = mlClient;
        this.userRepository = userRepository;
        this.stressHistoryRepository = stressHistoryRepository;
    }

    public PredictResponse predict(PredictRequest request) {
        String userId = request.getUserId();
        User user = null;
        if (userId != null && !userId.isBlank()) {
            user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("User with id '{}' not found in database; proceeding with anonymous assessment.", userId);
            }
        }

        // Determine effective role: prioritize request role, fall back to user role, default to student
        String role = request.getRole();
        if (role == null || role.isBlank()) {
            if (user != null && user.getRole() != null && !user.getRole().isBlank()) {
                role = user.getRole();
            } else {
                role = "student";
            }
        }
        role = role.trim().toLowerCase();
        if (!"employee".equals(role) && !"student".equals(role)) {
            role = "student";
        }

        log.info("Executing stress prediction for user '{}' with role '{}'", userId, role);

        // Call Python ML microservice
        Double stressScore = mlClient.predictStressScore(role, request);

        // Safely persist check-in history to MongoDB without breaking prediction on DB failure
        if (userId != null && !userId.isBlank()) {
            try {
                StressHistory history = StressHistory.builder()
                        .userId(userId)
                        .stressScore(stressScore)
                        .prediction(stressScore)
                        .predictionType(role)
                        .role(role)
                        .timestamp(Instant.now().toString())
                        .jobRole(request.getJobRole())
                        .workingHours(request.getWorkingHours())
                        .virtualMeetings(request.getVirtualMeetings())
                        .workLifeBalance(request.getWorkLifeBalance())
                        .accessToMentalHealth(request.getAccessToMentalHealth())
                        .satisfactionWithRemoteWork(request.getSatisfactionWithRemoteWork())
                        .companySupport(request.getCompanySupport())
                        .physicalActivity(request.getPhysicalActivity())
                        .sleepQuality(request.getSleepQuality())
                        .anxietyLevel(request.getAnxietyLevel())
                        .depression(request.getDepression())
                        .academicPerformance(request.getAcademicPerformance())
                        .studyLoad(request.getStudyLoad())
                        .teacherStudentRelationship(request.getTeacherStudentRelationship())
                        .futureCareerConcerns(request.getFutureCareerConcerns())
                        .socialSupport(request.getSocialSupport())
                        .peerPressure(request.getPeerPressure())
                        .extracurricularLoad(request.getExtracurricularLoad())
                        .build();

                stressHistoryRepository.save(history);
                log.info("Saved stress history record for user '{}'", userId);
            } catch (Exception e) {
                log.error("Failed to save stress history to MongoDB for user '{}': {}", userId, e.getMessage(), e);
            }
        }

        // Generate tailored suggestions based on role and score
        List<String> suggestions = generateSuggestions(stressScore, role);

        return PredictResponse.builder()
                .stressScore(stressScore)
                .suggestions(suggestions)
                .build();
    }

    private List<String> generateSuggestions(Double stressScore, String role) {
        List<String> suggestions = new ArrayList<>();
        if ("employee".equalsIgnoreCase(role)) {
            // Employee scale: 1 = Low, 2 = Medium, 3 = High
            if (stressScore >= 2.5) {
                suggestions.add("High burnout risk detected. Take structured micro-breaks every 60-90 minutes.");
                suggestions.add("Set firm boundaries on work communication outside regular operating hours.");
                suggestions.add("Schedule time to speak with your manager or HR about workload distribution.");
                suggestions.add("Consider engaging with your organization's mental health or wellness resources.");
            } else if (stressScore >= 1.5) {
                suggestions.add("Moderate workplace stress detected. Incorporate brief physical stretches during meetings.");
                suggestions.add("Protect your evening recovery time by disconnecting from email notifications.");
                suggestions.add("Prioritize 7-8 hours of restful sleep to prevent stress accumulation.");
            } else {
                suggestions.add("Low stress. You maintain a healthy work-life equilibrium.");
                suggestions.add("Continue your positive daily habits and regular physical exercise.");
                suggestions.add("Stay hydrated and maintain your balanced daily routine.");
            }
        } else {
            // Student scale: 0 = Low, 1 = Medium, 2 = High
            if (stressScore >= 1.5) {
                suggestions.add("High academic stress detected. Break complex assignments into small 25-minute Pomodoro intervals.");
                suggestions.add("Reach out to a trusted instructor, mentor, or campus wellness counselor for guidance.");
                suggestions.add("Ensure consistent sleep (7-8 hours) to support memory retention and mental clarity.");
                suggestions.add("Limit late-night screen time before bed to reduce nervous system arousal.");
            } else if (stressScore >= 0.5) {
                suggestions.add("Moderate academic stress detected. Create a structured study timetable with scheduled breaks.");
                suggestions.add("Incorporate daily physical movement or brief walks between study sessions.");
                suggestions.add("Connect with classmates or study groups to share learning workloads.");
            } else {
                suggestions.add("Low academic stress. Excellent study-life harmony.");
                suggestions.add("Continue practicing steady study habits and prioritizing self-care.");
                suggestions.add("Maintain your healthy balance between academics, hobbies, and social activities.");
            }
        }
        return suggestions;
    }
}
