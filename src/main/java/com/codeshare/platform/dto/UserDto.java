package com.codeshare.platform.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String bio;

    // Even though Lombok @Data should generate these methods,
    // let's add them explicitly to ensure they're present
    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
    
    // Constructor with bio
    public UserDto(Long id, String username, String email, String fullName, String bio) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.bio = bio;
    }
    
    // Default constructor
    public UserDto() {
    }
}