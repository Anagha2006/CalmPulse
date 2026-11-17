package com.calmpulse.backend.service;

import com.calmpulse.backend.client.MLClient;
import com.calmpulse.backend.dto.PredictRequest;
import com.calmpulse.backend.dto.PredictResponse;
import com.calmpulse.backend.entity.StressHistory;
import com.calmpulse.backend.entity.User;
import com.calmpulse.backend.repository.StressHistoryRepository;
import com.calmpulse.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PredictServiceTest {

    @Mock
    private MLClient mlClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StressHistoryRepository stressHistoryRepository;

    @InjectMocks
    private PredictService predictService;

    private User sampleStudentUser;
    private User sampleEmployeeUser;

    @BeforeEach
    void setUp() {
        sampleStudentUser = User.builder()
                .id("user123")
                .email("student@calmpulse.com")
                .role("student")
                .build();

        sampleEmployeeUser = User.builder()
                .id("emp123")
                .email("emp@calmpulse.com")
                .role("employee")
                .build();
    }

    @Test
    @DisplayName("Student Low Stress: Score 0.0 returns Low stress suggestions")
    void testStudentLowStress() {
        when(userRepository.findById("user123")).thenReturn(Optional.of(sampleStudentUser));
        when(mlClient.predictStressScore(eq("student"), any())).thenReturn(0.0);

        PredictRequest req = PredictRequest.builder()
                .userId("user123")
                .role("student")
                .anxietyLevel(2)
                .depression(1)
                .sleepQuality("5")
                .build();

        PredictResponse resp = predictService.predict(req);

        assertNotNull(resp);
        assertEquals(0.0, resp.getStressScore());
        assertTrue(resp.getSuggestions().stream().anyMatch(s -> s.contains("Low academic stress")));
        verify(stressHistoryRepository, times(1)).save(any(StressHistory.class));
    }

    @Test
    @DisplayName("Student Moderate Stress: Score 1.0 returns Moderate stress suggestions")
    void testStudentModerateStress() {
        when(userRepository.findById("user123")).thenReturn(Optional.of(sampleStudentUser));
        when(mlClient.predictStressScore(eq("student"), any())).thenReturn(1.0);

        PredictRequest req = PredictRequest.builder()
                .userId("user123")
                .role("student")
                .anxietyLevel(12)
                .depression(10)
                .sleepQuality("3")
                .build();

        PredictResponse resp = predictService.predict(req);

        assertNotNull(resp);
        assertEquals(1.0, resp.getStressScore());
        assertTrue(resp.getSuggestions().stream().anyMatch(s -> s.contains("Moderate academic stress")));
        verify(stressHistoryRepository, times(1)).save(any(StressHistory.class));
    }

    @Test
    @DisplayName("Student High Stress: Score 2.0 returns High stress suggestions")
    void testStudentHighStress() {
        when(userRepository.findById("user123")).thenReturn(Optional.of(sampleStudentUser));
        when(mlClient.predictStressScore(eq("student"), any())).thenReturn(2.0);

        PredictRequest req = PredictRequest.builder()
                .userId("user123")
                .role("student")
                .anxietyLevel(24)
                .depression(22)
                .sleepQuality("1")
                .build();

        PredictResponse resp = predictService.predict(req);

        assertNotNull(resp);
        assertEquals(2.0, resp.getStressScore());
        assertTrue(resp.getSuggestions().stream().anyMatch(s -> s.contains("High academic stress")));
        verify(stressHistoryRepository, times(1)).save(any(StressHistory.class));
    }

    @Test
    @DisplayName("Employee Low Stress: Score 1.0 returns Low stress suggestions")
    void testEmployeeLowStress() {
        when(userRepository.findById("emp123")).thenReturn(Optional.of(sampleEmployeeUser));
        when(mlClient.predictStressScore(eq("employee"), any())).thenReturn(1.0);

        PredictRequest req = PredictRequest.builder()
                .userId("emp123")
                .role("employee")
                .jobRole("Software Engineer")
                .workingHours(35)
                .workLifeBalance(5)
                .build();

        PredictResponse resp = predictService.predict(req);

        assertNotNull(resp);
        assertEquals(1.0, resp.getStressScore());
        assertTrue(resp.getSuggestions().stream().anyMatch(s -> s.contains("Low stress")));
        verify(stressHistoryRepository, times(1)).save(any(StressHistory.class));
    }

    @Test
    @DisplayName("Employee Moderate Stress: Score 2.0 returns Moderate suggestions")
    void testEmployeeModerateStress() {
        when(userRepository.findById("emp123")).thenReturn(Optional.of(sampleEmployeeUser));
        when(mlClient.predictStressScore(eq("employee"), any())).thenReturn(2.0);

        PredictRequest req = PredictRequest.builder()
                .userId("emp123")
                .role("employee")
                .jobRole("HR")
                .workingHours(45)
                .workLifeBalance(3)
                .build();

        PredictResponse resp = predictService.predict(req);

        assertNotNull(resp);
        assertEquals(2.0, resp.getStressScore());
        assertTrue(resp.getSuggestions().stream().anyMatch(s -> s.contains("Moderate workplace stress")));
        verify(stressHistoryRepository, times(1)).save(any(StressHistory.class));
    }

    @Test
    @DisplayName("Employee High Stress: Score 3.0 returns High burnout suggestions")
    void testEmployeeHighStress() {
        when(userRepository.findById("emp123")).thenReturn(Optional.of(sampleEmployeeUser));
        when(mlClient.predictStressScore(eq("employee"), any())).thenReturn(3.0);

        PredictRequest req = PredictRequest.builder()
                .userId("emp123")
                .role("employee")
                .jobRole("Project Manager")
                .workingHours(65)
                .workLifeBalance(1)
                .build();

        PredictResponse resp = predictService.predict(req);

        assertNotNull(resp);
        assertEquals(3.0, resp.getStressScore());
        assertTrue(resp.getSuggestions().stream().anyMatch(s -> s.contains("High burnout risk")));
        verify(stressHistoryRepository, times(1)).save(any(StressHistory.class));
    }

    @Test
    @DisplayName("Request role overrides DB user role correctly")
    void testRoleOverride() {
        // User is student in DB, but submitting employee check-in form
        when(userRepository.findById("user123")).thenReturn(Optional.of(sampleStudentUser));
        when(mlClient.predictStressScore(eq("employee"), any())).thenReturn(2.0);

        PredictRequest req = PredictRequest.builder()
                .userId("user123")
                .role("employee")
                .jobRole("Data Scientist")
                .workingHours(40)
                .build();

        PredictResponse resp = predictService.predict(req);

        assertNotNull(resp);
        verify(mlClient).predictStressScore(eq("employee"), any());

        ArgumentCaptor<StressHistory> captor = ArgumentCaptor.forClass(StressHistory.class);
        verify(stressHistoryRepository).save(captor.capture());
        assertEquals("employee", captor.getValue().getRole());
    }

    @Test
    @DisplayName("MongoDB save exception does not fail prediction response")
    void testMongoDbFailureResilience() {
        when(userRepository.findById("user123")).thenReturn(Optional.of(sampleStudentUser));
        when(mlClient.predictStressScore(eq("student"), any())).thenReturn(1.0);
        doThrow(new RuntimeException("MongoDB connection timeout")).when(stressHistoryRepository).save(any());

        PredictRequest req = PredictRequest.builder()
                .userId("user123")
                .role("student")
                .anxietyLevel(10)
                .build();

        PredictResponse resp = predictService.predict(req);

        // Assert response is still returned to the user despite DB error
        assertNotNull(resp);
        assertEquals(1.0, resp.getStressScore());
        assertFalse(resp.getSuggestions().isEmpty());
    }
}
