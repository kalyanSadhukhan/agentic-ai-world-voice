package com.voiceai.contact.service.orchestration;

import com.voiceai.contact.dto.LlmExtractionResponse;
import com.voiceai.contact.dto.VoiceResponse;
import com.voiceai.contact.service.DateNormalizerService;
import com.voiceai.contact.service.InputValidatorService;
import com.voiceai.contact.service.booking.*;
import com.voiceai.contact.service.llm.*;
import com.voiceai.contact.service.parser.*;
import com.voiceai.contact.service.session.ConversationStage;
import com.voiceai.contact.service.session.SessionManagerService;
import com.voiceai.contact.service.session.SessionState;
import com.voiceai.contact.service.speech.SpeechToTextService;
import com.voiceai.contact.service.speech.TextToSpeechService;
import com.voiceai.contact.service.util.ConversationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConversationOrchestrator {

    @Value("${SARVAM_API_KEY:}")
    private String sarvamApiKey;

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    private final SpeechToTextService speechToTextService;
    private final TextToSpeechService textToSpeechService;
    private final EntityExtractionService entityExtractionService;
    private final IntentClassifierService intentClassifierService;
    private final ResponseGenerationService responseGenerationService;
    private final SlotFillingService slotFillingService;
    private final DoctorAssignmentService doctorAssignmentService;
    private final AppointmentValidator appointmentValidator;
    private final AppointmentResponseBuilder responseBuilder;
    private final SessionManagerService sessionManagerService;
    private final InputValidatorService inputValidatorService;
    private final DateNormalizerService dateNormalizerService;

    private final DepartmentParserService departmentParserService;
    private final DateParserService dateParserService;
    private final TimeParserService timeParserService;
    private final NameParserService nameParserService;

    public ConversationOrchestrator(
            SpeechToTextService speechToTextService,
            TextToSpeechService textToSpeechService,
            EntityExtractionService entityExtractionService,
            IntentClassifierService intentClassifierService,
            ResponseGenerationService responseGenerationService,
            SlotFillingService slotFillingService,
            DoctorAssignmentService doctorAssignmentService,
            AppointmentValidator appointmentValidator,
            AppointmentResponseBuilder responseBuilder,
            SessionManagerService sessionManagerService,
            InputValidatorService inputValidatorService,
            DateNormalizerService dateNormalizerService,
            DepartmentParserService departmentParserService,
            DateParserService dateParserService,
            TimeParserService timeParserService,
            NameParserService nameParserService) {
        this.speechToTextService = speechToTextService;
        this.textToSpeechService = textToSpeechService;
        this.entityExtractionService = entityExtractionService;
        this.intentClassifierService = intentClassifierService;
        this.responseGenerationService = responseGenerationService;
        this.slotFillingService = slotFillingService;
        this.doctorAssignmentService = doctorAssignmentService;
        this.appointmentValidator = appointmentValidator;
        this.responseBuilder = responseBuilder;
        this.sessionManagerService = sessionManagerService;
        this.inputValidatorService = inputValidatorService;
        this.dateNormalizerService = dateNormalizerService;
        this.departmentParserService = departmentParserService;
        this.dateParserService = dateParserService;
        this.timeParserService = timeParserService;
        this.nameParserService = nameParserService;
    }

    public VoiceResponse processVoice(MultipartFile audio, String sessionId) throws Exception {
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        if (sarvamApiKey == null || sarvamApiKey.isEmpty() || groqApiKey == null || groqApiKey.isEmpty()) {
            throw new Exception("API keys are missing in the environment configuration.");
        }

        // 1. Transcription (STT)
        String transcription;
        try {
            transcription = speechToTextService.transcribeAudio(audio);
        } catch (Exception e) {
            System.err.println("Failed STT: " + e.getMessage());
            transcription = "नमस्ते, यह एक टेस्ट है।";
        }

        if (transcription == null || transcription.trim().isEmpty()) {
            String fallbackMsg = "मुझे आपकी आवाज़ सुनाई नहीं दी। कृपया फिर से प्रयास करें।";
            return new VoiceResponse("", fallbackMsg, textToSpeechService.synthesizeSpeech(fallbackMsg), false, state.getSessionId());
        }

        // 2. Normalization
        transcription = ConversationUtils.normalizeTranscription(transcription);
        String cleanText = transcription.replaceAll("[।.,!?\\s]+", "").trim();
        String cTextCheck = transcription.toLowerCase().trim();
        boolean hasNegation = cTextCheck.contains("नहीं") || cTextCheck.contains("मत") || cTextCheck.contains("ना ");

        // 3. Agent-Initiated Greeting (First turn check)
        if (!state.isGreetingDone()) {
            state.setGreetingDone(true);
            state.appendMessage("user", transcription);
            
            // Try to extract department deterministically from the first utterance
            String initialDept = departmentParserService.parseDepartment(transcription);
            if (initialDept != null) {
                state.setDepartment(initialDept);
                state.setStage(ConversationStage.COLLECT_NAME);
                state.setLastAskedField("name");
                String promptMsg = "Kripya apna naam batayiye.";
                state.appendMessage("assistant", promptMsg);
                return new VoiceResponse(transcription, promptMsg, textToSpeechService.synthesizeSpeech(promptMsg), false, state.getSessionId());
            }

            // Normal welcome greeting if no department provided
            state.setStage(ConversationStage.COLLECT_NAME);
            state.setLastAskedField("name");
            String greetMsg = "Welcome to VoiceAiAgentPro. Main aapka appointment assistant hoon. Kripya bataiye main aapki kaise sahayata kar sakta hoon.";
            state.appendMessage("assistant", greetMsg);
            return new VoiceResponse(transcription, greetMsg, textToSpeechService.synthesizeSpeech(greetMsg), false, state.getSessionId());
        }

        // 4. Input validation
        if (!inputValidatorService.isValidInput(transcription, state.getLastAskedField())) {
            String errorMsg = "माफ़ कीजिए, मुझे समझ नहीं आया। क्या आप फिर से बता सकते हैं?";
            state.appendMessage("assistant", errorMsg);
            return new VoiceResponse(transcription, errorMsg, textToSpeechService.synthesizeSpeech(errorMsg), false, state.getSessionId());
        }

        state.appendMessage("user", transcription);

        // 5. Deterministic rule-based extraction (Rule Engine Fast Path)
        String detName = null;
        String detDept = departmentParserService.parseDepartment(transcription);
        String detDate = dateParserService.parseDate(transcription);
        String detTime = timeParserService.parseTime(transcription);

        if ("name".equals(state.getLastAskedField())) {
            detName = nameParserService.parseName(transcription);
        }

        String ruleIntent = intentClassifierService.classifyIntent(transcription, state, "UNCLEAR");

        boolean ruleSuccess = false;
        if ("name".equals(state.getLastAskedField()) && detName != null) {
            ruleSuccess = true;
        } else if ("department".equals(state.getLastAskedField()) && detDept != null) {
            ruleSuccess = true;
        } else if ("date".equals(state.getLastAskedField()) && detDate != null) {
            ruleSuccess = true;
        } else if ("time".equals(state.getLastAskedField()) && detTime != null) {
            ruleSuccess = true;
        } else if (state.getStage() == ConversationStage.CONFIRMATION && ("CONTINUE".equals(ruleIntent) || "CANCEL".equals(ruleIntent) || "RESCHEDULE".equals(ruleIntent))) {
            ruleSuccess = true;
        } else if (state.getStage() == ConversationStage.POST_CONFIRM && ("RESCHEDULE".equals(ruleIntent) || "END".equals(ruleIntent) || "ASK_QUERY".equals(ruleIntent))) {
            ruleSuccess = true;
        }

        LlmExtractionResponse extracted = new LlmExtractionResponse();
        extracted.setIntent(ruleIntent);
        
        // 6. Hybrid AI Strategy - Fallback to Groq only if deterministic rules are insufficient
        if (!ruleSuccess) {
            System.out.println("[LOG] Rule engine failed to parse expected field or intent. Invoking Groq extraction...");
            String dateContext = dateNormalizerService.getDateContext(transcription);
            extracted = entityExtractionService.extractEntities(transcription, dateContext, state);
            if (extracted.getIntent() == null || extracted.getIntent().equals("UNCLEAR")) {
                extracted.setIntent(ruleIntent);
            }
        } else {
            System.out.println("[LOG] Rule engine match successful. Bypassing Groq extraction.");
            extracted.setName(detName);
            extracted.setDepartment(detDept);
            extracted.setDate(detDate);
            extracted.setTime(detTime);
        }

        // Out of Scope Handling
        int wordCount = transcription.split("\\s+").length;
        if (wordCount <= 3 && state.getLastAskedField() != null) {
            extracted.setIsOutOfScope(false);
        }
        if (extracted.getIsOutOfScope() != null && extracted.getIsOutOfScope()
                && (extracted.getIsQuerying() == null || !extracted.getIsQuerying())) {
            String oosMsg = "माफ़ कीजिए, मैं सिर्फ अपॉइंटमेंट बुकिंग में आपकी मदद कर सकती हूँ।";
            state.appendMessage("assistant", oosMsg);
            return new VoiceResponse(transcription, oosMsg, textToSpeechService.synthesizeSpeech(oosMsg), false, state.getSessionId());
        }

        // 7. Process intents and slot filling
        String intent = intentClassifierService.classifyIntent(transcription, state, extracted.getIntent());
        System.out.println("[LOG] Final Intent: " + intent);

        // Reschedule trigger
        if ("RESCHEDULE".equals(intent) && (state.isConfirmed() || state.getStage() == ConversationStage.POST_CONFIRM || state.getStage() == ConversationStage.CONFIRMATION)) {
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setStage(ConversationStage.RESCHEDULE);
            state.setLastAskedField("date");
            state.resetRepeatCount();
            String reschMsg = "ठीक है, आप किस दिन अपॉइंटमेंट रखना चाहेंगे?";
            state.appendMessage("assistant", reschMsg);
            return new VoiceResponse(transcription, reschMsg, textToSpeechService.synthesizeSpeech(reschMsg), false, state.getSessionId());
        }

        // Cancel trigger
        if ("CANCEL".equals(intent)) {
            state.setPatientName(null);
            state.setDepartment(null);
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setStage(ConversationStage.COLLECT_NAME);
            state.setLastAskedField("name");
            String cancelMsg = "ठीक है, अपॉइंटमेंट कैंसिल कर दी गई है।";
            state.appendMessage("assistant", cancelMsg);
            return new VoiceResponse(transcription, cancelMsg, textToSpeechService.synthesizeSpeech(cancelMsg), false, state.getSessionId());
        }

        // End trigger
        if ("END".equals(intent) && state.getStage() != ConversationStage.CONFIRMATION) {
            String endMsg = "धन्यवाद। आपका दिन शुभ हो।";
            state.appendMessage("assistant", endMsg);
            state.setStage(ConversationStage.COMPLETED);
            return new VoiceResponse(transcription, endMsg, textToSpeechService.synthesizeSpeech(endMsg), true, state.getSessionId());
        }

        // Query resolutions in post-confirmation
        if (state.isConfirmed() && state.getStage() == ConversationStage.POST_CONFIRM) {
            String answer = responseBuilder.resolvePostConfirmQuery(
                    cTextCheck, state.getDate(), state.getTime(), state.getDepartment(), state.getAssignedDoctor());
            if (answer != null) {
                state.appendMessage("assistant", answer);
                return new VoiceResponse(transcription, answer, textToSpeechService.synthesizeSpeech(answer), false, state.getSessionId());
            }
        }

        if ("ASK_QUERY".equals(intent) && state.isConfirmed()) {
            String dH = responseBuilder.toHindiDay(doctorAssignmentService.getDayOfWeek(state.getDate()));
            String tN = responseBuilder.toNaturalTime(state.getTime());
            String dN = responseBuilder.formatDoctorName(state.getAssignedDoctor());
            String aiResponse = "आपका अपॉइंटमेंट " + dH + " को " + tN + " " + dN + " के साथ है।";
            state.appendMessage("assistant", aiResponse);
            return new VoiceResponse(transcription, aiResponse, textToSpeechService.synthesizeSpeech(aiResponse), false, state.getSessionId());
        }

        // Slot filling & validation execution
        String fillStatus = "SUCCESS";
        if (!state.isConfirmed() && state.getStage() != ConversationStage.CONFIRMATION) {
            String candName = ConversationUtils.isValidValue(extracted.getName()) ? extracted.getName() : detName;
            String candDept = ConversationUtils.isValidValue(extracted.getDepartment()) ? extracted.getDepartment() : detDept;
            String candDate = ConversationUtils.isValidValue(extracted.getDate()) ? extracted.getDate() : detDate;
            String candTime = ConversationUtils.isValidValue(extracted.getTime()) ? extracted.getTime() : detTime;

            fillStatus = slotFillingService.fillSlots(state, cleanText, transcription, candName, candDept, candDate, candTime);
        }

        // Check if all slots filled to enter confirmation
        if (state.getPatientName() != null && state.getDepartment() != null && state.getDate() != null && state.getTime() != null && !state.isConfirmed()) {
            if (state.getStage() == ConversationStage.COLLECT_NAME || state.getStage() == ConversationStage.COLLECT_DEPARTMENT
                    || state.getStage() == ConversationStage.COLLECT_DATE || state.getStage() == ConversationStage.COLLECT_TIME
                    || state.getStage() == ConversationStage.RESCHEDULE || state.getStage() == ConversationStage.WELCOME) {
                state.setStage(ConversationStage.CONFIRMATION);
                if (state.getAssignedDoctor() == null) {
                    state.setAssignedDoctor(doctorAssignmentService.matchDoctor(state.getDepartment(), state.getDate()));
                }
            }
        }

        // 8. Determine Next Stage & Response
        String nextAction = "";
        String systemData = "";
        boolean endCall = false;

        if ("MULTI_DATE".equals(fillStatus)) {
            nextAction = "ASK_DATE";
            state.setStage(ConversationStage.COLLECT_DATE);
            state.setLastAskedField("date");
        } else if ("PAST_DATE".equals(fillStatus)) {
            nextAction = "ASK_DATE";
            state.setStage(ConversationStage.COLLECT_DATE);
            state.setLastAskedField("date");
            systemData = "यह तारीख़ बीत चुकी है। कृपया भविष्य की तारीख़ बताएं।";
        } else if ("RESCHEDULE".equals(intent)) {
            nextAction = "ASK_DATE";
            state.setStage(ConversationStage.COLLECT_DATE);
            state.setLastAskedField("date");
            systemData = "ठीक है, नई तारीख़ बताइए।";
        } else if ("SOFT_END".equals(intent)) {
            if (state.getStage() == ConversationStage.CONFIRMATION) {
                nextAction = "CONFIRM_DETAILS";
                systemData = responseBuilder.buildBookingSummary(
                        state.getPatientName(), state.getDepartment(), state.getDate(), state.getTime(), state.getAssignedDoctor());
            } else {
                nextAction = "END";
                state.setStage(ConversationStage.COMPLETED);
                endCall = true;
            }
        } else if ("UNCLEAR".equals(intent) && state.getStage() != ConversationStage.CONFIRMATION) {
            if (state.getLastAskedField() != null) {
                nextAction = "ASK_" + state.getLastAskedField().toUpperCase();
            } else {
                nextAction = "INFORM";
                systemData = "माफ़ कीजिए, मुझे यह स्पष्ट नहीं समझ आया। कृपया फिर से बताएं।";
            }
        } else if ("ASK_QUERY".equals(intent) || (extracted.getIsQuerying() != null && extracted.getIsQuerying())) {
            nextAction = "INFORM";
            if (state.isConfirmed() || state.getStage() == ConversationStage.CONFIRMATION) {
                String dH = responseBuilder.toHindiDay(doctorAssignmentService.getDayOfWeek(state.getDate()));
                String tN = responseBuilder.toNaturalTime(state.getTime());
                String dN = responseBuilder.formatDoctorName(state.getAssignedDoctor());
                systemData = "आपका अपॉइंटमेंट " + dH + " को " + tN + " " + dN + " के साथ है।";
            } else {
                systemData = responseBuilder.buildAvailabilityResponse(state.getDepartment(), state.getDate());
            }
        } else if (state.getStage() == ConversationStage.COLLECT_NAME || state.getStage() == ConversationStage.COLLECT_DEPARTMENT
                || state.getStage() == ConversationStage.COLLECT_DATE || state.getStage() == ConversationStage.COLLECT_TIME
                || state.getStage() == ConversationStage.RESCHEDULE) {
            if (state.getPatientName() == null) {
                nextAction = "ASK_NAME";
                state.setStage(ConversationStage.COLLECT_NAME);
                state.setLastAskedField("name");
                state.incrementRepeatCount();
            } else if (state.getDepartment() == null) {
                nextAction = "ASK_DEPARTMENT";
                state.setStage(ConversationStage.COLLECT_DEPARTMENT);
                state.setLastAskedField("department");
                state.incrementRepeatCount();
            } else if (state.getDate() == null) {
                nextAction = "ASK_DATE";
                state.setStage(ConversationStage.COLLECT_DATE);
                state.setLastAskedField("date");
                state.incrementRepeatCount();
            } else if (state.getTime() == null) {
                nextAction = "ASK_TIME";
                state.setStage(ConversationStage.COLLECT_TIME);
                state.setLastAskedField("time");
                state.incrementRepeatCount();
            }
        } else if (state.getStage() == ConversationStage.CONFIRMATION) {
            boolean isNegativeConfirm = hasNegation && (cTextCheck.contains("नहीं") || cTextCheck.contains("ठीक नहीं") || cTextCheck.contains("कन्फर्म नहीं"));
            boolean isPositive = "CONTINUE".equals(intent) || (extracted.getIsConfirming() != null && extracted.getIsConfirming());

            if (isNegativeConfirm) {
                state.setTime(null);
                state.setAssignedDoctor(null);
                state.setStage(ConversationStage.COLLECT_TIME);
                state.setLastAskedField("time");
                state.resetRepeatCount();
                nextAction = "NEG_CONFIRM";
            } else if (isPositive) {
                state.setConfirmed(true);
                state.setStage(ConversationStage.POST_CONFIRM);
                state.setLastAskedField(null);
                nextAction = "POST_CONFIRM";
            } else {
                nextAction = "CONFIRM_DETAILS";
                state.setLastAskedField("confirmation");
                systemData = responseBuilder.buildBookingSummary(
                        state.getPatientName(), state.getDepartment(), state.getDate(), state.getTime(), state.getAssignedDoctor());
            }
        } else if (state.getStage() == ConversationStage.POST_CONFIRM) {
            nextAction = "POST_CONFIRM";
        }

        // 9. Generate final response
        String aiResponse;
        switch (nextAction) {
            case "ASK_NAME"         -> aiResponse = "कृपया अपना नाम बताइए।";
            case "ASK_DEPARTMENT"   -> aiResponse = state.getRepeatCount() <= 1
                ? "किस विभाग में दिखाना है?"
                : "कृपया बताएं आपको किस तरह के डॉक्टर को दिखाना है, जैसे हड्डी, दिल, नसें या त्वचा।";
            case "ASK_DATE"         -> aiResponse = state.getRepeatCount() <= 1
                ? "किस दिन आना चाहेंगे?"
                : (state.getRepeatCount() == 2
                    ? "माफ़ कीजिए, तारीख़ स्पष्ट नहीं हुई। कोई एक दिन बताएं जैसे सोमवार या मंगलवार।"
                    : "आपने जो तारीख़ बताई वह स्पष्ट नहीं है। कृपया सिर्फ एक दिन बताएं।");
            case "ASK_TIME"         -> aiResponse = state.getRepeatCount() <= 1
                ? "कृपया समय बता दीजिए।"
                : (state.getRepeatCount() == 2
                    ? "माफ़ कीजिए, समय स्पष्ट नहीं हुआ। कृपया बताएं जैसे सुबह दस बजे या दोपहर बारह बजे।"
                    : "कृपया सटीक समय बताएं जैसे दोपहर बारह बजे या शाम चार बजे।");
            case "NEG_CONFIRM"      -> aiResponse = "ठीक है, कृपया नया समय बताइए।";
            case "POST_CONFIRM"     -> aiResponse = "आपकी अपॉइंटमेंट कन्फर्म हो गई है। क्या आपको और मदद चाहिए?";
            case "CANCEL"           -> aiResponse = "ठीक है, अपॉइंटमेंट कैंसिल कर दी गई है।";
            case "END"              -> aiResponse = "धन्यवाद। आपका दिन शुभ हो।";
            case "MULTI_DATE"        -> aiResponse = "आपने दो तारीखें बताई हैं। कृपया एक तारीख़ चुनें।";
            case "INFORM"           -> aiResponse = systemData;
            case "CONFIRM_DETAILS"  -> {
                String dayH  = responseBuilder.toHindiDay(doctorAssignmentService.getDayOfWeek(state.getDate()));
                String dateH = responseBuilder.toHindiDate(state.getDate());
                String timeN = responseBuilder.toNaturalTime(state.getTime());
                String docN  = responseBuilder.formatDoctorName(state.getAssignedDoctor());
                String dept  = responseBuilder.formatDeptName(state.getDepartment());
                aiResponse = "आपका अपॉइंटमेंट " + dayH + ", " + dateH + " को "
                    + timeN + " " + docN + " (" + dept + " विभाग) के साथ है, क्या मैं इसे कन्फर्म कर दूँ?";
            }
            default -> {
                StringBuilder sb = new StringBuilder();
                sb.append("USER STATE:\n");
                sb.append("Name: ").append(state.getPatientName() != null ? state.getPatientName() : "Not provided").append("\n");
                sb.append("Department: ").append(state.getDepartment() != null ? state.getDepartment() : "Not provided").append("\n");
                sb.append("Date: ").append(state.getDate() != null ? state.getDate() : "Not provided");
                String day = doctorAssignmentService.getDayOfWeek(state.getDate());
                if (day != null) sb.append(" (").append(day).append(")");
                sb.append("\n");
                sb.append("Time: ").append(state.getTime() != null ? state.getTime() : "Not provided").append("\n\n");
                
                sb.append("SYSTEM DATA:\n");
                sb.append(systemData != null && !systemData.isEmpty() ? systemData : "None").append("\n\n");

                sb.append("NEXT ACTION:\n");
                sb.append(nextAction).append("\n");

                aiResponse = responseGenerationService.generateResponse(sb.toString());
            }
        }

        state.appendMessage("assistant", aiResponse);
        String audioBase64;
        try {
            audioBase64 = textToSpeechService.synthesizeSpeech(aiResponse);
        } catch (Exception e) {
            System.err.println("Failed TTS: " + e.getMessage());
            audioBase64 = "";
        }

        return new VoiceResponse(transcription, aiResponse, audioBase64, endCall, state.getSessionId());
    }
}
