package com.voiceai.contact.service.booking;

import com.voiceai.contact.config.ClinicConfig;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class AppointmentValidator {

    public boolean isPastDate(String dateStr) {
        if (dateStr == null) return false;
        try {
            LocalDate parsed = LocalDate.parse(dateStr);
            return parsed.isBefore(LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTimeInSlot(String userTime, String department, String dateStr) {
        if (userTime == null || department == null || dateStr == null) return true;
        
        String dayOfWeek = getDayOfWeek(dateStr);
        if (dayOfWeek == null) return true;
        
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.equalsIgnoreCase(department) 
                    || dept.toLowerCase().contains(department.toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        
        if (matchedDept == null) return true;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors == null) return true;
        
        for (Map<String, Object> doc : doctors) {
            @SuppressWarnings("unchecked")
            List<String> days = (List<String>) doc.get("days");
            if (!days.contains(dayOfWeek)) continue;
            try {
                int slotStart = parseToMinutes((String) doc.get("start"));
                int slotEnd   = parseToMinutes((String) doc.get("end"));
                int requested = parseToMinutes(userTime);
                if (requested < 0) return true; // parse failed — allow
                return requested >= slotStart && requested < slotEnd;
            } catch (Exception e) {
                return true;
            }
        }
        return true; // no doctor for that day
    }

    private String getDayOfWeek(String dateStr) {
        if (dateStr == null) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr);
            String day = date.getDayOfWeek().name();
            return day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private int parseToMinutes(String timeStr) {
        if (timeStr == null) return -1;
        String s = timeStr.trim().toUpperCase();
        try {
            if (s.contains(":")) {
                String[] parts = s.replace("AM", "").replace("PM", "").trim().split(":");
                int h = Integer.parseInt(parts[0].trim());
                int m = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                if (s.contains("PM") && h != 12) h += 12;
                if (s.contains("AM") && h == 12) h = 0;
                return h * 60 + m;
            } else {
                String num = s.replace("AM", "").replace("PM", "").replace("बजे", "").trim();
                num = num.replaceAll("[^0-9]", "");
                if (num.isEmpty()) return -1;
                int h = Integer.parseInt(num);
                if (s.contains("PM") && h != 12) h += 12;
                if (s.contains("AM") && h == 12) h = 0;
                return h * 60;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}
