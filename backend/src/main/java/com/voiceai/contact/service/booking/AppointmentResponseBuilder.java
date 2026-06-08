package com.voiceai.contact.service.booking;

import com.voiceai.contact.config.ClinicConfig;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AppointmentResponseBuilder {

    private static final Map<String, String> DEPT_PHONETICS = new LinkedHashMap<>() {{
        put("General Physician", "जनरल फिजिशियन");
        put("Orthopedic",        "ऑर्थोपेडिक");
        put("Neurology",         "न्यूरोलॉजी");
        put("Cardiology",        "कार्डियोलॉजी");
        put("Dermatology",       "डर्मेटोलॉजी");
        put("Pediatrics",        "पीडियाट्रिक्स");
    }};

    private static final Map<String, String> DOCTOR_PHONETICS = new LinkedHashMap<>() {{
        put("Iyer",      "आयर");
        put("Roy",       "रॉय");
        put("Khan",      "खान");
        put("Sharma",    "शर्मा");
        put("Verma",     "वर्मा");
        put("Nair",      "नायर");
        put("Gupta",     "गुप्ता");
        put("Das",       "दास");
        put("Reddy",     "रेड्डी");
        put("Pillai",    "पिल्लई");
        put("Kapoor",    "कपूर");
        put("Singh",     "सिंह");
        put("Ali",       "अली");
        put("Joshi",     "जोशी");
        put("Kumar",     "कुमार");
        put("Thomas",    "थॉमस");
        put("Patel",     "पटेल");
        put("Arora",     "अरोड़ा");
        put("Mishra",    "मिश्रा");
        put("Fernandes", "फर्नांडिस");
        put("Sen",       "सेन");
    }};

    public String buildBookingSummary(String patientName, String department, String dateStr, String timeStr, String assignedDoctor) {
        StringBuilder sb = new StringBuilder();
        sb.append("BOOKING SUMMARY:\n");
        sb.append("Name: ").append(patientName != null ? patientName : "Not provided").append("\n");
        sb.append("Department: ").append(department != null ? department : "Not provided").append("\n");
        String day = getDayOfWeek(dateStr);
        sb.append("Date: ").append(day != null ? day : (dateStr != null ? dateStr : "Not provided")).append("\n");
        sb.append("Time: ").append(timeStr != null ? timeStr : "Not provided").append("\n");
        sb.append("Doctor: ").append(assignedDoctor != null ? assignedDoctor : "To be assigned").append("\n");
        return sb.toString();
    }

    public String buildAvailabilityResponse(String department, String dateStr) {
        if (department == null) {
            return "विभाग नहीं बताया गया।";
        }
        
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.equalsIgnoreCase(department) 
                    || dept.toLowerCase().contains(department.toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        
        if (matchedDept == null) return "इस विभाग के लिए कोई डॉक्टर नहीं मिला।";

        String dayOfWeek = getDayOfWeek(dateStr);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors == null) return "इस विभाग में अभी कोई डॉक्टर उपलब्ध नहीं हैं।";

        for (Map<String, Object> doc : doctors) {
            @SuppressWarnings("unchecked")
            List<String> days = (List<String>) doc.get("days");
            if (dayOfWeek != null && !days.contains(dayOfWeek)) continue;

            String startH   = toNaturalTime((String) doc.get("start"));
            String endH     = toNaturalTime((String) doc.get("end"));
            String dayHindi = toHindiDay(dayOfWeek != null ? dayOfWeek : ((List<String>) doc.get("days")).get(0));
            String docDisplay = formatDoctorName(doc.get("name").toString());
            return dayHindi + " को " + docDisplay + " " + startH + " से " + endH + " तक उपलब्ध हैं।";
        }
        
        return "इस दिन कोई डॉक्टर उपलब्ध नहीं हैं।";
    }

    public String resolvePostConfirmQuery(String lowerInput, String dateStr, String timeStr, String department, String assignedDoctor) {
        String dayH  = toHindiDay(getDayOfWeek(dateStr));
        String timeN = toNaturalTime(timeStr);
        String dept  = formatDeptName(department);
        String doc   = assignedDoctor != null ? formatDoctorName(assignedDoctor) : "";

        if (lowerInput.contains("विभाग") || lowerInput.contains("department")
                || lowerInput.contains("किस में") || lowerInput.contains("कौन सा विभाग")) {
            return "जी, आपका अपॉइंटमेंट " + dept + " विभाग में है।";
        }
        if (lowerInput.contains("कब") || lowerInput.contains("कौन सा दिन")
                || lowerInput.contains("तारीख") || lowerInput.contains("date")
                || lowerInput.contains("दिन")) {
            return "आपका अपॉइंटमेंट " + dayH + " को है।";
        }
        if (lowerInput.contains("समय") || lowerInput.contains("टाइम")
                || lowerInput.contains("time") || lowerInput.contains("बजे")) {
            return "आपका अपॉइंटमेंट " + timeN + " का है।";
        }
        if (lowerInput.contains("डॉक्टर") || lowerInput.contains("doctor")
                || lowerInput.contains("कौन") || lowerInput.contains("डॉ")) {
            return "आपका अपॉइंटमेंट " + doc + " के साथ है।";
        }
        
        return null;
    }

    public String toHindiDay(String day) {
        if (day == null) return "";
        return switch (day) {
            case "Monday"    -> "सोमवार";
            case "Tuesday"   -> "मंगलवार";
            case "Wednesday" -> "बुधवार";
            case "Thursday"  -> "गुरुवार";
            case "Friday"    -> "शुक्रवार";
            case "Saturday"  -> "शनिवार";
            case "Sunday"    -> "रविवार";
            default          -> day;
        };
    }

    public String toHindiDate(String isoDate) {
        if (isoDate == null) return "";
        try {
            String[] parts = isoDate.split("-");
            if (parts.length < 3) return isoDate;
            int day   = Integer.parseInt(parts[2]);
            int month = Integer.parseInt(parts[1]);
            String[] MONTHS_HI = {
                "", "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
                "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
            };
            String monthHi = (month >= 1 && month <= 12) ? MONTHS_HI[month] : parts[1];
            return day + " " + monthHi;
        } catch (Exception e) {
            return isoDate;
        }
    }

    public String toNaturalTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            int hour24;
            String up = raw.trim().toUpperCase();
            if (up.contains("AM") || up.contains("PM")) {
                String numPart = up.replaceAll("[^0-9:]", "").split(":")[0];
                int h = Integer.parseInt(numPart);
                if (up.contains("PM") && h != 12) h += 12;
                if (up.contains("AM") && h == 12) h = 0;
                hour24 = h;
            } else if (raw.contains(":")) {
                hour24 = Integer.parseInt(raw.split(":")[0].trim());
            } else {
                hour24 = Integer.parseInt(raw.replaceAll("[^0-9]", "").trim());
            }

            int h12 = hour24 == 0 ? 12 : (hour24 > 12 ? hour24 - 12 : hour24);
            String prefix;
            if      (hour24 >= 5  && hour24 < 12) prefix = "सुबह";
            else if (hour24 == 12)                 prefix = "दोपहर";
            else if (hour24 >= 12 && hour24 < 17)  prefix = "दोपहर";
            else if (hour24 >= 17 && hour24 < 21)  prefix = "शाम";
            else                                    prefix = "रात";

            return prefix + " " + toHindiNumber(h12) + " बजे";
        } catch (Exception e) {
            return raw;
        }
    }

    public String formatDeptName(String dept) {
        if (dept == null) return "";
        return DEPT_PHONETICS.getOrDefault(dept, dept);
    }

    public String formatDoctorName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String name = raw.trim().replaceFirst("^Dr\\.\\s*", "").trim();
        for (Map.Entry<String, String> e : DOCTOR_PHONETICS.entrySet()) {
            name = name.replace(e.getKey(), e.getValue());
        }
        return "डॉक्टर " + name;
    }

    private String toHindiNumber(int n) {
        return switch (n) {
            case 1  -> "एक";
            case 2  -> "दो";
            case 3  -> "तीन";
            case 4  -> "चार";
            case 5  -> "पाँच";
            case 6  -> "छह";
            case 7  -> "सात";
            case 8  -> "आठ";
            case 9  -> "नौ";
            case 10 -> "दस";
            case 11 -> "ग्यारह";
            case 12 -> "बारह";
            default -> String.valueOf(n);
        };
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
}
