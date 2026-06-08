package com.voiceai.contact.service.booking;

import com.voiceai.contact.config.ClinicConfig;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class DoctorAssignmentService {

    public String matchDoctor(String department, String dateStr) {
        if (department == null) return "Any available doctor";
        
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.toLowerCase().contains(department.toLowerCase()) 
                    || department.toLowerCase().contains(dept.toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        
        if (matchedDept == null) return "General Doctor";
        
        String dayOfWeek = getDayOfWeek(dateStr);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors != null) {
            for (Map<String, Object> doc : doctors) {
                @SuppressWarnings("unchecked")
                List<String> days = (List<String>) doc.get("days");
                if (dayOfWeek != null && days.contains(dayOfWeek)) {
                    return (String) doc.get("name");
                }
            }
            if (!doctors.isEmpty()) {
                return (String) doctors.get(0).get("name");
            }
        }
        
        return "General Doctor";
    }

    public String getDayOfWeek(String dateStr) {
        if (dateStr == null) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr);
            String day = date.getDayOfWeek().name();
            return day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}
