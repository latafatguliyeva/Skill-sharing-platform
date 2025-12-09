package com.skillsharing.controller;

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

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    
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
}
