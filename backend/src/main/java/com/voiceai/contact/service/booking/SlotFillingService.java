package com.voiceai.contact.service.booking;

import com.voiceai.contact.service.session.SessionState;
import com.voiceai.contact.service.util.ConversationUtils;
import org.springframework.stereotype.Service;

@Service
public class SlotFillingService {

    private final AppointmentValidator appointmentValidator;

    public SlotFillingService(AppointmentValidator appointmentValidator) {
        this.appointmentValidator = appointmentValidator;
    }

    public String fillSlots(SessionState state, String cleanText, String rawTranscription,
                            String nameCand, String deptCand, String dateCand, String timeCand) {
        String status = "SUCCESS";

        int wordCount = rawTranscription.split("\\s+").length;

        // 1. Loop Prevention: if same question asked >= 2 times -> force-accept raw input
        if (state.getRepeatCount() >= 2 && state.getLastAskedField() != null) {
            System.out.println("[LOG] Loop detected (repeat=" + state.getRepeatCount() + "), force-accepting for: " + state.getLastAskedField());
            forceAcceptForField(state, cleanText, rawTranscription, nameCand, deptCand, dateCand, timeCand);
            state.resetRepeatCount();
            return status;
        }

        // 2. Short contextual answers (<= 3 words) direct matching
        if (wordCount <= 3 && state.getLastAskedField() != null) {
            String field = state.getLastAskedField();
            if (field.equals("name") && state.getPatientName() == null) {
                String val = ConversationUtils.isValidValue(nameCand) ? nameCand : cleanText;
                if (ConversationUtils.isValidName(val)) {
                    state.setPatientName(val);
                }
            } else if (field.equals("department") && state.getDepartment() == null) {
                String val = ConversationUtils.isValidValue(deptCand) ? deptCand : cleanText;
                if (ConversationUtils.isValidValue(val)) {
                    state.setDepartment(val);
                }
            } else if (field.equals("date") && state.getDate() == null) {
                String val = ConversationUtils.isValidValue(dateCand) ? dateCand : cleanText;
                if (ConversationUtils.isValidValue(val)) {
                    if (val.contains(",")) {
                        return "MULTI_DATE";
                    }
                    if (appointmentValidator.isPastDate(val)) {
                        return "PAST_DATE";
                    }
                    state.setDate(val);
                }
            } else if (field.equals("time") && state.getTime() == null) {
                String val = ConversationUtils.isValidValue(timeCand) ? timeCand : cleanText;
                if (ConversationUtils.isValidValue(val)) {
                    if (ConversationUtils.isValidTimeFormat(val)) {
                        if (appointmentValidator.isTimeInSlot(val, state.getDepartment(), state.getDate())) {
                            state.setTime(val);
                        } else {
                            status = "TIME_INVALID";
                        }
                    } else {
                        status = "TIME_INVALID";
                    }
                }
            }
            return status;
        }

        // 3. Standard multi-word hydration (only overwrite if slot is empty)
        if (ConversationUtils.isValidValue(nameCand) && ConversationUtils.isValidName(nameCand) && state.getPatientName() == null) {
            state.setPatientName(nameCand);
        }

        if (ConversationUtils.isValidValue(deptCand) && state.getDepartment() == null) {
            state.setDepartment(deptCand);
        }

        if (ConversationUtils.isValidValue(dateCand) && state.getDate() == null) {
            if (dateCand.contains(",")) {
                return "MULTI_DATE";
            }
            if (appointmentValidator.isPastDate(dateCand)) {
                return "PAST_DATE";
            }
            state.setDate(dateCand);
        }

        if (ConversationUtils.isValidValue(timeCand) && state.getTime() == null) {
            if (ConversationUtils.isValidTimeFormat(timeCand)) {
                if (appointmentValidator.isTimeInSlot(timeCand, state.getDepartment(), state.getDate())) {
                    state.setTime(timeCand);
                } else {
                    status = "TIME_INVALID";
                }
            }
        }

        return status;
    }

    private void forceAcceptForField(SessionState state, String cleanText, String rawTranscription,
                                     String nameCand, String deptCand, String dateCand, String timeCand) {
        String field = state.getLastAskedField();
        if (field == null) return;
        
        switch (field) {
            case "name" -> {
                if (state.getPatientName() == null && ConversationUtils.isValidName(cleanText)) {
                    state.setPatientName(cleanText);
                    System.out.println("[LOG] Force-accepted name: " + cleanText);
                }
            }
            case "department" -> {
                if (state.getDepartment() == null) {
                    String dept = ConversationUtils.isValidValue(deptCand) ? deptCand : cleanText;
                    if (ConversationUtils.isValidValue(dept)) {
                        state.setDepartment(dept);
                        System.out.println("[LOG] Force-accepted department: " + dept);
                    }
                }
            }
            case "date" -> {
                if (state.getDate() == null) {
                    String date = ConversationUtils.isValidValue(dateCand) ? dateCand : cleanText;
                    if (ConversationUtils.isValidValue(date) && !appointmentValidator.isPastDate(date)) {
                        state.setDate(date);
                        System.out.println("[LOG] Force-accepted date: " + date);
                    }
                }
            }
            case "time" -> {
                if (state.getTime() == null) {
                    String t = ConversationUtils.isValidValue(timeCand) ? timeCand : rawTranscription.trim();
                    if (t.matches(".*\\d.*")) {
                        state.setTime(t);
                        System.out.println("[LOG] Force-accepted time: " + t);
                    }
                }
            }
        }
    }
}
