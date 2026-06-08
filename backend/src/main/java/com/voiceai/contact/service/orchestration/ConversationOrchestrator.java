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
import com.voiceai.contact.service.speech.SpeechTextFormatter;
import com.voiceai.contact.service.session.GreetingService;
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
    private final GreetingService greetingService;
    private final SpeechTextFormatter speechTextFormatter;

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
            NameParserService nameParserService,
            GreetingService greetingService,
            SpeechTextFormatter speechTextFormatter) {
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
        this.greetingService = greetingService;
        this.speechTextFormatter = speechTextFormatter;
    }

    public VoiceResponse processVoice(MultipartFile audio, String sessionId) throws Exception {
        long requestStart = System.currentTimeMillis();
        System.out.println("[PERF] Audio upload received at " + requestStart);

        long sttDuration = 0;
        long ruleDuration = 0;
        long llmDuration = 0;
        long nlgDuration = 0;
        long ttsDuration = 0;

        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        if (sarvamApiKey == null || sarvamApiKey.isEmpty() || groqApiKey == null || groqApiKey.isEmpty()) {
            throw new Exception("API keys are missing in the environment configuration.");
        }

        // 1. Transcription (STT)
        System.out.println("[PERF] Sarvam STT request started");
        long sttStart = System.currentTimeMillis();
        String transcription;
        try {
            transcription = speechToTextService.transcribeAudio(audio);
        } catch (Exception e) {
            System.err.println("Failed STT: " + e.getMessage());
            transcription = "नमस्ते, यह एक टेस्ट है।";
        }
        sttDuration = System.currentTimeMillis() - sttStart;
        System.out.println("[PERF] Sarvam STT response received in " + sttDuration + " ms");

        // Normalize transcription if present
        if (transcription != null) {
            transcription = ConversationUtils.normalizeTranscription(transcription);
        }

        // 2. Greeting check - must run first on new sessions to prevent empty audio bypassing the welcome greeting
        if (!state.isGreetingDone()) {
            System.out.println("[NEW SESSION DETECTED]");
            state.setGreetingDone(true);
            state.appendMessage("user", transcription != null ? transcription : "");
            
            // Try to extract department deterministically from the first utterance
            String initialDept = (transcription != null && !transcription.trim().isEmpty()) 
                    ? departmentParserService.parseDepartment(transcription) 
                    : null;
            
            if (initialDept != null) {
                state.setDepartment(initialDept);
                state.setStage(ConversationStage.COLLECT_NAME);
                state.setLastAskedField("name");
                String promptMsg = "कृपया अपना नाम बताइए।";
                state.appendMessage("assistant", promptMsg);
                
                String formattedPrompt = speechTextFormatter.formatForHindiTts(promptMsg);
                System.out.println("[PERF] Sarvam TTS request started");
                long ttsStart = System.currentTimeMillis();
                String audioBase64 = textToSpeechService.synthesizeSpeech(formattedPrompt);
                ttsDuration = System.currentTimeMillis() - ttsStart;
                System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
                
                long totalDuration = System.currentTimeMillis() - requestStart;
                System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
                
                System.out.println("[PERF] STT = " + sttDuration + " ms");
                System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
                System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
                System.out.println("[PERF] TTS = " + ttsDuration + " ms");
                System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
                
                return new VoiceResponse(transcription, promptMsg, audioBase64, false, state.getSessionId());
            }

            // Normal welcome greeting if no department provided
            state.setStage(ConversationStage.COLLECT_NAME);
            state.setLastAskedField("name");
            
            String greetMsg = greetingService.generateGreeting();
            System.out.println("[GREETING GENERATED]");
            
            state.appendMessage("assistant", greetMsg);
            
            String formattedGreet = speechTextFormatter.formatForHindiTts(greetMsg);
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedGreet);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            System.out.println("[GREETING TTS GENERATED]");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse(transcription, greetMsg, audioBase64, false, state.getSessionId());
        }

        // 3. Transcription empty check for subsequent turns
        if (transcription == null || transcription.trim().isEmpty()) {
            String fallbackMsg = "मुझे आपकी आवाज़ सुनाई नहीं दी। कृपया फिर से प्रयास करें।";
            String formattedFallback = speechTextFormatter.formatForHindiTts(fallbackMsg);
            
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedFallback);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse("", fallbackMsg, audioBase64, false, state.getSessionId());
        }

        // 4. Normalization and basic setup
        String cleanText = transcription.replaceAll("[।.,!?\\s]+", "").trim();
        String cTextCheck = transcription.toLowerCase().trim();
        boolean hasNegation = cTextCheck.contains("नहीं") || cTextCheck.contains("मत") || cTextCheck.contains("ना ");

        // 5. Input validation
        if (!inputValidatorService.isValidInput(transcription, state.getLastAskedField())) {
            String errorMsg = "माफ़ कीजिए, मुझे समझ नहीं आया। क्या आप फिर से बता सकते हैं?";
            String formattedError = speechTextFormatter.formatForHindiTts(errorMsg);
            state.appendMessage("assistant", errorMsg);
            
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedError);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse(transcription, errorMsg, audioBase64, false, state.getSessionId());
        }

        state.appendMessage("user", transcription);

        // 6. Deterministic rule-based extraction (Rule Engine Fast Path)
        System.out.println("[PERF] Rule engine execution started");
        long ruleStart = System.currentTimeMillis();
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
        } else if (state.getStage() == ConversationStage.CONFIRMATION) {
            // Confirmation stage bypasses LLM
            ruleSuccess = true;
        } else if (state.getStage() == ConversationStage.POST_CONFIRM) {
            // Post confirmation bypasses LLM
            ruleSuccess = true;
        }
        ruleDuration = System.currentTimeMillis() - ruleStart;
        System.out.println("[PERF] Rule engine execution completed in " + ruleDuration + " ms");

        LlmExtractionResponse extracted = new LlmExtractionResponse();
        extracted.setIntent(ruleIntent);
        
        // 7. Hybrid AI Strategy - Fallback to Groq only if deterministic rules are insufficient
        if (!ruleSuccess) {
            System.out.println("[LOG] Rule engine failed to parse expected field or intent. Invoking Groq extraction...");
            System.out.println("[PERF] Intent and entity extraction started");
            long llmStart = System.currentTimeMillis();
            String dateContext = dateNormalizerService.getDateContext(transcription);
            extracted = entityExtractionService.extractEntities(transcription, dateContext, state);
            if (extracted.getIntent() == null || extracted.getIntent().equals("UNCLEAR")) {
                extracted.setIntent(ruleIntent);
            }
            llmDuration = System.currentTimeMillis() - llmStart;
            System.out.println("[PERF] Intent and entity extraction completed in " + llmDuration + " ms");
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
            
            String formattedOos = speechTextFormatter.formatForHindiTts(oosMsg);
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedOos);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse(transcription, oosMsg, audioBase64, false, state.getSessionId());
        }

        // 8. Process intents and slot filling
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
            
            String formattedResch = speechTextFormatter.formatForHindiTts(reschMsg);
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedResch);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse(transcription, reschMsg, audioBase64, false, state.getSessionId());
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
            
            String formattedCancel = speechTextFormatter.formatForHindiTts(cancelMsg);
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedCancel);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse(transcription, cancelMsg, audioBase64, false, state.getSessionId());
        }

        // End trigger
        if ("END".equals(intent) && state.getStage() != ConversationStage.CONFIRMATION) {
            String endMsg = "धन्यवाद। आपका दिन शुभ हो।";
            state.appendMessage("assistant", endMsg);
            state.setStage(ConversationStage.COMPLETED);
            
            String formattedEnd = speechTextFormatter.formatForHindiTts(endMsg);
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedEnd);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse(transcription, endMsg, audioBase64, true, state.getSessionId());
        }

        // Query resolutions in post-confirmation
        if (state.isConfirmed() && state.getStage() == ConversationStage.POST_CONFIRM) {
            String answer = responseBuilder.resolvePostConfirmQuery(
                    cTextCheck, state.getDate(), state.getTime(), state.getDepartment(), state.getAssignedDoctor());
            if (answer != null) {
                state.appendMessage("assistant", answer);
                
                String formattedAnswer = speechTextFormatter.formatForHindiTts(answer);
                System.out.println("[PERF] Sarvam TTS request started");
                long ttsStart = System.currentTimeMillis();
                String audioBase64 = textToSpeechService.synthesizeSpeech(formattedAnswer);
                ttsDuration = System.currentTimeMillis() - ttsStart;
                System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
                
                long totalDuration = System.currentTimeMillis() - requestStart;
                System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
                
                System.out.println("[PERF] STT = " + sttDuration + " ms");
                System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
                System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
                System.out.println("[PERF] TTS = " + ttsDuration + " ms");
                System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
                
                return new VoiceResponse(transcription, answer, audioBase64, false, state.getSessionId());
            }
        }

        if ("ASK_QUERY".equals(intent) && state.isConfirmed()) {
            long docMatchStart = System.currentTimeMillis();
            String dayVal = doctorAssignmentService.getDayOfWeek(state.getDate());
            System.out.println("[PERF] Doctor matching completed in " + (System.currentTimeMillis() - docMatchStart) + " ms");
            
            String dH = responseBuilder.toHindiDay(dayVal);
            String tN = responseBuilder.toNaturalTime(state.getTime());
            String dN = responseBuilder.formatDoctorName(state.getAssignedDoctor());
            String aiResponse = "आपका अपॉइंटमेंट " + dH + " को " + tN + " " + dN + " के साथ है।";
            state.appendMessage("assistant", aiResponse);
            
            String formattedQueryResponse = speechTextFormatter.formatForHindiTts(aiResponse);
            System.out.println("[PERF] Sarvam TTS request started");
            long ttsStart = System.currentTimeMillis();
            String audioBase64 = textToSpeechService.synthesizeSpeech(formattedQueryResponse);
            ttsDuration = System.currentTimeMillis() - ttsStart;
            System.out.println("[PERF] Sarvam TTS response received in " + ttsDuration + " ms");
            
            long totalDuration = System.currentTimeMillis() - requestStart;
            System.out.println("[PERF] Total request duration = " + totalDuration + " ms");
            
            System.out.println("[PERF] STT = " + sttDuration + " ms");
            System.out.println("[PERF] RULE_ENGINE = " + ruleDuration + " ms");
            System.out.println("[PERF] LLM = " + (llmDuration + nlgDuration) + " ms");
            System.out.println("[PERF] TTS = " + ttsDuration + " ms");
            System.out.println("[PERF] TOTAL = " + totalDuration + " ms");
            
            return new VoiceResponse(transcription, aiResponse, audioBase64, false, state.getSessionId());
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
                    long docMatchStart = System.currentTimeMillis();
                    state.setAssignedDoctor(doctorAssignmentService.matchDoctor(state.getDepartment(), state.getDate()));
                    System.out.println("[PERF] Doctor matching completed in " + (System.currentTimeMillis() - docMatchStart) + " ms");
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
                long docMatchStart = System.currentTimeMillis();
                String dayVal = doctorAssignmentService.getDayOfWeek(state.getDate());
                System.out.println("[PERF] Doctor matching completed in " + (System.currentTimeMillis() - docMatchStart) + " ms");
                
                String dH = responseBuilder.toHindiDay(dayVal);
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

        // 10. Generate final response
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
                long docMatchStart = System.currentTimeMillis();
                String dayVal = doctorAssignmentService.getDayOfWeek(state.getDate());
                System.out.println("[PERF] Doctor matching completed in " + (System.currentTimeMillis() - docMatchStart) + " ms");
                
                String dayH  = responseBuilder.toHindiDay(dayVal);
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

                System.out.println("[PERF] Response generation started");
                long nlgStart = System.currentTimeMillis();
                aiResponse = responseGenerationService.generateResponse(sb.toString());
                nlgDuration = System.currentTimeMillis() - nlgStart;
                System.out.println("[PERF] Response generation completed in " + nlgDuration + " ms");
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
