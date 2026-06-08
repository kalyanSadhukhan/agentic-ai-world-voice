package com.voiceai.contact.controller;

import com.voiceai.contact.service.session.SessionManagerService;
import com.voiceai.contact.service.session.SessionState;
import com.voiceai.contact.service.session.GreetingService;
import com.voiceai.contact.service.speech.SpeechTextFormatter;
import com.voiceai.contact.service.speech.TextToSpeechService;
import com.voiceai.contact.service.session.ConversationStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class GreetingController {

    @Autowired
    private SessionManagerService sessionManagerService;

    @Autowired
    private GreetingService greetingService;

    @Autowired
    private SpeechTextFormatter speechTextFormatter;

    @Autowired
    private TextToSpeechService textToSpeechService;

    @PostMapping({"/conversation/start", "/api/voice/start"})
    public ResponseEntity<?> startConversation(@RequestBody(required = false) Map<String, String> request) {
        System.out.println("[START_CONVERSATION_CLICKED]");

        String sessionId = null;
        if (request != null) {
            sessionId = request.get("sessionId");
        }
        
        // Retrieve or create session
        SessionState state = sessionManagerService.getOrCreateSession(sessionId);
        System.out.println("[NEW_SESSION_CREATED] sessionId=" + state.getSessionId());
        
        // Initialize state fields explicitly
        state.setStage(ConversationStage.WELCOME);
        state.setGreetingDone(true); 
        state.setWelcomeDelivered(true); // set welcomeDelivered = true as we are delivering it now
        state.setConversationActive(true);
        state.setPatientName(null);
        state.setDepartment(null);
        state.setDate(null);
        state.setTime(null);
        state.setAssignedDoctor(null);
        state.setConfirmed(false);
        state.setEndCall(false);
        state.setLastAskedField(null);
        state.resetRepeatCount();
        
        System.out.println("[SESSION_STAGE] stage=" + state.getStage());
        System.out.println("[GREETING_REQUEST]");
        
        // Generate greeting
        String greetMsg = greetingService.generateGreeting();
        state.appendMessage("assistant", greetMsg);
        
        System.out.println("[GREETING_RESPONSE]");
        
        // Format for TTS (Hindi)
        String formattedGreet = speechTextFormatter.formatForHindiTts(greetMsg);
        
        // TTS Synthesis
        String audioBase64 = "";
        try {
            audioBase64 = textToSpeechService.synthesizeSpeech(formattedGreet);
        } catch (Exception e) {
            System.err.println("TTS Error in startConversation: " + e.getMessage());
        }
        
        System.out.println("[END_CALL_FLAG] value=" + state.isEndCall());
        
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", state.getSessionId());
        response.put("message", greetMsg);
        response.put("audioBase64", audioBase64);
        response.put("conversationActive", true);
        
        return ResponseEntity.ok(response);
    }
}
