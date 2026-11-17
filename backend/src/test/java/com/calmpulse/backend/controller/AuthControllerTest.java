package com.calmpulse.backend.controller;

import com.calmpulse.backend.dto.LoginRequest;
import com.calmpulse.backend.dto.LoginResponse;
import com.calmpulse.backend.dto.SignupRequest;
import com.calmpulse.backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("POST /signup creates user and returns 201 Created")
    void testSignup() {
        SignupRequest req = SignupRequest.builder()
                .email("newuser@calmpulse.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Doe")
                .role("student")
                .build();

        LoginResponse resp = LoginResponse.builder()
                .message("User created successfully")
                .userId("new-id-123")
                .isNewUser(true)
                .role("student")
                .build();

        when(userService.signup(any())).thenReturn(resp);

        ResponseEntity<LoginResponse> responseEntity = authController.signup(req);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("new-id-123", responseEntity.getBody().getUserId());
    }

    @Test
    @DisplayName("POST /login authenticates user and returns 200 OK")
    void testLogin() {
        LoginRequest req = LoginRequest.builder()
                .email("user@calmpulse.com")
                .password("Password123!")
                .build();

        LoginResponse resp = LoginResponse.builder()
                .message("Login successful")
                .userId("id-123")
                .isNewUser(false)
                .role("employee")
                .build();

        when(userService.login(any())).thenReturn(resp);

        ResponseEntity<LoginResponse> responseEntity = authController.login(req);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("employee", responseEntity.getBody().getRole());
    }
}
