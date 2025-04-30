package com.codeshare.platform.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.dto.UserDto;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> createUser(@RequestBody User user) {
        User createdUser = userService.createUser(user);
        return new ResponseEntity<>(ApiResponse.success("User created successfully", convertToDto(createdUser)), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable Long id) {
        Optional<User> userOpt = userService.getUserById(id);
        return userOpt.map(user -> new ResponseEntity<>(ApiResponse.success(convertToDto(user)), HttpStatus.OK))
                .orElse(new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<UserDto> userDtos = users.stream().map(this::convertToDto).collect(Collectors.toList());
        return new ResponseEntity<>(ApiResponse.success(userDtos), HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(@PathVariable Long id, @RequestBody User user) {
        Optional<User> existingUser = userService.getUserById(id);
        if (existingUser.isPresent()) {
            user.setId(id);
            User updatedUser = userService.updateUser(user);
            return new ResponseEntity<>(ApiResponse.success("User updated successfully", convertToDto(updatedUser)), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        Optional<User> existingUser = userService.getUserById(id);
        if (existingUser.isPresent()) {
            userService.deleteUser(id);
            return new ResponseEntity<>(ApiResponse.success("User deleted successfully", null), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
    }
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(@RequestBody Map<String, Object> profileData, Authentication authentication) {
        // Get current authenticated user
        String username = authentication.getName();
        Optional<User> userOpt = userService.getUserByUsername(username);
        
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
        
        User user = userOpt.get();
        boolean updated = false;
        
        // Update fullName if provided
        if (profileData.containsKey("fullName")) {
            String fullName = (String) profileData.get("fullName");
            user.setFullName(fullName);
            updated = true;
        }
        
        // Update email if provided
        if (profileData.containsKey("email")) {
            String email = (String) profileData.get("email");
            
            // Validate email format
            if (email != null && !email.isEmpty() && isValidEmail(email)) {
                // Check if email is already used by another user
                if (!userService.existsByEmail(email) || user.getEmail().equals(email)) {
                    user.setEmail(email);
                    updated = true;
                } else {
                    return new ResponseEntity<>(ApiResponse.error("Email already in use"), HttpStatus.BAD_REQUEST);
                }
            } else if (email != null && !email.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("Invalid email format"), HttpStatus.BAD_REQUEST);
            }
        }
        
        // Update bio if provided - this is the key part we're checking
        if (profileData.containsKey("bio")) {
            String bio = (String) profileData.get("bio");
            // Set the bio field
            user.setBio(bio);
            // Log to confirm bio is being set
            System.out.println("Setting user bio to: " + bio);
            updated = true;
        }
        
        // Save changes if any updates were made
        if (updated) {
            User updatedUser = userService.updateUser(user);
            // Convert to DTO
            UserDto dto = convertToDto(updatedUser);
            return new ResponseEntity<>(
                ApiResponse.success("Profile updated successfully", dto),
                HttpStatus.OK
            );
        } else {
            return new ResponseEntity<>(
                ApiResponse.success("No changes to apply", convertToDto(user)),
                HttpStatus.OK
            );
        }
    }



    // Helper method to convert User to UserDto
    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        
        // Make sure we include bio in the DTO response
        dto.setBio(user.getBio());
        
        return dto;
    }
    
    // Helper method to validate email format
    private boolean isValidEmail(String email) {
        // Basic email validation using regex
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return email.matches(emailRegex);
    }
    
}