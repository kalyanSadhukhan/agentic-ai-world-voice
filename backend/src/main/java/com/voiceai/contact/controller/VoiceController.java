package com.voiceai.contact.controller;

import com.voiceai.contact.dto.VoiceResponse;
import com.voiceai.contact.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@CrossOrigin(origins = "*") // Allows calls from any frontend port like 5173
public class VoiceController {

    @Autowired
    private VoiceService voiceService;

    @PostMapping
    public ResponseEntity<?> handleVoice(
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            VoiceResponse response = voiceService.processVoice(audio, sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            Map<String, String> errorResponse = new HashMap<>();
            ex.printStackTrace(); // Log full stack trace
            errorResponse.put("status", "error");
            errorResponse.put("message", ex.getMessage() != null ? ex.getMessage() : ex.toString());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
