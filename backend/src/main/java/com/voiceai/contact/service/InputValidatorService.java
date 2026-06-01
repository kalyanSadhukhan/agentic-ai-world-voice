package com.voiceai.contact.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates raw STT transcription before it enters state processing.
 *
 * Design principles:
 * - Accept all real scripts: Hindi (Devanagari), Bengali, English
 * - Reject ONLY: empty strings, single-character noise, heavy repeated-word gibberish
 * - NEVER reject based on script or language — multilingual input is valid
 * - When a specific field is expected (lastAskedField != null), be maximally lenient
 */
@Service
public class InputValidatorService {

    public boolean isValidInput(String transcription, String lastAskedField) {
        // 1. Null / empty
        if (transcription == null || transcription.trim().isEmpty()) {
            return false;
        }

        String cleaned = transcription.trim();

        // 2. Always allow single-character-or-more explicit responses
        String lower = cleaned.toLowerCase();
        if (lower.equals("हाँ") || lower.equals("हां") || lower.equals("yes") ||
            lower.equals("नहीं") || lower.equals("no") || lower.equals("जी") ||
            lower.equals("okay") || lower.equals("ok")) {
            return true;
        }

        // 3. Reject pure single punctuation / noise token
        String textOnly = cleaned.replaceAll("[\\p{Punct}\\s]+", "");
        if (textOnly.length() < 2) {
            return false;
        }

        // 4. If we are actively expecting a slot answer — be very lenient
        //    Accept anything up to 30 characters that isn't all-punctuation
        if (lastAskedField != null) {
            // Only hard-reject if it's pure repeated noise like "हा हा हा हा हा"
            return !isPureRepeatedNoise(cleaned);
        }

        // 5. For unsolicited input: reject only heavy repeated-word gibberish
        return !isPureRepeatedNoise(cleaned);
    }

    /**
     * Returns true if the input is almost entirely composed of one repeated word
     * (e.g. "हा हा हा हा" or "mm mm mm mm").
     */
    private boolean isPureRepeatedNoise(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length < 4) return false; // short inputs are fine

        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            // Strip punctuation before counting, but keep Unicode letters
            String clean = w.replaceAll("[^\\p{L}\\p{N}]", "");
            if (!clean.isEmpty()) {
                freq.merge(clean.toLowerCase(), 1, Integer::sum);
            }
        }

        // If any single word appears in more than 60% of tokens → noise
        for (int count : freq.values()) {
            if ((double) count / words.length > 0.6 && count >= 4) {
                return true;
            }
        }
        return false;
    }
}
