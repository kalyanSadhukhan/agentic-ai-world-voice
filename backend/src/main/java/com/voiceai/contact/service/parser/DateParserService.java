package com.voiceai.contact.service.parser;

import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DateParserService {

    private static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    public String parseDate(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String s = text.toLowerCase().trim();

        LocalDate today = LocalDate.now(ZONE_KOLKATA);

        // 1. Check relative date keywords
        if (s.contains("aaj") || s.contains("today") || s.contains("आज")) {
            return today.toString();
        }
        if (s.contains("kal") || s.contains("tomorrow") || s.contains("कल")) {
            return today.plusDays(1).toString();
        }
        if (s.contains("parso") || s.contains("day after tomorrow") || s.contains("परसों") || s.contains("परसो")) {
            return today.plusDays(2).toString();
        }

        // 2. Check weekdays
        Map<String, DayOfWeek> keywords = new LinkedHashMap<>();
        keywords.put("monday",      DayOfWeek.MONDAY);
        keywords.put("मंडे",         DayOfWeek.MONDAY);
        keywords.put("सोमवार",       DayOfWeek.MONDAY);
        keywords.put("tuesday",     DayOfWeek.TUESDAY);
        keywords.put("मंगलवार",      DayOfWeek.TUESDAY);
        keywords.put("wednesday",   DayOfWeek.WEDNESDAY);
        keywords.put("बुधवार",       DayOfWeek.WEDNESDAY);
        keywords.put("thursday",    DayOfWeek.THURSDAY);
        keywords.put("गुरुवार",      DayOfWeek.THURSDAY);
        keywords.put("friday",      DayOfWeek.FRIDAY);
        keywords.put("शुक्रवार",     DayOfWeek.FRIDAY);
        keywords.put("saturday",    DayOfWeek.SATURDAY);
        keywords.put("शनिवार",      DayOfWeek.SATURDAY);
        keywords.put("sunday",      DayOfWeek.SUNDAY);
        keywords.put("रविवार",      DayOfWeek.SUNDAY);

        for (Map.Entry<String, DayOfWeek> entry : keywords.entrySet()) {
            if (s.contains(entry.getKey())) {
                LocalDate target = nextWeekday(today, entry.getValue());
                return target.toString();
            }
        }

        // Check if input is already in YYYY-MM-DD format
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return text.trim();
        }

        return null;
    }

    public static LocalDate nextWeekday(LocalDate from, DayOfWeek target) {
        int daysUntil = (target.getValue() - from.getDayOfWeek().getValue() + 7) % 7;
        return from.plusDays(daysUntil == 0 ? 7 : daysUntil);
    }
}
