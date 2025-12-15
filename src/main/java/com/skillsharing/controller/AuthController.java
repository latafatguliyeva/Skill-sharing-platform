package com.skillsharing.controller;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.skillsharing.dto.AuthResponse;
import com.skillsharing.dto.LoginRequest;
import com.skillsharing.dto.RegisterRequest;
import com.skillsharing.model.User;
import com.skillsharing.security.JwtUtil;
import com.skillsharing.service.UserService;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CREDENTIALS_FILE_PATH = "src/main/resources/credentials.json";

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Check if username exists
        if (userService.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        
        // Check if email exists
        if (userService.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already exists");
        }
        
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setFullName(request.getFullName());
        user.setBio(request.getBio());
        user.setLocation(request.getLocation());
        
        User savedUser = userService.registerUser(user);
        String token = jwtUtil.generateToken(savedUser.getUsername(), savedUser.getId());
        
        return ResponseEntity.ok(new AuthResponse(token, savedUser.getUsername(), 
                                                  savedUser.getEmail(), savedUser.getId()));
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.findByUsername(request.getUsername());

        if (!userOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(),
                                                  user.getEmail(), user.getId()));
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            // Verify the Google ID token
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY)
                    .setAudience(Collections.singletonList(getClientId()))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String googleId = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            // Check if user exists with this Google ID
            Optional<User> existingUserOpt = userService.findByGoogleId(googleId);
            User user;

            if (existingUserOpt.isPresent()) {
                // User exists, update their Google tokens
                user = existingUserOpt.get();
                user.setGoogleAccessToken(request.getAccessToken());
                user.setGoogleRefreshToken(request.getRefreshToken());
                user.setGoogleTokenExpiry(request.getTokenExpiry());
                user = userService.updateUser(user);
            } else {
                // Check if user exists with this email
                Optional<User> emailUserOpt = userService.findByEmail(email);
                if (emailUserOpt.isPresent()) {
                    // Link Google account to existing user
                    user = emailUserOpt.get();
                    user.setGoogleId(googleId);
                    user.setGoogleEmail(email);
                    user.setGoogleAccessToken(request.getAccessToken());
                    user.setGoogleRefreshToken(request.getRefreshToken());
                    user.setGoogleTokenExpiry(request.getTokenExpiry());
                    user = userService.updateUser(user);
                } else {
                    // Create new user
                    user = new User();
                    user.setUsername(email.split("@")[0] + "_" + googleId.substring(0, 8));
                    user.setEmail(email);
                    user.setFullName(name);
                    user.setGoogleId(googleId);
                    user.setGoogleEmail(email);
                    user.setGoogleAccessToken(request.getAccessToken());
                    user.setGoogleRefreshToken(request.getRefreshToken());
                    user.setGoogleTokenExpiry(request.getTokenExpiry());

                    // Generate a random password for the user (they'll use Google login)
                    user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));

                    user = userService.registerUser(user);
                }
            }

            String token = jwtUtil.generateToken(user.getUsername(), user.getId());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername(),
                                                      user.getEmail(), user.getId()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Google authentication failed: " + e.getMessage());
        }
    }

    private String getClientId() throws IOException {
        // Load client secrets to get the client ID
        java.io.File credentialsFile = new java.io.File(CREDENTIALS_FILE_PATH);
        if (!credentialsFile.exists()) {
            throw new IOException("Credentials file not found");
        }

        InputStreamReader reader = new InputStreamReader(new FileInputStream(credentialsFile));
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        return clientSecrets.getDetails().getClientId();
    }

    // DTO for Google login request
    public static class GoogleLoginRequest {
        private String idToken;
        private String accessToken;
        private String refreshToken;
        private Long tokenExpiry;

        // Getters and setters
        public String getIdToken() { return idToken; }
        public void setIdToken(String idToken) { this.idToken = idToken; }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public Long getTokenExpiry() { return tokenExpiry; }
        public void setTokenExpiry(Long tokenExpiry) { this.tokenExpiry = tokenExpiry; }
    }
}
