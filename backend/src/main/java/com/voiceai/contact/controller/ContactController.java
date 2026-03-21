package com.voiceai.contact.controller;

import com.voiceai.contact.dto.ContactFormRequest;
import com.voiceai.contact.dto.EnhancedFormRequest;
import com.voiceai.contact.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/contact")
@CrossOrigin(origins = "*") // Allows calls from any frontend port like 5173
public class ContactController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/simple")
    public ResponseEntity<Map<String, String>> handleSimpleContact(@Valid @RequestBody ContactFormRequest request) {
        emailService.sendContactEmail(request);
        return successResponse();
    }

    @PostMapping("/enhanced")
    public ResponseEntity<Map<String, String>> handleEnhancedContact(@Valid @RequestBody EnhancedFormRequest request) {
        emailService.sendEnhancedInquiryEmail(request);
        return successResponse();
    }

    private ResponseEntity<Map<String, String>> successResponse() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Email sent successfully");
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleExceptions(Exception ex) {
        Map<String, String> response = new HashMap<>();
        ex.printStackTrace(); // Log full stack trace
        response.put("status", "error");
        response.put("message", ex.getMessage() != null ? ex.getMessage() : ex.toString());
        
        // Debugging info to verify if .env is properly loaded
        String loadedUser = System.getProperty("EMAIL_USER");
        response.put("debug_email_configured", loadedUser != null ? loadedUser : "NOT_LOADED_FROM_ENV");
        
        return ResponseEntity.status(500).body(response);
    }
}
