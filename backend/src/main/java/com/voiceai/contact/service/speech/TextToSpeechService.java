package com.voiceai.contact.service.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class TextToSpeechService {

    @Value("${SARVAM_API_KEY:}")
    private String sarvamApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public String synthesizeSpeech(String text) throws Exception {
        if (sarvamApiKey == null || sarvamApiKey.isEmpty()) {
            throw new Exception("Sarvam API Key is missing in configuration.");
        }

        String url = "https://api.sarvam.ai/text-to-speech";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-subscription-key", sarvamApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", Collections.singletonList(text));
        body.put("target_language_code", "hi-IN");
        body.put("speaker", "anushka");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            if (root.has("audios") && root.get("audios").isArray() && root.get("audios").size() > 0) {
                return root.get("audios").get(0).asText();
            }
            throw new Exception("No audio returned. Payload: " + response.getBody());
        } catch (HttpClientErrorException e) {
            throw new Exception("Sarvam TTS Client Error: " + e.getResponseBodyAsString());
        }
    }
}
