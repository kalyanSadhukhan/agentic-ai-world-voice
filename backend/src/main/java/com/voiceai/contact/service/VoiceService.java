package com.voiceai.contact.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.voiceai.contact.dto.VoiceResponse;
import com.voiceai.contact.dto.LlmExtractionResponse;
import com.voiceai.contact.model.SessionState;
import com.voiceai.contact.config.ClinicConfig;

import java.util.*;

@Service
public class VoiceService {

    @Value("${SARVAM_API_KEY:}")
    private String sarvamApiKey;

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final DateNormalizerService dateNormalizerService;
    private final SessionManagerService sessionManagerService;
    private final InputValidatorService inputValidatorService;

    public VoiceService(DateNormalizerService dateNormalizerService, SessionManagerService sessionManagerService, InputValidatorService inputValidatorService) {
        this.dateNormalizerService = dateNormalizerService;
        this.sessionManagerService = sessionManagerService;
        this.inputValidatorService = inputValidatorService;
    }

    public VoiceResponse processVoice(MultipartFile audio, String sessionId) throws Exception {
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        if (sarvamApiKey == null || sarvamApiKey.isEmpty() || groqApiKey == null || groqApiKey.isEmpty()) {
            throw new Exception("API keys are missing in the environment configuration.");
        }

        // 1. STT (Speech to Text) via Sarvam
        System.out.println("Transcribing audio...");
        String transcription;
        try {
            transcription = transcribeAudio(audio);
            System.out.println("Transcription: " + transcription);
        } catch (Exception e) {
            System.err.println("Failed STT: " + e.getMessage());
            transcription = "नमस्ते, यह एक टेस्ट है।"; // Fallback text
        }

        if (transcription == null || transcription.trim().isEmpty()) {
            System.out.println("No speech detected. Returning fallback audio directly and skipping LLM.");
            String fallbackMsg = "मुझे आपकी आवाज़ सुनाई नहीं दी। कृपया फिर से प्रयास करें।";
            String audioBase64 = synthesizeSpeech(fallbackMsg);
            // Return empty transcription so the frontend doesn't show the error phrase as
            // the user's message
            return new VoiceResponse("", fallbackMsg, audioBase64, false, state.getSessionId());
        }

        // ─── 1.1  Script normalization (Bengali → canonical terms) ────────────────
        transcription = transcription
                .replace("অর্থোপেডিক", "Orthopedic")
                .replace("অস্থি",      "Orthopedic")
                .replace("ডাক্তার",    "Doctor")
                .replace("অ্যাপয়েন্টমেন্ট", "Appointment")
                .replace("রাহুল",      "राहुल")   // Bengali → Devanagari for common names
                .replace("কাল",       "कल")       // Bengali "tomorrow"
                .replace("আজ",        "आज");       // Bengali "today"
        transcription = normalizeDepartment(transcription);
        System.out.println("[LOG] Normalized input: " + transcription);

        // ─── 1.2  GREETING — fires ONCE before any further processing ────────────
        if (!state.isGreetingDone()) {
            state.setGreetingDone(true);
            state.setLastAskedField("name");
            state.appendMessage("user", transcription);
            String greetMsg = "नमस्ते, मैं क्लिनिक की रिसेप्शनिस्ट हूँ। कृपया अपना नाम बताइए।";
            state.appendMessage("assistant", greetMsg);
            System.out.println("[LOG] GREETING sent.");
            return new VoiceResponse(transcription, greetMsg, synthesizeSpeech(greetMsg), false, state.getSessionId());
        }

        // ─── 1.3  Input validation ────────────────────────────────────────────────
        if (!inputValidatorService.isValidInput(transcription, state.getLastAskedField())) {
            System.out.println("[LOG] Input rejected by validator: " + transcription);
            String errorMsg = "माफ़ कीजिए, मुझे समझ नहीं आया। क्या आप फिर से बता सकते हैं?";
            state.appendMessage("assistant", errorMsg);
            return new VoiceResponse(transcription, errorMsg, synthesizeSpeech(errorMsg), false, state.getSessionId());
        }

        // Add user text to memory
        state.appendMessage("user", transcription);

        // 1.5. NEW: Intent Classification
        String intent = classifyIntent(transcription, state);
        
        // Hard Override for Intents (deterministic keyword rules override LLM)
        String cTextCheck = transcription.toLowerCase().trim();

        // Confirmation override — MUST happen before SOFT_END/END check
        boolean hasNegation = cTextCheck.contains("नहीं") || cTextCheck.contains("मत") || cTextCheck.contains("ना ");
        boolean isConfirmKeyword = !hasNegation && (
                cTextCheck.contains("हाँ") || cTextCheck.contains("हां") || cTextCheck.contains("yes") ||
                cTextCheck.contains("कर दीजिए") || cTextCheck.contains("कर दो") || cTextCheck.contains("बिल्कुल") ||
                cTextCheck.contains("कन्फर्म") || cTextCheck.contains("ठीक है") || cTextCheck.contains("जी") || cTextCheck.contains("sure"));
        // Explicit negative confirmation in CONFIRMATION mode → ask for correction
        boolean isNegativeConfirm = hasNegation && state.getMode() == SessionState.Mode.CONFIRMATION
                && (cTextCheck.contains("नहीं") || cTextCheck.contains("ठीक नहीं") || cTextCheck.contains("कन्फर्म नहीं"));
        if (isConfirmKeyword && state.getMode() == SessionState.Mode.CONFIRMATION) {
            intent = "CONTINUE";
        } else if (cTextCheck.equals("नहीं") || cTextCheck.equals("बस") || cTextCheck.equals("no") ||
                   cTextCheck.contains("धन्यवाद") || cTextCheck.contains("ज़रूरत नहीं") ||
                   cTextCheck.contains("thank you") || cTextCheck.contains("thanks") || cTextCheck.contains("डिस्कनेक्ट")) {
            // END only when NOT in the middle of asking for a slot
            if (state.getLastAskedField() == null || state.getMode() != SessionState.Mode.BOOKING) {
                intent = "END";
            }
        } else if (cTextCheck.contains("कब है") || cTextCheck.contains("टाइम क्या है") ||
                   cTextCheck.contains("समय क्या है") || cTextCheck.contains("कौन सा टाइम") ||
                   cTextCheck.contains("कौन सा समय") || cTextCheck.contains("कब मिलेंगे") ||
                   cTextCheck.contains("कब उपलब्ध") || cTextCheck.contains("available") ||
                   cTextCheck.contains("अवेलेबल") || cTextCheck.contains("उपलब्ध") ||
                   cTextCheck.contains("कब आएं") || cTextCheck.contains("टाइम बताइए")) {
            intent = "ASK_QUERY";
        } else if (cTextCheck.contains("appointment") || cTextCheck.contains("अपॉइंटमेंट")) {
            if (!intent.equals("CANCEL") && state.getMode() == SessionState.Mode.BOOKING && state.getPatientName() == null) {
                intent = "BOOK_APPOINTMENT";
            }
        }
        // Cancellation only if explicitly requested — never infer from short inputs
        if (intent.equals("CANCEL") && !cTextCheck.contains("कैंसिल") && !cTextCheck.contains("cancel")) {
            intent = "UNCLEAR"; // revert false CANCEL classifications
        }

        // ── RESCHEDULE override — MUST come before failsafe PROVIDE_INFO override ──
        boolean isRescheduleKeyword = cTextCheck.contains("रीशेडिउल") || cTextCheck.contains("रिशेडिउल")
            || cTextCheck.contains("रीशिडिउल") || cTextCheck.contains("reschedule")
            || cTextCheck.contains("दूसरे दिन") || cTextCheck.contains("दूसरी तारीख")
            || cTextCheck.contains("दिन बदलना") || cTextCheck.contains("बदलना है");
        if (isRescheduleKeyword) {
            intent = "RESCHEDULE";
        }

        System.out.println("Classified Intent: " + intent);

        // Failsafe Priority Order: slot filling always wins (except for hard-classified intents)
        if (state.getLastAskedField() != null) {
            if (!intent.equals("END") && !intent.equals("CONTINUE") && !intent.equals("ASK_QUERY")
                    && !intent.equals("CANCEL") && !intent.equals("RESCHEDULE")) {
                intent = "PROVIDE_INFO";
            }
        }

        // RESCHEDULE — intercept in POST_CONFIRM immediately, before hardEnd check
        if (intent.equals("RESCHEDULE")
                && (state.isConfirmed() || state.getMode() == SessionState.Mode.POST_CONFIRM)) {
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setMode(SessionState.Mode.RESCHEDULE);
            state.setLastAskedField("date");
            state.resetRepeatCount();
            String reschMsg = "ठीक है, आप किस दिन अपॉइंटमेंट रखना चाहेंगे?";
            state.appendMessage("assistant", reschMsg);
            System.out.println("[LOG] RESCHEDULE triggered from POST_CONFIRM");
            return new VoiceResponse(transcription, reschMsg, synthesizeSpeech(reschMsg), false, state.getSessionId());
        }

        // Hard END — guard: don't fire if reschedule was just handled above
        boolean hardEnd = intent.equals("END")
            || cTextCheck.equals("नहीं") || cTextCheck.equals("नहीं।")
            || cTextCheck.equals("बस") || cTextCheck.equals("बस।")
            || cTextCheck.contains("धन्यवाद")
            || cTextCheck.contains("ठीक है, बस")
            || cTextCheck.contains("thank you") || cTextCheck.contains("thanks")
            || cTextCheck.contains("डिस्कनेक्ट");

        if (hardEnd && state.getMode() != SessionState.Mode.CONFIRMATION) {
            String aiResponse = "धन्यवाद। आपका दिन शुभ हो।";
            state.appendMessage("assistant", aiResponse);
            return new VoiceResponse(transcription, aiResponse, synthesizeSpeech(aiResponse), true, state.getSessionId());
        }

        // POST_CONFIRM: answer slot queries from frozen snapshot — never re-enter flow
        if (state.isConfirmed() && state.getMode() == SessionState.Mode.POST_CONFIRM) {
            String answer = resolvePostConfirmQuery(cTextCheck, state);
            if (answer != null) {
                state.appendMessage("assistant", answer);
                return new VoiceResponse(transcription, answer, synthesizeSpeech(answer), false, state.getSessionId());
            }
        }

        // Confirmed general booking query (e.g. "कब है") — answer from snapshot
        if (intent.equals("ASK_QUERY") && state.isConfirmed()) {
            String dayHindi    = toHindiDay(getDayOfWeek(state.getDate()));
            String timeNatural = toNaturalTime(state.getTime());
            String docName     = formatDoctorName(state.getAssignedDoctor());
            String aiResponse  = "आपका अपॉइंटमेंट " + dayHindi + " को " + timeNatural + " " + docName + " के साथ है।";
            state.setLastAskedField(null);
            state.appendMessage("assistant", aiResponse);
            return new VoiceResponse(transcription, aiResponse, synthesizeSpeech(aiResponse), false, state.getSessionId());
        }

        // CONTINUE, PROVIDE_INFO, BOOK_APPOINTMENT, SOFT_END, UNCLEAR → Entity Extraction
        String dateContext = dateNormalizerService.getDateContext(transcription);
        System.out.println("Extracting AI entities for: " + transcription);
        LlmExtractionResponse extracted = extractGroqEntities(transcription, dateContext, state);

        // Bypass OOS flag for short contextual answers (e.g. "Rahul", "Orthopedic")
        int wordCount = transcription.split("\\s+").length;
        if (wordCount <= 3 && state.getLastAskedField() != null) {
            extracted.setIsOutOfScope(false);
        }

        // Hard Out-of-Scope gate
        if (extracted.getIsOutOfScope() != null && extracted.getIsOutOfScope()
                && (extracted.getIsQuerying() == null || !extracted.getIsQuerying())) {
            String oosMsg = "माफ़ कीजिए, मैं सिर्फ अपॉइंटमेंट बुकिंग में आपकी मदद कर सकती हूँ।";
            state.appendMessage("assistant", oosMsg);
            return new VoiceResponse(transcription, oosMsg, synthesizeSpeech(oosMsg), false, state.getSessionId());
        }

        System.out.println("[LOG] Extracted: name=" + extracted.getName()
            + " dept=" + extracted.getDepartment()
            + " date=" + extracted.getDate()
            + " time=" + extracted.getTime());

        boolean timeInvalid = false;
        String systemData = "";   // hoisted so hydration can set PAST_DATE flag
        String nextAction = "";
        boolean endCall = false;

        // ─── Safe Entity Hydration — NEVER overwrite a filled slot ──────────────
        if (!state.isConfirmed() && state.getMode() != SessionState.Mode.CONFIRMATION) {

            // Single cleanText — strips punctuation, preserves all Unicode scripts
            String cleanText = transcription.replaceAll("[।.,!?\\s]+", "").trim();

            // LOOP PREVENTION: if same question asked ≥ 2 times → force-accept raw input
            if (state.getRepeatCount() >= 2 && state.getLastAskedField() != null) {
                System.out.println("[LOG] Loop detected (repeat=" + state.getRepeatCount() + "), force-accepting for: " + state.getLastAskedField());
                forceAcceptForField(state, cleanText, transcription, extracted);
                state.resetRepeatCount();

            } else if (wordCount <= 3 && state.getLastAskedField() != null) {
                // Short contextual answer — map directly to the pending slot
                if (state.getLastAskedField().equals("name") && state.getPatientName() == null) {
                    String val = extracted.getName();
                    if (isValidValue(val) && isValidName(val)) {
                        state.setPatientName(val);
                    } else if (isValidName(cleanText)) {
                        state.setPatientName(cleanText);
                        System.out.println("[LOG] Name via heuristic fallback: " + cleanText);
                    }
                } else if (state.getLastAskedField().equals("department") && state.getDepartment() == null) {
                    String val = normalizeDepartment(extracted.getDepartment());
                    if (isValidValue(val)) {
                        state.setDepartment(val);
                    } else {
                        String fallback = normalizeDepartment(cleanText);
                        if (isValidValue(fallback)) state.setDepartment(fallback);
                    }
                } else if (state.getLastAskedField().equals("date")
                        && (state.getDate() == null || state.getMode() == SessionState.Mode.RESCHEDULE)) {
                    // In RESCHEDULE mode: ALWAYS overwrite date — user is picking a new day
                    // Priority 1: deterministic weekday extraction from raw text
                    String weekdayDate = resolveWeekdayFromText(transcription);
                    String val = (weekdayDate != null) ? weekdayDate : extracted.getDate();
                    System.out.println("[LOG] Date candidate: weekday=" + weekdayDate + " llm=" + extracted.getDate() + " using=" + val);
                    if (isValidValue(val) && val.contains(",")) {
                        systemData = "MULTI_DATE";
                    } else if (isValidValue(val)) {
                        if (isPastDate(val)) systemData = "PAST_DATE";
                        else state.setDate(val);
                    }
                }

            } else if (state.getLastAskedField().equals("time") && state.getTime() == null) {
                // Time slot-fill (moved here from mixed block)
                String candidateTime = isValidValue(extracted.getTime()) ? extracted.getTime() : null;
                if (candidateTime == null && !transcription.replaceAll("[^0-9]", "").isEmpty()) {
                    candidateTime = transcription.trim();
                }
                if (candidateTime != null && isValidTimeFormat(candidateTime)) {
                    if (isTimeInSlot(candidateTime, state)) state.setTime(candidateTime);
                    else timeInvalid = true;
                } else if (candidateTime != null) {
                    timeInvalid = true;
                }

            } else if (!"PAST_DATE".equals(systemData)) {
                // Standard multi-word hydration
                String eName = extracted.getName();
                if (isValidValue(eName) && isValidName(eName) && state.getPatientName() == null)
                    state.setPatientName(eName);

                String eDept = normalizeDepartment(extracted.getDepartment());
                if (isValidValue(eDept) && state.getDepartment() == null)
                    state.setDepartment(eDept);

                // Priority 1: deterministic weekday extraction from raw transcription
                String _weekdayDate = resolveWeekdayFromText(transcription);
                String eDate = (_weekdayDate != null) ? _weekdayDate : extracted.getDate();
                System.out.println("[LOG] Multi-word date candidate: weekday=" + _weekdayDate + " llm=" + extracted.getDate() + " using=" + eDate);
                if (isValidValue(eDate) && eDate.contains(",")) {
                    // Multiple dates extracted — ask user to choose one
                    systemData = "MULTI_DATE";
                } else if (isValidValue(eDate)
                        && (state.getDate() == null || state.getMode() == SessionState.Mode.RESCHEDULE)) {
                    // In RESCHEDULE: always overwrite date with the new one
                    if (isPastDate(eDate)) systemData = "PAST_DATE";
                    else state.setDate(eDate);
                }

                String eTime = extracted.getTime();
                if (isValidValue(eTime) && state.getTime() == null && isValidTimeFormat(eTime)) {
                    if (isTimeInSlot(eTime, state)) state.setTime(eTime);
                    else timeInvalid = true;
                } else if (isValidValue(eTime) && !isValidTimeFormat(eTime)) {
                    System.out.println("[LOG] Rejected invalid time format: " + eTime);
                }
            }
        }

        boolean justEnteredConfirmation = false;
        if (state.getPatientName() != null && state.getDepartment() != null && state.getDate() != null && state.getTime() != null && !state.isConfirmed()) {
            if (state.getMode() == SessionState.Mode.BOOKING || state.getMode() == SessionState.Mode.RESCHEDULE) {
                state.setMode(SessionState.Mode.CONFIRMATION);
                justEnteredConfirmation = true;
                if (state.getAssignedDoctor() == null) {
                    state.setAssignedDoctor(matchDoctorRaw(state));
                }
            }
        }

        boolean isCancel = intent.equals("CANCEL") || cTextCheck.contains("कैंसिल") || cTextCheck.contains("cancel");
        boolean isEnd = intent.equals("END") || cTextCheck.equals("नहीं") || cTextCheck.equals("बस") || cTextCheck.contains("धन्यवाद") || cTextCheck.contains("डिस्कनेक्ट");
        boolean isReschedule = intent.equals("RESCHEDULE")
            || cTextCheck.contains("रीशेड्यूल") || cTextCheck.contains("रिशेड्यूल")
            || cTextCheck.contains("रीशिड्यूल") || cTextCheck.contains("reschedule")
            || cTextCheck.contains("दूसरे दिन") || cTextCheck.contains("दिन बदलना") || cTextCheck.contains("बदलना है");
        boolean isPositive = !justEnteredConfirmation && (isConfirmKeyword || (extracted.getIsConfirming() != null && extracted.getIsConfirming()) || intent.equals("CONTINUE"));

        // Past date rejection takes priority — stay in ASK_DATE
        // Multiple dates in one utterance — ask user to choose
        if ("MULTI_DATE".equals(systemData)) {
            nextAction = "ASK_DATE";
            state.setLastAskedField("date");
            systemData = "";
        } else if ("PAST_DATE".equals(systemData)) {

            nextAction = "ASK_DATE";
            state.setLastAskedField("date");
            systemData = "यह तारीख़ बीत चुकी है। कृपया भविष्य की तारीख़ बताएं।";
        } else if (isEnd && state.getMode() != SessionState.Mode.CONFIRMATION) {
            nextAction = "END";
            endCall = true;
            state.setMode(SessionState.Mode.POST_CONFIRM);
        } else if (isReschedule && (state.isConfirmed() || state.getMode() == SessionState.Mode.POST_CONFIRM || state.getMode() == SessionState.Mode.CONFIRMATION)) {
            // Reschedule: keep name + department, clear date/time/doctor
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setMode(SessionState.Mode.RESCHEDULE);
            state.setLastAskedField("date");
            nextAction = "ASK_DATE";
            systemData = "ठीक है, नई तारीख़ बताइए।";
        } else if (isCancel) {
            state.setPatientName(null);
            state.setDepartment(null);
            state.setDate(null);
            state.setTime(null);
            state.setAssignedDoctor(null);
            state.setConfirmed(false);
            state.setMode(SessionState.Mode.BOOKING);
            state.setLastAskedField(null);
            nextAction = "CANCEL";
        } else if (intent.equals("SOFT_END")) {
            // SOFT_END during CONFIRMATION must NOT end — re-ask for confirmation
            if (state.getMode() == SessionState.Mode.CONFIRMATION) {
                nextAction = "CONFIRM_DETAILS";
                systemData = buildBookingSummary(state);
            } else {
                nextAction = "END";
                endCall = true;
            }
        } else if (intent.equals("UNCLEAR") && !justEnteredConfirmation) {
            if (state.getLastAskedField() != null) {
                nextAction = "ASK_" + state.getLastAskedField().toUpperCase();
            } else {
                nextAction = "INFORM";
                systemData = "माफ़ कीजिए, मुझे यह स्पष्ट नहीं समझ आया। कृपया फिर से बताएं।";
            }
        } else if ((intent.equals("ASK_QUERY") || (extracted.getIsQuerying() != null && extracted.getIsQuerying()))
                && !justEnteredConfirmation) {
            nextAction = "INFORM";
            if (state.isConfirmed() || state.getMode() == SessionState.Mode.CONFIRMATION) {
                // Confirmed — answer from frozen snapshot in Hindi
                String dH = toHindiDay(getDayOfWeek(state.getDate()));
                String tN = toNaturalTime(state.getTime());
                String dN = formatDoctorName(state.getAssignedDoctor());
                systemData = "आपका अपॉइंटमेंट " + dH + " को " + tN + " " + dN + " के साथ है।";
            } else {
                // Booking/Reschedule — first resolve any weekday from text, then show fresh availability
                if (state.getMode() == SessionState.Mode.RESCHEDULE) {
                    String _wd = resolveWeekdayFromText(transcription);
                    if (_wd != null && !_wd.equals(state.getDate())) {
                        System.out.println("[LOG] RESCHEDULE: date updated from query: " + state.getDate() + " → " + _wd);
                        state.setDate(_wd);
                    } else if (isValidValue(extracted.getDate()) && !extracted.getDate().contains(",")) {
                        String llmD = extracted.getDate();
                        if (!llmD.equals(state.getDate()) && !isPastDate(llmD)) {
                            System.out.println("[LOG] RESCHEDULE: date updated from LLM query: " + state.getDate() + " → " + llmD);
                            state.setDate(llmD);
                        }
                    }
                }
                systemData = buildAvailabilityInfo(state);
            }
        } else if (state.getMode() == SessionState.Mode.BOOKING || state.getMode() == SessionState.Mode.RESCHEDULE) {
            if (state.getPatientName() == null) {
                nextAction = "ASK_NAME";
                state.setLastAskedField("name");   // auto-resets repeatCount if field changed
                state.incrementRepeatCount();
            } else if (state.getDepartment() == null) {
                nextAction = "ASK_DEPARTMENT";
                state.setLastAskedField("department");
                state.incrementRepeatCount();
            } else if (state.getDate() == null) {
                nextAction = "ASK_DATE";
                state.setLastAskedField("date");
                state.incrementRepeatCount();
            } else if (state.getTime() == null) {
                if (timeInvalid) {
                    // timeInvalid flag is set — ASK_TIME repeat-count will give contextual message
                }
                nextAction = "ASK_TIME";
                state.setLastAskedField("time");
                state.incrementRepeatCount();
            }
        } else if (state.getMode() == SessionState.Mode.CONFIRMATION) {
            if (isNegativeConfirm) {
                // User explicitly rejected — offer to change time or date
                state.setTime(null);
                state.setAssignedDoctor(null);
                state.setMode(SessionState.Mode.BOOKING);
                state.setLastAskedField("time");
                state.resetRepeatCount();
                nextAction = "NEG_CONFIRM";
            } else if (isPositive) {
                state.setConfirmed(true);
                state.setMode(SessionState.Mode.POST_CONFIRM);
                state.setLastAskedField(null);
                nextAction = "POST_CONFIRM";
            } else {
                nextAction = "CONFIRM_DETAILS";
                state.setLastAskedField("confirmation");
                systemData = buildBookingSummary(state);
            }
        } else if (state.getMode() == SessionState.Mode.POST_CONFIRM) {
            nextAction = "POST_CONFIRM";
        }

        // Build LLM Context
        String contextStr = buildLLMContext(state, nextAction, systemData);
        System.out.println("Generated LLM Context:\n" + contextStr);

        // Hardcoded responses for deterministic states — zero LLM for guaranteed correctness
        String aiResponse;
        switch (nextAction) {
            case "GREETING"         -> aiResponse = "नमस्ते, मैं क्लिनिक की रिसेप्शनिस्ट हूँ। अपना नाम बताइए।";
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
            case "INFORM"           -> aiResponse = systemData;  // pre-built Hindi from buildAvailabilityResponse
            case "CONFIRM_DETAILS"  -> {
                String dayH  = toHindiDay(getDayOfWeek(state.getDate()));
                String dateH = toHindiDate(state.getDate());  // e.g. "26 अप्रैल"
                String timeN = toNaturalTime(state.getTime());
                String docN  = formatDoctorName(state.getAssignedDoctor());
                String dept  = formatDeptName(state.getDepartment());
                aiResponse = "आपका अपॉइंटमेंट " + dayH + ", " + dateH + " को "
                    + timeN + " " + docN + " (" + dept + " विभाग) के साथ है, क्या मैं इसे कन्फर्म कर दूँ?";
            }
            default -> {
                try { aiResponse = generateGroqResponse(contextStr); }
                catch (Exception e) { aiResponse = "माफ़ कीजिए, कुछ तकनीकी दिक्कत आई।"; }
            }
        }

        // Add pure response text to memory
        state.appendMessage("assistant", aiResponse);
        System.out.println("[LOG] AI Response: " + aiResponse);
        System.out.println("[LOG] Final State | mode=" + state.getMode()
            + " | name=" + state.getPatientName()
            + " | dept=" + state.getDepartment()
            + " | date=" + state.getDate()
            + " | time=" + state.getTime()
            + " | doctor=" + state.getAssignedDoctor()
            + " | lastAsked=" + state.getLastAskedField()
            + " | repeatCount=" + state.getRepeatCount()
            + " | confirmed=" + state.isConfirmed());

        // 3. TTS (Text to Speech) via Sarvam
        System.out.println("Synthesizing speech...");
        String audioBase64;
        try {
            audioBase64 = synthesizeSpeech(aiResponse);
        } catch (Exception e) {
            System.err.println("Failed TTS: " + e.getMessage());
            audioBase64 = "";
        }

        return new VoiceResponse(transcription, aiResponse, audioBase64, endCall, state.getSessionId());
    }

