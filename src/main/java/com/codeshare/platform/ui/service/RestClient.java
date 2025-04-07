package com.codeshare.platform.ui.service;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.codeshare.platform.ui.util.SessionManager;

@Service
public class RestClient {

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private SessionManager sessionManager;
    
    private static final String API_BASE_URL = "http://localhost:8080/api";
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        
        if (sessionManager.isAuthenticated()) {
            headers.setBearerAuth(sessionManager.getAuthToken());
        }
        
        return headers;
    }
    
    public <T> T get(String endpoint, Class<T> responseType) {
        HttpEntity<?> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<T> response = restTemplate.exchange(
            API_BASE_URL + endpoint,
            HttpMethod.GET,
            entity,
            responseType
        );
        return response.getBody();
    }
    
    public <T> T get(String endpoint, ParameterizedTypeReference<T> responseType) {
        HttpEntity<?> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<T> response = restTemplate.exchange(
            API_BASE_URL + endpoint,
            HttpMethod.GET,
            entity,
            responseType
        );
        return response.getBody();
    }
    
    public <T, R> R post(String endpoint, T request, Class<R> responseType) {
        HttpEntity<T> entity = new HttpEntity<>(request, createHeaders());
        ResponseEntity<R> response = restTemplate.exchange(
            API_BASE_URL + endpoint,
            HttpMethod.POST,
            entity,
            responseType
        );
        return response.getBody();
    }
    
    public <T, R> R put(String endpoint, T request, Class<R> responseType) {
        HttpEntity<T> entity = new HttpEntity<>(request, createHeaders());
        ResponseEntity<R> response = restTemplate.exchange(
            API_BASE_URL + endpoint,
            HttpMethod.PUT,
            entity,
            responseType
        );
        return response.getBody();
    }
    
    public <T> void delete(String endpoint, Class<T> responseType) {
        HttpEntity<?> entity = new HttpEntity<>(createHeaders());
        restTemplate.exchange(
            API_BASE_URL + endpoint,
            HttpMethod.DELETE,
            entity,
            responseType
        );
    }
}