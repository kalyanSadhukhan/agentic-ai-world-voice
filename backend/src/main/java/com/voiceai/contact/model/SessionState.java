package com.voiceai.contact.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionState {
    public enum Mode { BOOKING, CONFIRMATION, QUERY, POST_CONFIRM, RESCHEDULE, END }

    private String sessionId;
    private String patientName;
    private String department;
    private String date;
    private String time;
    private String assignedDoctor;
    private boolean confirmed;
    private boolean greetingDone;   // true once the opening greeting has been sent
    private String lastAskedField;
    private int repeatCount;        // how many consecutive turns the same question has been asked
    private Mode mode;
    private List<Map<String, String>> messageHistory;

    public SessionState(String sessionId) {
        this.sessionId = sessionId;
        this.mode = Mode.BOOKING;
        this.messageHistory = new ArrayList<>();
        this.greetingDone = false;
        this.repeatCount = 0;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getAssignedDoctor() { return assignedDoctor; }
    public void setAssignedDoctor(String assignedDoctor) { this.assignedDoctor = assignedDoctor; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public boolean isGreetingDone() { return greetingDone; }
    public void setGreetingDone(boolean greetingDone) { this.greetingDone = greetingDone; }

    public int getRepeatCount() { return repeatCount; }
    public void incrementRepeatCount() { this.repeatCount++; }
    public void resetRepeatCount() { this.repeatCount = 0; }

    /** Resets repeatCount automatically when the field being asked changes. */
    public String getLastAskedField() { return lastAskedField; }
    public void setLastAskedField(String newField) {
        if (newField == null || !newField.equals(this.lastAskedField)) {
            this.repeatCount = 0;
        }
        this.lastAskedField = newField;
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public List<Map<String, String>> getMessageHistory() { return messageHistory; }

    public void appendMessage(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        this.messageHistory.add(msg);
    }
}
