package com.voiceai.contact.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ClinicConfig {

    public static final Map<String, Object> CLINIC_SCHEDULE = Map.of(
        "Orthopedic", List.of(
            Map.of("name", "Dr. Sharma", "days", List.of("Monday", "Wednesday"), "start", "10:00", "end", "14:00"),
            Map.of("name", "Dr. Reddy", "days", List.of("Tuesday", "Thursday"), "start", "12:00", "end", "16:00"),
            Map.of("name", "Dr. Khan", "days", List.of("Friday", "Saturday"), "start", "09:00", "end", "13:00"),
            Map.of("name", "Dr. Iyer", "days", List.of("Sunday"), "start", "10:00", "end", "13:00")
        ),

        "Cardiology", List.of(
            Map.of("name", "Dr. Verma", "days", List.of("Monday", "Thursday"), "start", "09:00", "end", "13:00"),
            Map.of("name", "Dr. Nair", "days", List.of("Tuesday", "Friday"), "start", "11:00", "end", "15:00"),
            Map.of("name", "Dr. Gupta", "days", List.of("Wednesday", "Saturday"), "start", "10:00", "end", "14:00"),
            Map.of("name", "Dr. Das", "days", List.of("Sunday"), "start", "09:00", "end", "12:00")
        ),

        "Neurology", List.of(
            Map.of("name", "Dr. Mehta", "days", List.of("Monday", "Tuesday"), "start", "11:00", "end", "15:00"),
            Map.of("name", "Dr. Banerjee", "days", List.of("Wednesday", "Thursday"), "start", "12:00", "end", "16:00"),
            Map.of("name", "Dr. Pillai", "days", List.of("Friday"), "start", "10:00", "end", "14:00"),
            Map.of("name", "Dr. Roy", "days", List.of("Saturday", "Sunday"), "start", "09:00", "end", "13:00")
        ),

        "Dermatology", List.of(
            Map.of("name", "Dr. Kapoor", "days", List.of("Monday", "Friday"), "start", "10:00", "end", "14:00"),
            Map.of("name", "Dr. Singh", "days", List.of("Tuesday", "Saturday"), "start", "11:00", "end", "15:00"),
            Map.of("name", "Dr. Ali", "days", List.of("Wednesday"), "start", "12:00", "end", "16:00"),
            Map.of("name", "Dr. Joshi", "days", List.of("Sunday"), "start", "10:00", "end", "13:00")
        ),

        "General Physician", List.of(
            Map.of("name", "Dr. Kumar", "days", List.of("Monday", "Tuesday", "Wednesday"), "start", "09:00", "end", "17:00"),
            Map.of("name", "Dr. Sharma", "days", List.of("Thursday", "Friday"), "start", "10:00", "end", "16:00"),
            Map.of("name", "Dr. Thomas", "days", List.of("Saturday"), "start", "09:00", "end", "13:00"),
            Map.of("name", "Dr. Patel", "days", List.of("Sunday"), "start", "10:00", "end", "14:00")
        ),

        "Pediatrics", List.of(
            Map.of("name", "Dr. Arora", "days", List.of("Monday", "Thursday"), "start", "10:00", "end", "14:00"),
            Map.of("name", "Dr. Mishra", "days", List.of("Tuesday", "Friday"), "start", "11:00", "end", "15:00"),
            Map.of("name", "Dr. Fernandes", "days", List.of("Wednesday"), "start", "12:00", "end", "16:00"),
            Map.of("name", "Dr. Sen", "days", List.of("Saturday", "Sunday"), "start", "09:00", "end", "13:00")
        )
    );
}
