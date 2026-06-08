package com.voiceai.contact.service.parser;

import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TimeParserService {

    public String parseTime(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String s = text.toLowerCase().trim();

        // 1. Check for standard patterns like 10:30 AM/PM, 10 AM/PM, 14:00, etc.
        Pattern standardPattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = standardPattern.matcher(s);
        if (m1.find()) {
            String hour = m1.group(1);
            String minute = m1.group(2) != null ? m1.group(2) : "00";
            String ampm = m1.group(3).toUpperCase();
            return hour + (m1.group(2) != null ? ":" + minute : "") + " " + ampm;
        }

        // 2. Check for HH:MM 24h pattern
        Pattern hhmmPattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher m2 = hhmmPattern.matcher(s);
        if (m2.find()) {
            return m2.group(1) + ":" + m2.group(2);
        }

        // 3. Check for Hindi time expressions like "10 baje", "10 बजे", "सुबह 10", etc.
        Pattern hindiPattern = Pattern.compile("(\\d{1,2})(?:\\s*[:.]\\s*(\\d{2}))?\\s*(baje|बजे)?", Pattern.CASE_INSENSITIVE);
        Matcher m3 = hindiPattern.matcher(s);
        if (m3.find()) {
            String hourStr = m3.group(1);
            String minuteStr = m3.group(2) != null ? m3.group(2) : "00";
            
            // Determine AM/PM prefix based on context words
            String ampm = "";
            if (s.contains("सुबह") || s.contains("morning")) {
                ampm = " AM";
            } else if (s.contains("दोपहर") || s.contains("afternoon") || s.contains("शाम") || s.contains("evening") || s.contains("रात") || s.contains("night")) {
                ampm = " PM";
            }
            
            return hourStr + (m3.group(2) != null ? ":" + minuteStr : "") + ampm;
        }

        // 4. Check if it's a plain number (1 to 12 or 24h format)
        Pattern numberPattern = Pattern.compile("^\\s*(\\d{1,2})\\s*$");
        Matcher m4 = numberPattern.matcher(s);
        if (m4.find()) {
            int h = Integer.parseInt(m4.group(1));
            if (h >= 1 && h <= 24) {
                return m4.group(1);
            }
        }

        return null;
    }
}
