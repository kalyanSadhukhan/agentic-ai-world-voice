package com.voiceai.contact.service.util;

import java.util.Set;

public class ConversationUtils {

    private static final Set<String> INVALID_NAME_TOKENS = Set.of(
        "appointment", "अपॉइंटमेंट", "confirmation", "कन्फर्म", "booking",
        "reschedule", "रीशेड्यूल", "neurology", "न्यूरोलॉजी", "orthopedic",
        "cardiology", "dermatology", "pediatrics", "physician", "doctor"
    );

    public static String normalizeTranscription(String text) {
        if (text == null) return "";
        return text
            .replace("অর্থোপেডিক", "Orthopedic")
            .replace("অস্থি",      "Orthopedic")
            .replace("ডাক্তার",    "Doctor")
            .replace("অ্যাপয়েন্টমেন্ট", "Appointment")
            .replace("রাহুল",      "राहुल")
            .replace("কাল",       "कल")
            .replace("আজ",        "आज");
    }

    public static boolean isValidValue(String val) {
        return val != null && !val.trim().isEmpty() && !val.trim().equalsIgnoreCase("null");
    }

    public static boolean isValidName(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.split("\\s+").length > 3) return false;
        
        String lower = n.toLowerCase();
        for (String bad : INVALID_NAME_TOKENS) {
            if (lower.contains(bad)) return false;
        }
        
        return !lower.contains("appointment") && !lower.contains("अपॉइंटमेंट")
            && !lower.contains("चाहिए") && !lower.contains("बुक") && !lower.contains("लेना")
            && !lower.contains("करना") && !lower.contains("दिखाना") && !lower.contains("doctor")
            && !lower.contains("विभाग") && !lower.contains("क्लिनिक");
    }

    public static boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        String t = time.trim().toUpperCase();
        
        if (t.matches("\\d{1,2}\\s*(AM|PM)")) return true;
        if (t.matches("\\d{1,2}:\\d{2}(\\s*(AM|PM))?")) return true;
        
        if (time.contains("बजे") || time.contains("सुबह") || time.contains("दोपहर") || time.contains("शाम")) {
            return true;
        }
        
        if (t.matches("\\d{1,2}")) {
            try {
                int h = Integer.parseInt(t);
                return h >= 1 && h <= 12;
            } catch (Exception e) {
                return false;
            }
        }
        
        String low = time.toLowerCase();
        String[] datesOrMonths = {
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार",
            "april", "may", "june", "january", "february", "march", "july", "august",
            "अप्रैल", "मार्च", "जनवरी"
        };
        for (String day : datesOrMonths) {
            if (low.contains(day)) return false;
        }
        return false;
    }
}
