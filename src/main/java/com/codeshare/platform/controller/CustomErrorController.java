package com.codeshare.platform.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codeshare.platform.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> handleError(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        
        if (statusCode == null) {
            statusCode = 500; // Default to internal server error
        }
        
        String errorMessage;
        switch (statusCode) {
            case 404:
                errorMessage = "Resource not found";
                break;
            case 403:
                errorMessage = "Access denied";
                break;
            case 401:
                errorMessage = "Unauthorized access";
                break;
            case 400:
                errorMessage = "Bad request";
                break;
            default:
                errorMessage = "Unexpected error occurred";
        }
        
        return new ResponseEntity<>(
            ApiResponse.error(errorMessage), 
            HttpStatus.valueOf(statusCode)
        );
    }
    
    // For handling HTML requests to /error
    @RequestMapping(value = "/error", produces = MediaType.TEXT_HTML_VALUE)
    public String errorHtml() {
        return "redirect:/error.html";
    }
}