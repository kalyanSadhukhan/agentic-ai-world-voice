package com.voiceai.contact.service.llm;

import com.voiceai.contact.service.session.ConversationStage;
import com.voiceai.contact.service.session.SessionState;
import org.springframework.stereotype.Service;

@Service
public class IntentClassifierService {

    public String classifyIntent(String transcription, SessionState state, String extractedIntent) {
        String intent = extractedIntent != null ? extractedIntent : "UNCLEAR";
        String cTextCheck = transcription.toLowerCase().trim();

        // 1. Check Confirmation overrides
        boolean hasNegation = cTextCheck.contains("नहीं") || cTextCheck.contains("मत") || cTextCheck.contains("ना ");
        boolean isConfirmKeyword = !hasNegation && (
                cTextCheck.contains("हाँ") || cTextCheck.contains("हां") || cTextCheck.contains("yes") ||
                cTextCheck.contains("कर दीजिए") || cTextCheck.contains("कर दो") || cTextCheck.contains("बिल्कुल") ||
                cTextCheck.contains("कन्फर्म") || cTextCheck.contains("ठीक है") || cTextCheck.contains("जी") ||
                cTextCheck.contains("sure"));

        if (isConfirmKeyword && state.getStage() == ConversationStage.CONFIRMATION) {
            intent = "CONTINUE";
        } else if (cTextCheck.equals("नहीं") || cTextCheck.equals("बस") || cTextCheck.equals("no") ||
                   cTextCheck.contains("धन्यवाद") || cTextCheck.contains("ज़रूरत नहीं") ||
                   cTextCheck.contains("thank you") || cTextCheck.contains("thanks") || cTextCheck.contains("डिस्कनेक्ट")) {
            // End only if not actively collecting slots
            if (state.getLastAskedField() == null || (state.getStage() != ConversationStage.COLLECT_NAME 
                    && state.getStage() != ConversationStage.COLLECT_DEPARTMENT 
                    && state.getStage() != ConversationStage.COLLECT_DATE 
                    && state.getStage() != ConversationStage.COLLECT_TIME)) {
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
            if (!intent.equals("CANCEL") && (state.getStage() == ConversationStage.WELCOME || state.getStage() == ConversationStage.COLLECT_NAME) 
                    && state.getPatientName() == null) {
                intent = "BOOK_APPOINTMENT";
            }
        }

        // Cancellation checks
        if (intent.equals("CANCEL") && !cTextCheck.contains("कैंसिल") && !cTextCheck.contains("cancel")) {
            intent = "UNCLEAR"; 
        }

        // Reschedule checks
        boolean isRescheduleKeyword = cTextCheck.contains("रीशेडिउल") || cTextCheck.contains("रिशेडिउल")
            || cTextCheck.contains("रीशिडिउल") || cTextCheck.contains("reschedule")
            || cTextCheck.contains("दूसरे दिन") || cTextCheck.contains("दूसरी तारीख")
            || cTextCheck.contains("दिन बदलना") || cTextCheck.contains("बदलना है")
            || cTextCheck.contains("रिशेड्यूल") || cTextCheck.contains("रीशेड्यूल");
            
        if (isRescheduleKeyword) {
            intent = "RESCHEDULE";
        }

        // Failsafe slot-filling override
        if (state.getLastAskedField() != null) {
            if (!intent.equals("END") && !intent.equals("CONTINUE") && !intent.equals("ASK_QUERY")
                    && !intent.equals("CANCEL") && !intent.equals("RESCHEDULE")) {
                intent = "PROVIDE_INFO";
            }
        }

        return intent;
    }
}
