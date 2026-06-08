package com.voiceai.contact.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResponseGenerationService {

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateResponse(String contextStr) {
        if (groqApiKey == null || groqApiKey.isEmpty()) {
            return "माफ़ कीजिए, कुछ तकनीकी दिक्कत आई।";
        }

        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        
        String sysPrompt =
            "You are a strict response formatter for a clinic receptionist voice assistant.\n" +
            "You are NOT an AI. You do NOT think. You ONLY echo the GIVEN CONTEXT into ONE Hindi sentence.\n\n" +

            "=== ABSOLUTE RULES ===\n" +
            "1. NEVER change the department name. Use it EXACTLY as given (e.g. Orthopedic stays 'Orthopedic', DO NOT translate).\n" +
            "2. NEVER translate department names to Sanskrit/Hindi medical terms (e.g. Orthopedic ≠ वृक्कशास्त्र).\n" +
            "3. NEVER change time values. Convert 24h to 12h ONLY: 13:00 → 1 बजे, 10:00 → 10 बजे. Never say '13 बजे'.\n" +
            "4. NEVER add patient name prefix like 'राहुल जी'. Start directly with the doctor or time.\n" +
            "5. NEVER add words: बीमारी, चिकित्सा, विशेषज्ञ, समस्या, or any word NOT in CONTEXT.\n" +
            "6. NEVER infer day/date. Use EXACTLY what is in CONTEXT.\n" +
            "7. Return ONLY one sentence. No explanation.\n\n" +

            "=== LANGUAGE ===\n" +
            "- Hindi (Devanagari only).\n" +
            "- Short, polite receptionist style.\n" +
            "- Max 12 words.\n\n" +

            "=== NEXT ACTION RULES ===\n" +
            "INFORM      → 'रविवार को डॉ आयर 10 से 1 तक उपलब्ध हैं।' (day+doctor+start से end तक only)\n" +
            "CONFIRM_DETAILS → 'आपका अपॉइंटमेंट [day] को [time] बजे [doctor] के साथ है, क्या कन्फर्म करूँ?' (use EXACT values only)\n" +
            "OUTPUT FORMAT: Return ONLY the final Hindi sentence. Nothing else.";

        systemMsg.put("content", sysPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", contextStr);
        messages.add(userMsg);

        body.put("messages", messages);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").path(0).path("message").path("content").asText().trim();
        } catch (Exception e) {
            System.err.println("Groq NLG Error: " + e.getMessage());
            return "माफ़ कीजिए, कुछ तकनीकी दिक्कत आई।";
        }
    }
}
