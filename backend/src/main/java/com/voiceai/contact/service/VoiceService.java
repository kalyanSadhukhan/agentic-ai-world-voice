package com.voiceai.contact.service;

import com.voiceai.contact.dto.VoiceResponse;
import com.voiceai.contact.service.orchestration.ConversationOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceService {

    private final ConversationOrchestrator conversationOrchestrator;

    public VoiceService(ConversationOrchestrator conversationOrchestrator) {
        this.conversationOrchestrator = conversationOrchestrator;
    }

    public VoiceResponse processVoice(MultipartFile audio, String sessionId) throws Exception {
        return conversationOrchestrator.processVoice(audio, sessionId);
    }
}
