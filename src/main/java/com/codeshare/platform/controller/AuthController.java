package com.codeshare.platform.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.dto.JwtAuthResponse;
import com.codeshare.platform.dto.LoginRequest;
import com.codeshare.platform.dto.SignupRequest;
import com.codeshare.platform.dto.UserDto;
import com.codeshare.platform.model.User;
import com.codeshare.platform.security.JwtUtil;
import com.codeshare.platform.service.UserService;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<User>> registerUser(@RequestBody SignupRequest signupRequest) {
        try {
            // Check if username exists
            if (userService.existsByUsername(signupRequest.getUsername())) {
                return new ResponseEntity<>(ApiResponse.error("Username is already taken!"), HttpStatus.BAD_REQUEST);
            }
            
            // Check if email exists
            if (userService.existsByEmail(signupRequest.getEmail())) {
                return new ResponseEntity<>(ApiResponse.error("Email is already in use!"), HttpStatus.BAD_REQUEST);
            }
            
            // Create new user
            User user = new User();
            user.setUsername(signupRequest.getUsername());
            user.setEmail(signupRequest.getEmail());
            user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
            user.setFullName(signupRequest.getFullName());
            
            User savedUser = userService.createUser(user);
            
            return new ResponseEntity<>(ApiResponse.success("User registered successfully", savedUser), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.error("Registration failed: " + e.getMessage()), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> authenticateUser(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
            
            String jwt = jwtUtil.generateToken(authentication);
            
            return new ResponseEntity<>(
                    ApiResponse.success("Login successful", new JwtAuthResponse(jwt)),
                    HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                    ApiResponse.error("Authentication failed: " + e.getMessage()),
                    HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return new ResponseEntity<>(ApiResponse.error("Not authenticated"), HttpStatus.UNAUTHORIZED);
        }
        
        String username = authentication.getName();
        Optional<User> userOpt = userService.getUserByUsername(username);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            UserDto userDto = new UserDto();
            userDto.setId(user.getId());
            userDto.setUsername(user.getUsername());
            userDto.setEmail(user.getEmail());
            userDto.setFullName(user.getFullName());
            
            return new ResponseEntity<>(ApiResponse.success(userDto), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
    }
}