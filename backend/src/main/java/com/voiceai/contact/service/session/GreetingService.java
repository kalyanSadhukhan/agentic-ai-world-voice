package com.voiceai.contact.service.session;

import org.springframework.stereotype.Service;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
public class GreetingService {

    private static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    public String generateGreeting() {
        LocalTime now = LocalTime.now(ZONE_KOLKATA);
        int hour = now.getHour();

        String greetingPrefix;
        if (hour >= 5 && hour < 12) {
            greetingPrefix = "सुप्रभात।";
        } else if (hour >= 12 && hour < 17) {
            greetingPrefix = "नमस्कार।";
        } else if (hour >= 17 && hour < 21) {
            greetingPrefix = "शुभ संध्या।";
        } else {
            greetingPrefix = "नमस्कार।";
        }

        return greetingPrefix + " मैं वॉइस एआई एजेंट प्रो की अपॉइंटमेंट सहायक हूँ। कृपया बताइए मैं आपकी कैसे सहायता कर सकती हूँ?";
    }
}
