package com.voiceai.contact.service;

import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DateNormalizerService {

    public String getDateContext(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        StringBuilder ctx = new StringBuilder();

        ctx.append("- 'आज' (Today) maps exactly to: ").append(today).append("\n");
        ctx.append("- 'कल' (Tomorrow) maps exactly to: ").append(today.plusDays(1)).append("\n");
        ctx.append("- 'परसों' (Day after tomorrow) maps exactly to: ").append(today.plusDays(2)).append("\n");

        // Add next-occurrence dates for all 7 weekdays so LLM never guesses wrong
        DayOfWeek[] DAYS = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};
        String[][] LABELS = {
            {"Monday", "सोमवार", "मंडे", "Monday"},
            {"Tuesday", "मंगलवार", "Tuesday"},
            {"Wednesday", "बुधवार", "Wednesday"},
            {"Thursday", "गुरुवार", "Thursday"},
            {"Friday", "शुक्रवार", "Friday"},
            {"Saturday", "शनिवार", "Saturday"},
            {"Sunday", "रविवार", "Sunday"}
        };
        for (int i = 0; i < DAYS.length; i++) {
            LocalDate next = nextWeekday(today, DAYS[i]);
            ctx.append("- '").append(LABELS[i][0]).append("' / '").append(LABELS[i][1]).append("' maps to: ").append(next).append("\n");
        }
        return ctx.toString().trim();
    }

    /** Returns the next occurrence of the given weekday (today counts if today is that day). */
    public static LocalDate nextWeekday(LocalDate from, DayOfWeek target) {
        int daysUntil = (target.getValue() - from.getDayOfWeek().getValue() + 7) % 7;
        return from.plusDays(daysUntil == 0 ? 7 : daysUntil);
    }
}