    private String transcribeAudio(MultipartFile audio) throws Exception {
        String url = "https://api.sarvam.ai/speech-to-text";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("api-subscription-key", sarvamApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource fileAsResource = new ByteArrayResource(audio.getBytes()) {
            @Override
            public String getFilename() {
                return audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.wav";
            }
        };

        body.add("file", fileAsResource);
        body.add("model", "saaras:v3");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());

            if (root.has("transcript")) {
                return root.get("transcript").asText();
            } else if (root.has("text")) {
                return root.get("text").asText();
            } else {
                return root.toString();
            }
        } catch (HttpClientErrorException e) {
            throw new Exception("Client Error: " + e.getResponseBodyAsString());
        }
    }

    private LlmExtractionResponse extractGroqEntities(String transcription, String dateContext, SessionState state) throws Exception {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        body.put("response_format", responseFormat);

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");

        String systemPromptContent = "You are a strict JSON extractor for a clinic booking voice assistant.\n" +
                "RULES:\n" +
                "1. Output ONLY a flat JSON object with exactly these 7 keys. NO nested objects. NO extra keys.\n" +
                "2. All string values must be plain strings, never objects or arrays.\n" +
                "3. If a value is not present in the input, use JSON null.\n" +
                "4. The user input may contain Bengali, Hindi, or English. ALWAYS translate names and departments into English.\n" +
                "5. Normalize department names to one of: Orthopedic, Cardiology, Neurology, Dermatology, General Physician, Pediatrics.\n" +
                "   Variants: 'हड्डी' or 'bone' or 'ortho' → Orthopedic\n" +
                "             'heart' or 'दिल' or 'cardio' → Cardiology\n" +
                "             'brain' or 'neuro' or 'दिमाग' → Neurology\n" +
                "             'skin' or 'derma' or 'त्वचा' → Dermatology\n" +
                "             'general' or 'सामान्य' → General Physician\n" +
                "             'child' or 'paed' or 'बच्चे' → Pediatrics\n" +
                "6. date: ONLY extract if the user EXPLICITLY mentions a day, date, 'आज', 'कल', 'परसों', or a specific date.\n" +
                "   If user says only 'अपॉइंटमेंट चाहिए' with NO date word → date MUST be null.\n" +
                "   When present, format as YYYY-MM-DD using the context mapping below.\n" +
                "7. time: Keep exactly as stated (e.g. '10 AM', '3 PM'). Do NOT translate or rephrase.\n" +
                "8. isConfirming: true only if user explicitly says yes/haan/confirm. false otherwise.\n" +
                "9. isQuerying: true if user asks about existing booking details or available times.\n" +
                "10. isOutOfScope: true ONLY for completely unrelated topics (weather, news). NOT for booking queries.\n\n" +
                "JSON schema:\n" +
                "{\"name\": string|null, \"department\": string|null, \"date\": string|null, \"time\": string|null, \"isConfirming\": boolean, \"isQuerying\": boolean, \"isOutOfScope\": boolean}\n\n";

        if (state.getLastAskedField() != null) {
            systemPromptContent += "CRITICAL CONTEXT: You just explicitly asked the user for their '" + state.getLastAskedField() + "'. If they give a short 1-word answer, map it to '" + state.getLastAskedField() + "' instead of marking it outOfScope! You MUST extract and translate it to Hindi/English.\n";
        }

        if (dateContext != null && !dateContext.isEmpty()) {
            systemPromptContent += "Context mapping:\n" + dateContext + "\n";
        }

        if (!state.getMessageHistory().isEmpty()) {
            systemPromptContent += "\nRecent Conversation History:\n";
            int start = Math.max(0, state.getMessageHistory().size() - 3);
            for (int i = start; i < state.getMessageHistory().size(); i++) {
                Map<String, String> msg = state.getMessageHistory().get(i);
                systemPromptContent += msg.get("role") + ": " + msg.get("content") + "\n";
            }
        }

        systemMsg.put("content", systemPromptContent);
        messages.add(systemMsg);

        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        userMsgMap.put("content", transcription);
        messages.add(userMsgMap);

        body.put("messages", messages);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            String jsonOutput = root.path("choices").path(0).path("message").path("content").asText();
            System.out.println("Extracted JSON: " + jsonOutput);
            // Field-level safe parse: tolerates partial/malformed JSON
            try {
                JsonNode j = mapper.readTree(jsonOutput);
                LlmExtractionResponse r = new LlmExtractionResponse();
                if (j.hasNonNull("name") && !j.get("name").asText().equals("null")) r.setName(j.get("name").asText());
                if (j.hasNonNull("department") && !j.get("department").asText().equals("null")) r.setDepartment(j.get("department").asText());
                if (j.hasNonNull("date") && !j.get("date").asText().equals("null")) r.setDate(j.get("date").asText());
                if (j.hasNonNull("time") && !j.get("time").asText().equals("null")) r.setTime(j.get("time").asText());
                if (j.hasNonNull("isConfirming")) r.setIsConfirming(j.get("isConfirming").asBoolean(false));
                if (j.hasNonNull("isQuerying")) r.setIsQuerying(j.get("isQuerying").asBoolean(false));
                if (j.hasNonNull("isOutOfScope")) r.setIsOutOfScope(j.get("isOutOfScope").asBoolean(false));
                return r;
            } catch (Exception parseEx) {
                System.err.println("Field-level parse failed, returning empty: " + parseEx.getMessage());
                return new LlmExtractionResponse();
            }
        } catch (Exception e) {
            System.err.println("Groq JSON Extraction Error: " + e.getMessage());
            return new LlmExtractionResponse();
        }
    }

    /** Returns true if a string value from LLM is genuinely useful (not null/empty/literal-null). */
    /**
     * Loop prevention: after the same question has been asked ≥ 2 times without a slot being filled,
     * force-accept the raw transcription into the correct slot using heuristic rules.
     */
    private void forceAcceptForField(SessionState state, String cleanText, String rawTranscription, LlmExtractionResponse extracted) {
        String field = state.getLastAskedField();
        if (field == null) return;
        switch (field) {
            case "name" -> {
                if (state.getPatientName() == null && isValidName(cleanText)) {
                    state.setPatientName(cleanText);
                    System.out.println("[LOG] Force-accepted name: " + cleanText);
                }
            }
            case "department" -> {
                if (state.getDepartment() == null) {
                    String dept = normalizeDepartment(isValidValue(extracted.getDepartment()) ? extracted.getDepartment() : cleanText);
                    if (isValidValue(dept)) {
                        state.setDepartment(dept);
                        System.out.println("[LOG] Force-accepted department: " + dept);
                    }
                }
            }
            case "date" -> {
                if (state.getDate() == null && isValidValue(extracted.getDate()) && !isPastDate(extracted.getDate())) {
                    state.setDate(extracted.getDate());
                    System.out.println("[LOG] Force-accepted date: " + extracted.getDate());
                }
            }
            case "time" -> {
                if (state.getTime() == null) {
                    String t = isValidValue(extracted.getTime()) ? extracted.getTime() : rawTranscription.trim();
                    if (t.matches(".*\\d.*")) {
                        state.setTime(t);
                        System.out.println("[LOG] Force-accepted time: " + t);
                    }
                }
            }
        }
    }

    /** Returns true if a string value from LLM is genuinely useful (not null/empty/literal-null). */
    private boolean isValidValue(String val) {
        return val != null && !val.trim().isEmpty() && !val.trim().equalsIgnoreCase("null");
    }

    /**
     * Returns true if a string looks like a real patient name (1-3 words, no booking keywords).
     * Prevents sentences like "मुझे अपॉइंटमेंट चाहिए" from being stored as the patient name.
     */
    /** Rejects LLM hallucinated names like "appointment", "confirmation". */
    private static final java.util.Set<String> INVALID_NAME_TOKENS = java.util.Set.of(
        "appointment", "अपॉइंटमेंट", "confirmation", "कन्फर्म", "booking",
        "reschedule", "रीशेड्यूल", "neurology", "न्यूरोलॉजी", "orthopedic",
        "cardiology", "dermatology", "pediatrics", "physician", "doctor"
    );
    private boolean isValidName(String name) {
        if (name == null) return false;
        String n = name.trim();
        // Reject if too many words (more than 3)
        if (n.split("\\s+").length > 3) return false;
        // Reject if it contains booking-related Hindi/English keywords or dept/medical terms
        String lower = n.toLowerCase();
        for (String bad : INVALID_NAME_TOKENS) { if (lower.contains(bad)) return false; }
        return !lower.contains("appointment") && !lower.contains("अपॉइंटमेंट")
            && !lower.contains("चाहिए") && !lower.contains("बुक") && !lower.contains("लेना")
            && !lower.contains("करना") && !lower.contains("दिखाना") && !lower.contains("doctor")
            && !lower.contains("विभाग") && !lower.contains("क्लिनिक");
    }

    /**
     * Validates that extracted time is a recognisable time expression, not a
     * day/date string accidentally returned as time (e.g. "Monday 27th April").
     * Accepts: "10 AM", "3 PM", "12:00", plain digits like "10", Hindi time phrases.
     */
    private boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        String t = time.trim().toUpperCase();
        // Accept standard 12h: "10 AM", "3 PM", "12 PM"
        if (t.matches("\\d{1,2}\\s*(AM|PM)")) return true;
        // Accept HH:MM
        if (t.matches("\\d{1,2}:\\d{2}(\\s*(AM|PM))?")) return true;
        // Accept Hindi time phrases
        if (time.contains("बजे") || time.contains("सुबह") || time.contains("दोपहर") || time.contains("शाम")) return true;
        // Accept plain 1-2 digit hour number
        if (t.matches("\\d{1,2}")) {
            int h = Integer.parseInt(t);
            return h >= 1 && h <= 12;
        }
        // Reject anything with day names, dates, month names
        String low = time.toLowerCase();
        for (String day : new String[]{"monday","tuesday","wednesday","thursday","friday","saturday","sunday",
                "सोमवार","मंगलवार","बुधवार","गुरुवार","शुक्रवार","शनिवार","रविवार",
                "april","may","june","january","february","march","july","august",
                "अप्रैल","मार्च","जनवरी"}) {
            if (low.contains(day)) return false;
        }
        return false;
    }

    /** Maps department synonyms (Hindi/English/colloquial/symptom) to canonical config keys. */
    private String normalizeDepartment(String input) {
        if (input == null) return null;
        String s = input.toLowerCase().trim();
        // Orthopedic — bone/joint symptoms
        if (s.contains("ortho") || s.contains("हड्डी") || s.contains("bone") || s.contains("अस्थि")
                || s.contains("घुटने") || s.contains("जोड़") || s.contains("कमर") || s.contains("बाँह्") || s.contains("टाँग")) return "Orthopedic";
        // Cardiology — heart symptoms
        if (s.contains("cardio") || s.contains("heart") || s.contains("दिल") || s.contains("छाती") || s.contains("सांस")) return "Cardiology";
        // Neurology — brain/nerve symptoms
        if (s.contains("neuro") || s.contains("brain") || s.contains("दिमाग") || s.contains("सिरदर्द") || s.contains("मिर्गी")) return "Neurology";
        // Dermatology — skin symptoms
        if (s.contains("derma") || s.contains("skin") || s.contains("त्वचा") || s.contains("खाज") || s.contains("दाने") || s.contains("पिंपल")) return "Dermatology";
        // General Physician
        if (s.contains("general") || s.contains("सामान्य") || s.contains("gp") || s.contains("बुखार") || s.contains("जुकाम")) return "General Physician";
        // Pediatrics — child
        if (s.contains("paed") || s.contains("pedia") || s.contains("child") || s.contains("बच्च")) return "Pediatrics";
        return input;
    }

    /** Returns true if the given YYYY-MM-DD string is strictly before today. */
    private boolean isPastDate(String dateStr) {
        if (dateStr == null) return false;
        try {
            java.time.LocalDate parsed = java.time.LocalDate.parse(dateStr);
            return parsed.isBefore(java.time.LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if the user-provided time falls within the assigned doctor's slot.
     * Accepts formats: "10 AM", "10:30 AM", "14:00", "2 PM" etc.
     * Falls back to true (don't reject) if parsing is ambiguous.
     */
    private boolean isTimeInSlot(String userTime, SessionState state) {
        if (userTime == null || state.getDepartment() == null || state.getDate() == null) return true;
        String dayOfWeek = getDayOfWeek(state.getDate());
        if (dayOfWeek == null) return true;
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.equalsIgnoreCase(state.getDepartment()) || dept.toLowerCase().contains(state.getDepartment().toLowerCase())) {
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
        return true; // no doctor for that day — let state machine handle
    }

    /** Converts time strings like "10 AM", "2:30 PM", "14:00" to minutes since midnight. */
    private int parseToMinutes(String timeStr) {
        if (timeStr == null) return -1;
        String s = timeStr.trim().toUpperCase();
        try {
            if (s.contains(":")) {
                // HH:MM or H:MM AM/PM
                String[] parts = s.replace("AM", "").replace("PM", "").trim().split(":");
                int h = Integer.parseInt(parts[0].trim());
                int m = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                if (s.contains("PM") && h != 12) h += 12;
                if (s.contains("AM") && h == 12) h = 0;
                return h * 60 + m;
            } else {
                // "10 AM", "3 PM", "14"
                String num = s.replace("AM", "").replace("PM", "").replace("बजे", "").trim();
                // Handle Hindi numerals: extract first numeric sequence
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

    private String synthesizeSpeech(String text) throws Exception {
        String url = "https://api.sarvam.ai/text-to-speech";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-subscription-key", sarvamApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", Collections.singletonList(text));
        body.put("target_language_code", "hi-IN");
        body.put("speaker", "anushka");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            if (root.has("audios") && root.get("audios").isArray() && root.get("audios").size() > 0) {
                return root.get("audios").get(0).asText();
            }
            throw new Exception("No audio returned. Payload: " + response.getBody());
        } catch (HttpClientErrorException e) {
            throw new Exception("Client Error: " + e.getResponseBodyAsString());
        }
    }

    private String classifyIntent(String transcription, SessionState state) {
        String lastAskedField = state.getLastAskedField();
        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        body.put("response_format", responseFormat);

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");

        String systemPromptContent = "You are an intent classifier. Categorize the user's input into one of these intents:\n" +
                "- \"END\": User explicitly wants to end the conversation or say goodbye.\n" +
                "- \"SOFT_END\": User wants to pause or wait.\n" +
                "- \"ASK_QUERY\": User is asking a question about their booking or appointment details.\n" +
                "- \"CONTINUE\": User says yes, ok, go ahead, confirming the current action.\n" +
                "- \"PROVIDE_INFO\": User is providing their name, date, time, or department.\n" +
                "- \"BOOK_APPOINTMENT\": User explicitly asks to start booking an appointment.\n" +
                "- \"UNCLEAR\": Unrelated, gibberish, or cannot determine intent.\n" +
                "Output strictly a JSON object with a single key \"intent\" containing the categorized intent string.";

        if (lastAskedField != null) {
            systemPromptContent += "\nCRITICAL CONTEXT: You just explicitly asked the user for their '" + lastAskedField + "'. If they give a short answer, map it to \"PROVIDE_INFO\"!";
        }

        if (!state.getMessageHistory().isEmpty()) {
            systemPromptContent += "\nRecent Conversation History:\n";
            int start = Math.max(0, state.getMessageHistory().size() - 3);
            for (int i = start; i < state.getMessageHistory().size(); i++) {
                Map<String, String> msg = state.getMessageHistory().get(i);
                systemPromptContent += msg.get("role") + ": " + msg.get("content") + "\n";
            }
        }

        systemMsg.put("content", systemPromptContent);
        messages.add(systemMsg);

        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        userMsgMap.put("content", transcription);
        messages.add(userMsgMap);

        body.put("messages", messages);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            String jsonOutput = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode intentNode = mapper.readTree(jsonOutput);
            if (intentNode.has("intent")) {
                return intentNode.get("intent").asText().toUpperCase();
            }
            return "UNCLEAR";
        } catch (Exception e) {
            System.err.println("Groq Intent Classification Error: " + e.getMessage());
            return "UNCLEAR"; 
        }
    }

    

    
    private String getDayOfWeek(String dateStr) {
        if (dateStr == null) return null;
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            String day = date.getDayOfWeek().name();
            return day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    
    private String buildBookingSummary(SessionState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("BOOKING SUMMARY:\n");
        sb.append("Name: ").append(state.getPatientName() != null ? state.getPatientName() : "Not provided").append("\n");
        sb.append("Department: ").append(state.getDepartment() != null ? state.getDepartment() : "Not provided").append("\n");
        String day = getDayOfWeek(state.getDate());
        sb.append("Date: ").append(day != null ? day : (state.getDate() != null ? state.getDate() : "Not provided")).append("\n");
        sb.append("Time: ").append(state.getTime() != null ? state.getTime() : "Not provided").append("\n");
        // ONLY read locked snapshot — never recompute
        sb.append("Doctor: ").append(state.getAssignedDoctor() != null ? state.getAssignedDoctor() : "To be assigned").append("\n");
        return sb.toString();
    }

    private String matchDoctorRaw(SessionState state) {
        if (state.getDepartment() == null) return "Any available doctor";
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.toLowerCase().contains(state.getDepartment().toLowerCase()) || state.getDepartment().toLowerCase().contains(dept.toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        if (matchedDept == null) return "General Doctor";
        String dayOfWeek = getDayOfWeek(state.getDate());
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

    /**
     * Builds a pre-formatted Hindi availability sentence.
     * Format: "[Day] को [Doctor] [Start12h] से [End12h] तक उपलब्ध हैं।"
     * This is used as SYSTEM DATA for the LLM, giving it a safe literal template.
     * Also used directly as hardcoded INFORM response to bypass LLM entirely.
     */
    String buildAvailabilityResponse(SessionState state) {
        if (state.getDepartment() == null) {
            return "विभाग नहीं बताया गया।";
        }
        String matchedDept = null;
        for (String dept : ClinicConfig.CLINIC_SCHEDULE.keySet()) {
            if (dept.equalsIgnoreCase(state.getDepartment()) || dept.toLowerCase().contains(state.getDepartment().toLowerCase())) {
                matchedDept = dept;
                break;
            }
        }
        if (matchedDept == null) return "इस विभाग के लिए कोई डॉक्टर नहीं मिला।";

        String dayOfWeek = getDayOfWeek(state.getDate());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> doctors = (List<Map<String, Object>>) ClinicConfig.CLINIC_SCHEDULE.get(matchedDept);
        if (doctors == null) return "इस विभाग में अभी कोई डॉक्टर उपलब्ध नहीं हैं।";

        for (Map<String, Object> doc : doctors) {
            @SuppressWarnings("unchecked")
            List<String> days = (List<String>) doc.get("days");
            if (dayOfWeek != null && !days.contains(dayOfWeek)) continue;

            String startH   = toNaturalTime((String) doc.get("start"));   // e.g. "सुबह 10 बजे"
            String endH     = toNaturalTime((String) doc.get("end"));     // e.g. "दोपहर 1 बजे"
            String dayHindi = toHindiDay(dayOfWeek != null ? dayOfWeek : ((List<String>) doc.get("days")).get(0));
            String docDisplay = formatDoctorName(doc.get("name").toString());
            return dayHindi + " को " + docDisplay + " " + startH + " से " + endH + " तक उपलब्ध हैं।";
        }
        return "इस दिन कोई डॉक्टर उपलब्ध नहीं हैं।";
    }

    /** Converts "HH:MM" 24h string to "H बजे" 12h form (e.g. 13:00 → 1 बजे). */

    /** Converts a 1-12 hour integer to Hindi word (e.g. 1 → "एक", 12 → "बारह"). */
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


    /** Maps English department name → natural Hindi phonetic form for speech. */
    private static final java.util.Map<String, String> DEPT_PHONETICS = new java.util.LinkedHashMap<>() {{
        put("General Physician", "जनरल फिजिशियन");
        put("Orthopedic",        "ऑर्थोपेडिक");
        put("Neurology",         "न्यूरोलॉजी");
        put("Cardiology",        "कार्डियोलॉजी");
        put("Dermatology",       "डर्मेटोलॉजी");
        put("Pediatrics",        "पीडियाट्रिक्स");
    }};

    /** Returns the Hindi phonetic form of an English department name. */
    String formatDeptName(String dept) {
        if (dept == null) return "";
        return DEPT_PHONETICS.getOrDefault(dept, dept);
    }

    /** Maps English surname → Hindi phonetic pronunciation for TTS clarity. */
    private static final java.util.Map<String, String> DOCTOR_PHONETICS = new java.util.LinkedHashMap<>() {{
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

    /** Formats a doctor name "Dr. Roy" → "डॉक्टर रॉय" using phonetic map. */
    String formatDoctorName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String name = raw.trim().replaceFirst("^Dr\\.\\s*", "").trim();
        for (java.util.Map.Entry<String, String> e : DOCTOR_PHONETICS.entrySet()) {
            name = name.replace(e.getKey(), e.getValue());
        }
        return "डॉक्टर " + name;
    }



    /**
     * Scans raw transcription for any weekday keyword (Hindi, English, Hinglish transliteration)
     * and returns the NEXT UPCOMING date for that weekday as "YYYY-MM-DD".
     * Returns null if no weekday found.
     * This is the authoritative date source in RESCHEDULE mode — always beats LLM extraction.
     */
    private String resolveWeekdayFromText(String text) {
        if (text == null) return null;
        String low = text.toLowerCase().trim();

        // Map of keywords → DayOfWeek (Java 1=Mon … 7=Sun)
        java.util.Map<String, java.time.DayOfWeek> keywords = new java.util.LinkedHashMap<>();
        keywords.put("monday",      java.time.DayOfWeek.MONDAY);
        keywords.put("मंडे",         java.time.DayOfWeek.MONDAY);
        keywords.put("सोमवार",       java.time.DayOfWeek.MONDAY);
        keywords.put("tuesday",     java.time.DayOfWeek.TUESDAY);
        keywords.put("मंगलवार",      java.time.DayOfWeek.TUESDAY);
        keywords.put("wednesday",   java.time.DayOfWeek.WEDNESDAY);
        keywords.put("बुधवार",       java.time.DayOfWeek.WEDNESDAY);
        keywords.put("thursday",    java.time.DayOfWeek.THURSDAY);
        keywords.put("गुरुवार",      java.time.DayOfWeek.THURSDAY);
        keywords.put("friday",      java.time.DayOfWeek.FRIDAY);
        keywords.put("शुक्रवार",     java.time.DayOfWeek.FRIDAY);
        keywords.put("saturday",    java.time.DayOfWeek.SATURDAY);
        keywords.put("शनिवार",      java.time.DayOfWeek.SATURDAY);
        keywords.put("sunday",      java.time.DayOfWeek.SUNDAY);
        keywords.put("रविवार",      java.time.DayOfWeek.SUNDAY);

        for (java.util.Map.Entry<String, java.time.DayOfWeek> e : keywords.entrySet()) {
            if (low.contains(e.getKey())) {
                java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
                java.time.LocalDate target = DateNormalizerService.nextWeekday(today, e.getValue());
                System.out.println("[LOG] resolveWeekdayFromText: '" + e.getKey() + "' → " + target);
                return target.toString();
            }
        }
        return null;
    }

    /** Maps English month name/number to Hindi. Returns e.g. "26 अप्रैल" from "2026-04-26". */
    private String toHindiDate(String isoDate) {
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

    /**
     * Converts any time string to natural spoken Hindi with time-of-day prefix.
     * Handles: "HH:MM", "H AM/PM", "H बजे", numeric hour strings.
     * Examples:
     *   "09:00" → "सुबह 9 बजे"
     *   "12:00" → "दोपहर 12 बजे"
     *   "13:00" → "दोपहर 1 बजे"
     *   "16:00" → "शाम 4 बजे"
     *   "10 AM" → "सुबह 10 बजे"
     *   "12 PM" → "दोपहर 12 बजे"
     */
    String toNaturalTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            int hour24;
            // Parse "H AM" / "H PM" / "HH:MM AM" etc.
            String up = raw.trim().toUpperCase();
            if (up.contains("AM") || up.contains("PM")) {
                String numPart = up.replaceAll("[^0-9:]", "").split(":")[0];
                int h = Integer.parseInt(numPart);
                if (up.contains("PM") && h != 12) h += 12;
                if (up.contains("AM") && h == 12) h = 0;
                hour24 = h;
            } else if (raw.contains(":")) {
                // "HH:MM" 24h
                hour24 = Integer.parseInt(raw.split(":")[0].trim());
            } else {
                // Plain number like "10" or "13"
                hour24 = Integer.parseInt(raw.replaceAll("[^0-9]", "").trim());
            }

            // Convert to 12h
            int h12 = hour24 == 0 ? 12 : (hour24 > 12 ? hour24 - 12 : hour24);
            // Time-of-day prefix
            String prefix;
            if      (hour24 >= 5  && hour24 < 12) prefix = "सुबह";
            else if (hour24 == 12)                 prefix = "दोपहर";
            else if (hour24 >= 12 && hour24 < 17)  prefix = "दोपहर";
            else if (hour24 >= 17 && hour24 < 21)  prefix = "शाम";
            else                                    prefix = "रात";

            return prefix + " " + toHindiNumber(h12) + " बजे";
        } catch (Exception e) {
            // Fallback: pass raw value unchanged
            return raw;
        }
    }

    /** Used by to12Hour (kept for buildAvailabilityResponse range formatting). */
    private String to12Hour(String hhmm) {
        if (hhmm == null) return "";
        try {
            String[] parts = hhmm.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (h == 0) h = 12;
            else if (h > 12) h -= 12;
            return m == 0 ? h + " बजे" : h + ":" + String.format("%02d", m) + " बजे";
        } catch (Exception e) {
            return hhmm;
        }
    }

    /**
     * If user in POST_CONFIRM mode asks about their booking details,
     * return a direct Hindi answer from the frozen snapshot.
     * Returns null if the query doesn't match a known slot question.
     */
    private String resolvePostConfirmQuery(String lowerInput, SessionState state) {
        String dayH  = toHindiDay(getDayOfWeek(state.getDate()));
        String timeN = toNaturalTime(state.getTime());
        String dept  = formatDeptName(state.getDepartment());
        String doc   = state.getAssignedDoctor() != null
                        ? formatDoctorName(state.getAssignedDoctor()) : "";

        // Department / विभाग query
        if (lowerInput.contains("विभाग") || lowerInput.contains("department")
                || lowerInput.contains("किस में") || lowerInput.contains("कौन सा विभाग")) {
            return "जी, आपका अपॉइंटमेंट " + dept + " विभाग में है।";
        }
        // Date / day query
        if (lowerInput.contains("कब") || lowerInput.contains("कौन सा दिन")
                || lowerInput.contains("तारीख") || lowerInput.contains("date")
                || lowerInput.contains("दिन")) {
            return "आपका अपॉइंटमेंट " + dayH + " को है।";
        }
        // Time query
        if (lowerInput.contains("समय") || lowerInput.contains("टाइम")
                || lowerInput.contains("time") || lowerInput.contains("बजे")) {
            return "आपका अपॉइंटमेंट " + timeN + " का है।";
        }
        // Doctor query
        if (lowerInput.contains("डॉक्टर") || lowerInput.contains("doctor")
                || lowerInput.contains("कौन") || lowerInput.contains("डॉ")) {
            return "आपका अपॉइंटमेंट " + doc + " के साथ है।";
        }
        return null;   // not a slot query — fall through to normal flow
    }

    /** Maps English weekday name to Hindi. */
    private String toHindiDay(String day) {
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

    private String buildAvailabilityInfo(SessionState state) {
        // Keep raw English info for LLM context (legacy), but we now use buildAvailabilityResponse for INFORM
        return buildAvailabilityResponse(state);
    }

    // matchDoctor (legacy) removed — only matchDoctorRaw is used

    private String buildLLMContext(SessionState state, String nextAction, String systemData) {
        StringBuilder sb = new StringBuilder();
        sb.append("USER STATE:\n");
        sb.append("Name: ").append(state.getPatientName() != null ? state.getPatientName() : "Not provided").append("\n");
        sb.append("Department: ").append(state.getDepartment() != null ? state.getDepartment() : "Not provided").append("\n");
        sb.append("Date: ").append(state.getDate() != null ? state.getDate() : "Not provided");
        String day = getDayOfWeek(state.getDate());
        if (day != null) sb.append(" (").append(day).append(")");
        sb.append("\n");
        sb.append("Time: ").append(state.getTime() != null ? state.getTime() : "Not provided").append("\n\n");
        
        sb.append("SYSTEM DATA:\n");
        sb.append(systemData != null && !systemData.isEmpty() ? systemData : "None").append("\n\n");

        sb.append("NEXT ACTION:\n");
        sb.append(nextAction).append("\n");

        return sb.toString();
    }

    private String generateGroqResponse(String contextStr) throws Exception {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        String sysPrompt =
            "You are a strict response formatter for a clinic receptionist voice assistant.\n" +
            "You are NOT an AI. You do NOT think. You ONLY echo the GIVEN CONTEXT into ONE Hindi sentence.\n\n" +

            "=== ABSOLUTE RULES ===\n" +
            "1. NEVER change the department name. Use it EXACTLY as given (e.g. Orthopedic stays 'Orthopedic', DO NOT translate).\n" +
            "2. NEVER translate department names to Sanskrit/Hindi medical terms (e.g. Orthopedic ≠ वृक्कशास्त्र).\n" +
            "3. NEVER change time values. Convert 24h to 12h ONLY: 13:00 → 1 बजे, 10:00 → 10 बजे. Never say '13 बजे'.\n" +
            "4. NEVER add patient name prefix like 'राहुल जी'. Start directly with the doctor or time.\n" +
            "5. NEVER add words: बीमारी, चिकित्सा, विशेषज्ञ, समस्या, or any word NOT in CONTEXT.\n" +
            "6. NEVER infer day/date. Use EXACTLY what is in CONTEXT.\n" +
            "7. Return ONLY one sentence. No explanation.\n\n" +

            "=== LANGUAGE ===\n" +
            "- Hindi (Devanagari only).\n" +
            "- Short, polite receptionist style.\n" +
            "- Max 12 words.\n\n" +

            "=== NEXT ACTION RULES ===\n" +
            "INFORM      → 'रविवार को डॉ आयर 10 से 1 तक उपलब्ध हैं।' (day+doctor+start से end तक only)\n" +
            "CONFIRM_DETAILS → 'आपका अपॉइंटमेंट [day] को [time] बजे [doctor] के साथ है, क्या कन्फर्म करूँ?' (use EXACT values only)\n" +
            "OUTPUT FORMAT: Return ONLY the final Hindi sentence. Nothing else.";
        systemMsg.put("content", sysPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", contextStr);
        messages.add(userMsg);

        body.put("messages", messages);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").path(0).path("message").path("content").asText().trim();
        } catch (Exception e) {
            throw new Exception("Groq NLG Error: " + e.getMessage());
        }
    }
}
