package com.voiceai.contact.service.parser;

import com.voiceai.contact.service.util.ConversationUtils;
import org.springframework.stereotype.Service;

@Service
public class NameParserService {

    public String parseName(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        
        // Clean text from punctuation and leading/trailing spaces
        String clean = text.replaceAll("[।.,!?]+", "").trim();
        
        // Check if it is a valid name format
        if (ConversationUtils.isValidName(clean)) {
            return clean;
        }
        
        return null;
    }
}
